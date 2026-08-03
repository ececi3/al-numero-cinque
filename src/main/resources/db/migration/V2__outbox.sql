-- V2: outbox transazionale per la pubblicazione affidabile di eventi
-- (feed WebSocket del KDS, analytics) verso Redpanda/Kafka. Scrivere l'evento
-- nella stessa transazione della mutazione di dominio evita la doppia
-- scrittura non atomica DB+broker; un publisher separato legge da qui e
-- pubblica in modo asincrono, marcando published_at.

CREATE TABLE outbox_event (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL, -- es. GRUPPO_INVIO, SESSIONE
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL, -- es. GRUPPO_IN_CODA, GRUPPO_PRONTO
    payload         TEXT         NOT NULL, -- JSON serializzato
    created_at      TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    published_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished ON outbox_event(published_at);
CREATE INDEX idx_outbox_aggregate ON outbox_event(aggregate_type, aggregate_id);
