package com.alnumerocinque.service;

import com.alnumerocinque.domain.OutboxEvent;
import com.alnumerocinque.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Legge periodicamente le righe non pubblicate di outbox_event e le invia su
 * Kafka/Redpanda (topic app.kafka.topic-eventi), marcandole pubblicate solo
 * dopo conferma dal broker. Se un invio fallisce, l'iterazione si interrompe
 * senza processare gli eventi successivi: preserva l'ordine di pubblicazione
 * (rilevante per il feed KDS) e lascia che il prossimo tick ritenti dallo
 * stesso punto.
 *
 * Disattivato nel profilo test (app.eventi-async.enabled=false), dove non
 * c'e' un broker Kafka disponibile.
 */
@Component
@ConditionalOnProperty(prefix = "app.eventi-async", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final long timeoutInvioMs;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                            KafkaTemplate<String, String> kafkaTemplate,
                            @Value("${app.kafka.topic-eventi}") String topic,
                            @Value("${app.outbox-publisher.timeout-invio-ms:5000}") long timeoutInvioMs) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.timeoutInvioMs = timeoutInvioMs;
    }

    @Scheduled(fixedDelayString = "${app.outbox-publisher.intervallo-ms:2000}")
    @Transactional
    public void pubblica() {
        List<OutboxEvent> daPubblicare = outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc();

        for (OutboxEvent evento : daPubblicare) {
            try {
                kafkaTemplate.send(topic, evento.getAggregateId(), evento.getPayload())
                        .get(timeoutInvioMs, TimeUnit.MILLISECONDS);
                evento.segnaPubblicato();
            } catch (Exception e) {
                log.warn("Impossibile pubblicare l'evento outbox {} ({}#{}) su Kafka, verra' ritentato al " +
                                "prossimo giro: {}",
                        evento.getId(), evento.getAggregateType(), evento.getAggregateId(), e.getMessage());
                break;
            }
        }
    }
}
