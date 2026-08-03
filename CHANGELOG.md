# Changelog

Tutte le modifiche rilevanti al progetto sono registrate qui.

## [Ricostruzione] — 2026-07-30

Il lavoro di una sessione precedente (documentazione, scaffold, schema DB,
modello di dominio, motore di coursing parziale) non era mai stato pushato
su GitHub e non era più recuperabile lato Claude (il filesystem di lavoro
si resetta tra sessioni). Su indicazione di Emanuele, l'intero progetto è
stato ricostruito da zero in questa sessione, seguendo fedelmente le
decisioni di design già fissate in memoria.

### Aggiunto

- Scaffold Maven (`pom.xml`), `application.yml` multi-profilo (dev/test),
  `docker-compose.dev.yml` (Postgres + Redpanda).
- Migrazioni Flyway: `V1__core_schema.sql`, `V2__outbox.sql`,
  `V3__sequences.sql`.
- Entità di dominio: `Utente`, `Tavolo`, `MenuItem`, `Sessione`, `Comanda`,
  `GruppoInvio` (con state machine incapsulata), `RigaOrdine`.
- Motore di coursing completo: `SequenzaGenerator`, `ComandaRepository`,
  `GruppoInvioRepository`, `CoursingService` (`registraComanda`,
  `segnaPronto`, fire-on-ready sincrono).
- Test di integrazione (`CoursingServiceIntegrationTest`) sul flusso
  canonico O1-A → O2 → O1-B e su idempotenza/transizioni illegali.
- Set completo di documentazione tecnica (9 documenti in `docs/`).

### Nota operativa

Da qui in avanti, ogni branch va pushato su GitHub subito dopo la merge in
`main`, per evitare di ripetere questa perdita di lavoro.
