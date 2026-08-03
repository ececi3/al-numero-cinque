package com.alnumerocinque.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Riga dell'outbox transazionale. Scritta nella stessa transazione della
 * mutazione di dominio che la genera (vedi CoursingService), cosi' che la
 * scrittura del dato e la registrazione dell'evento siano atomiche. Un
 * publisher separato (non ancora implementato) legge le righe con
 * publishedAt nullo e le pubblica su Redpanda, marcandole poi come
 * pubblicate.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = OffsetDateTime.now();
    }

    public void segnaPubblicato() {
        this.publishedAt = OffsetDateTime.now();
    }

    public boolean isPubblicato() {
        return this.publishedAt != null;
    }
}
