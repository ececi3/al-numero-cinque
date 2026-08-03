package com.alnumerocinque.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Consuma dal topic Kafka alimentato da OutboxPublisher e ripubblica ogni
 * evento sul canale STOMP /topic/kds, a cui il tablet cucina e' sottoscritto
 * (vedi WebSocketConfig, KdsHandshakeAuthInterceptor). Disattivato nel
 * profilo test insieme a OutboxPublisher (app.eventi-async.enabled=false):
 * nessun broker Kafka disponibile.
 */
@Component
@ConditionalOnProperty(prefix = "app.eventi-async", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KdsWebSocketBridge {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public KdsWebSocketBridge(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @KafkaListener(topics = "${app.kafka.topic-eventi}", groupId = "${spring.kafka.consumer.group-id}")
    public void onEvento(String payload) {
        simpMessagingTemplate.convertAndSend("/topic/kds", payload);
    }
}
