package com.alnumerocinque.service;

import com.alnumerocinque.domain.OutboxEvent;
import com.alnumerocinque.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Scrive eventi nell'outbox transazionale (tabella outbox_event). Non ha
 * un proprio @Transactional: partecipa sempre alla transazione del
 * chiamante (es. CoursingService), cosi' che la scrittura dell'evento sia
 * atomica con la mutazione di dominio che lo genera.
 */
@Component
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void registraEvento(String aggregateType, String aggregateId, String eventType, Object payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile serializzare il payload dell'evento outbox", e);
        }
        outboxEventRepository.save(new OutboxEvent(aggregateType, aggregateId, eventType, payloadJson));
    }
}
