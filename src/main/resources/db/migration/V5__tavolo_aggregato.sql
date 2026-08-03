-- V5: aggregazione tavoli (online-only, vedi docs/09-roadmap.md). Un tavolo
-- aggiuntivo puo' essere associato a una sessione gia' aperta (es. due tavoli
-- accostati per un gruppo numeroso), senza cambiare il tavolo "primario"
-- della sessione (sessione.tavolo_id resta quello di apertura, coerente con
-- la nota di design in V1__core_schema.sql). Ogni riga registra un tavolo
-- aggregato a una sessione; alla chiusura della sessione anche i tavoli
-- aggregati vengono liberati (vedi SessioneService.chiudiSessione), ma le
-- righe restano per storico/analytics.

CREATE TABLE tavolo_aggregato (
    id              BIGSERIAL PRIMARY KEY,
    sessione_id     UUID NOT NULL REFERENCES sessione(id),
    tavolo_id       BIGINT NOT NULL REFERENCES tavolo(id),
    aggregato_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (sessione_id, tavolo_id)
);

CREATE INDEX idx_tavolo_aggregato_sessione ON tavolo_aggregato(sessione_id);
CREATE INDEX idx_tavolo_aggregato_tavolo ON tavolo_aggregato(tavolo_id);
