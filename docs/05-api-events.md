# 05 — API ed eventi

Contratto REST implementato (vedi anche docs/07-auth.md per autenticazione e
autorizzazione per ruolo di ciascuna rotta).

## Endpoint di lettura (qualunque ruolo autenticato)

- `GET /api/tavoli` — elenco tavoli (per scegliere il tavolo in apertura sessione).
- `GET /api/menu` — elenco voci menu disponibili (`?tutti=true` per includere anche quelle disattivate).

## Endpoint (dispositivo cameriere, ruolo `CAMERIERE`)

- `POST /api/sessioni` — apre una sessione. Body: `id` (UUID
  client-generated), `tavoloId`, `numeroCoperti`. Idempotente sull'`id`.
- `POST /api/sessioni/{id}/chiudi` — chiude la sessione e libera il tavolo
  (e gli eventuali tavoli aggregati). 409 se esistono portate non ancora
  `SERVITO`.
- `POST /api/sessioni/{id}/aggrega-tavolo/{tavoloId}` — aggrega un tavolo
  libero alla sessione (online-only, non cambia il tavolo primario). 409 se
  il tavolo è già occupato.
- `POST /api/comande` — sincronizza una comanda completa di gruppi/righe
  costruita offline. Body: `id` (UUID), `sessioneId`, lista gruppi con
  `numeroPortata` e righe (`menuItemId`, `quantita`, `note`).
  Il prezzo viene congelato **lato server** al momento della registrazione,
  leggendo `MenuItem.prezzo` corrente — non deve mai essere inviato dal
  client, per evitare manomissioni. Internamente chiama
  `CoursingService.registraComanda`.

## Endpoint (KDS — tablet cucina, ruolo `CUCINA`)

- `GET /api/kds/coda` — stato corrente della coda cucina, ordinata per
  `seq_coda` (righe in `IN_CODA`, `IN_PREP`, `PRONTO`).
- `POST /api/kds/gruppi/{id}/inizia-preparazione`
- `POST /api/kds/gruppi/{id}/pronto` — chiama `CoursingService.segnaPronto`.
- `POST /api/kds/gruppi/{id}/servito`
- **Feed WebSocket** (`/ws-kds`, STOMP su `/topic/kds`): alternativa al
  polling di `/api/kds/coda`, alimentata dagli eventi outbox via Kafka (vedi
  sotto). Handshake autenticato da `KdsHandshakeAuthInterceptor` (JWT via
  header `Authorization` o query param `access_token`, ruolo `CUCINA`
  richiesto).

## Endpoint (autenticazione, `/api/auth/**`)

- `POST /api/auth/login` — pubblico. Body: `username`, `password`.
- `POST /api/auth/cambia-password` — autenticato, qualunque ruolo. Body:
  `vecchiaPassword`, `nuovaPassword`.

## Endpoint (admin, ruolo `ADMIN`, `/api/admin/**`)

- `GET /api/admin/utenti`, `POST /api/admin/utenti`,
  `POST /api/admin/utenti/{id}/disattiva` — gestione utenti.
- `GET /api/admin/analytics` — coperti totali, numero sessioni/comande,
  tempi medi di preparazione per `numeroPortata`.
- `POST /api/admin/tavoli` — crea un tavolo (409 se `numero` già in uso).
- `POST /api/admin/menu`, `POST /api/admin/menu/{id}/attiva`,
  `POST /api/admin/menu/{id}/disattiva` — gestione voci menu.

## Eventi outbox (`aggregate_type` / `event_type`)

| aggregate_type | event_type | Quando |
|---|---|---|
| `GRUPPO_INVIO` | `GRUPPO_IN_CODA` | al fire (registrazione o fire-on-ready) |
| `GRUPPO_INVIO` | `GRUPPO_IN_PREP` | a inizio preparazione |
| `GRUPPO_INVIO` | `GRUPPO_PRONTO` | a `segnaPronto` |
| `GRUPPO_INVIO` | `GRUPPO_SERVITO` | a `segnaServito`, o subito al fire se il gruppo non richiede cucina (vedi docs/02-domain-model.md) |
| `SESSIONE` | `SESSIONE_CHIUSA` | a chiusura sessione |

La scrittura in `outbox_event` avviene nella stessa transazione della
mutazione di dominio che genera l'evento (`CoursingService`,
`SessioneService`). Un processo separato (`OutboxPublisher`, tick
schedulato via `@Scheduled`) legge le righe non pubblicate e le invia su
Kafka/Redpanda (topic `app.kafka.topic-eventi`, un unico topic per tutti gli
`event_type`), marcandole pubblicate solo dopo conferma dal broker.
`KdsWebSocketBridge` consuma dallo stesso topic e ripubblica ogni evento sul
canale STOMP `/topic/kds`, alimentando il feed WebSocket del KDS.

Entrambi i componenti sono disattivati nel profilo di test
(`app.eventi-async.enabled=false`, nessun broker Kafka disponibile); sono
testati con `@EmbeddedKafka` in `EventiAsyncIntegrationTest`.
