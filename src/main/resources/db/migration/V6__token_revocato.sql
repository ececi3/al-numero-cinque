-- V6: blacklist di logout per JWT stateless. Con validita' lunga (12h di
-- default, vedi docs/07-auth.md) serve un modo per invalidare un token
-- prima della scadenza naturale (logout esplicito, dispositivo smarrito).
-- Ogni riga e' il jti (claim "jti", UUID generato alla creazione del token)
-- di un token revocato; scade_at ne consente la pulizia periodica una volta
-- che il token sarebbe comunque scaduto naturalmente (vedi
-- TokenRevocatoService.pulisciScaduti).

CREATE TABLE token_revocato (
    jti          VARCHAR(64) PRIMARY KEY,
    revocato_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    scade_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_token_revocato_scade_at ON token_revocato(scade_at);
