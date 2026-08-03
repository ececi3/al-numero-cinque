-- V1: schema core del dominio "al-numero-cinque"
-- Convenzioni: id numerici per entita' mutabili gestite solo online (tavolo,
-- menu_item, utente, gruppo_invio); UUID client-generated per entita' che
-- devono poter nascere offline in append-only (sessione, comanda, riga_ordine).

CREATE TABLE utente (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    ruolo           VARCHAR(32)  NOT NULL, -- CAMERIERE, CUCINA, ADMIN
    attivo          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now()
);

CREATE TABLE tavolo (
    id              BIGSERIAL PRIMARY KEY,
    numero          VARCHAR(16)  NOT NULL UNIQUE,
    stato           VARCHAR(32)  NOT NULL DEFAULT 'LIBERO', -- LIBERO, OCCUPATO
    created_at      TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now()
);

CREATE TABLE menu_item (
    id                  BIGSERIAL PRIMARY KEY,
    nome                VARCHAR(128) NOT NULL,
    descrizione         VARCHAR(512),
    prezzo              NUMERIC(10,2) NOT NULL,
    categoria           VARCHAR(64),
    invia_in_cucina     BOOLEAN NOT NULL DEFAULT TRUE, -- fonte di verita' per il routing cucina
    disponibile         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Sessione: client-generated UUID, puo' essere aperta offline (append-only).
-- L'aggregazione tra tavoli e' invece un'operazione online-only, quindi non
-- modella qui una relazione N:N tavoli-sessione, ma un semplice riferimento
-- al tavolo "primario" della sessione; l'aggregazione e' gestita a livello
-- applicativo tramite lo stato e non richiede mutazione retroattiva di questa riga.
CREATE TABLE sessione (
    id              UUID PRIMARY KEY,
    tavolo_id       BIGINT NOT NULL REFERENCES tavolo(id),
    cameriere_id    BIGINT NOT NULL REFERENCES utente(id),
    numero_coperti  INTEGER NOT NULL,
    stato           VARCHAR(32) NOT NULL DEFAULT 'APERTA', -- APERTA, CHIUSA
    aperta_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    chiusa_at       TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_sessione_tavolo ON sessione(tavolo_id);
CREATE INDEX idx_sessione_stato ON sessione(stato);

-- Comanda: client-generated UUID, append-only (creata offline dal cameriere).
-- seq_server e' assegnato in modo monotono lato server al momento del sync
-- (registraComanda) e determina l'ordinamento assoluto di arrivo, usato dal
-- motore di coursing indipendentemente dall'ordine di sincronizzazione.
CREATE TABLE comanda (
    id              UUID PRIMARY KEY,
    sessione_id     UUID NOT NULL REFERENCES sessione(id),
    cameriere_id    BIGINT NOT NULL REFERENCES utente(id),
    seq_server      BIGINT UNIQUE, -- assegnato da registraComanda(), monotono
    creata_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    registrata_at   TIMESTAMP WITH TIME ZONE -- momento in cui il server assegna seq_server
);

CREATE INDEX idx_comanda_sessione ON comanda(sessione_id);
CREATE INDEX idx_comanda_seq_server ON comanda(seq_server);

-- GruppoInvio: unita' di invio in cucina. Ogni comanda puo' generare piu'
-- gruppi (es. portate trattenute per essere "sparate" in momenti diversi).
-- Stato: TRATTENUTO -> IN_CODA -> IN_PREP -> PRONTO -> SERVITO
-- seq_coda e' assegnato quando il gruppo passa in IN_CODA (fire) e determina
-- l'ordine di uscita dalla coda cucina.
CREATE TABLE gruppo_invio (
    id                  BIGSERIAL PRIMARY KEY,
    comanda_id          UUID NOT NULL REFERENCES comanda(id),
    numero_portata      INTEGER NOT NULL, -- 1 = prima portata, 2 = seconda, ...
    stato               VARCHAR(32) NOT NULL DEFAULT 'TRATTENUTO',
    seq_coda            BIGINT UNIQUE, -- assegnato al fire (ingresso in IN_CODA)
    creato_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    in_coda_at          TIMESTAMP WITH TIME ZONE,
    in_prep_at          TIMESTAMP WITH TIME ZONE,
    pronto_at           TIMESTAMP WITH TIME ZONE,
    servito_at          TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_gruppo_invio_comanda ON gruppo_invio(comanda_id);
CREATE INDEX idx_gruppo_invio_stato ON gruppo_invio(stato);
CREATE INDEX idx_gruppo_invio_seq_coda ON gruppo_invio(seq_coda);

-- RigaOrdine: prezzo congelato al momento della presa comanda (no repricing
-- retroattivo). Nessuna modifica/cancellazione in v1: le righe sono append-only.
CREATE TABLE riga_ordine (
    id                  BIGSERIAL PRIMARY KEY,
    gruppo_invio_id     BIGINT NOT NULL REFERENCES gruppo_invio(id),
    menu_item_id        BIGINT NOT NULL REFERENCES menu_item(id),
    quantita            INTEGER NOT NULL CHECK (quantita > 0),
    prezzo_congelato    NUMERIC(10,2) NOT NULL,
    note                VARCHAR(256),
    creata_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_riga_ordine_gruppo ON riga_ordine(gruppo_invio_id);
CREATE INDEX idx_riga_ordine_menu_item ON riga_ordine(menu_item_id);
