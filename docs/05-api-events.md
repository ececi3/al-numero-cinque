# 05 — API ed eventi

Contratto REST implementato (vedi anche docs/07-auth.md per autenticazione e
autorizzazione per ruolo di ciascuna rotta).

## Endpoint di lettura (qualunque ruolo autenticato)

- `GET /api/tavoli` — elenco tavoli (per scegliere il tavolo in apertura sessione).
- `GET /api/menu` — elenco voci menu disponibili (`?tutti=true` per includere anche quelle disattivate).
  Ogni voce riporta anche `categoriaId`/`categoria` (nome), vedi `Categoria`
  in docs/02-domain-model.md.
- `GET /api/categorie` — elenco categorie di menu (per raggruppare il menu
  lato cameriere e popolare il form voce di menu lato admin).

## Endpoint (dispositivo cameriere, ruolo `CAMERIERE`)

- `POST /api/sessioni` — apre una sessione. Body: `id` (UUID
  client-generated), `tavoloId`, `numeroCoperti`. Idempotente sull'`id`.
- `GET /api/sessioni/{id}` — stato completo di una sessione (comande, gruppi
  e righe incluse). Usato come fallback quando la sessione non è nella cache
  locale del dispositivo (vedi docs/06-offline-sync.md), e per il polling
  periodico dello stato delle portate.
- `GET /api/sessioni/per-tavolo/{tavoloId}` — come sopra, ma a partire dal
  tavolo invece che dall'id sessione (risolve anche i tavoli aggregati). 404
  se il tavolo non ha una sessione `APERTA`.
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
- `POST /api/comande/gruppi/{gruppoId}/righe` — aggiunge una voce a un
  gruppo già inviato. Body: `menuItemId`, `quantita`, `note`. 409 se il
  gruppo è già `IN_PREP` o oltre (`GruppoInvio.puoModificare`); il prezzo
  è congelato al momento dell'aggiunta, non retroattivo.
- `DELETE /api/comande/gruppi/{gruppoId}/righe/{rigaId}` — rimuove una voce
  da un gruppo già inviato. Stesso vincolo di stato dell'aggiunta.
- `PATCH /api/comande/righe/{rigaId}/note` — aggiorna la nota di una riga
  esistente. Stesso vincolo di stato: 409 se il gruppo è già `IN_PREP` o
  oltre (una nota comunicata dopo che la cucina ha già iniziato a
  lavorare la portata non la raggiungerebbe in tempo utile).
- `GET /api/sessioni/{id}/conto` — totale da pagare per la sessione: somma
  di `prezzoCongelato * quantita` su tutte le `RigaOrdine` delle sue
  comande (i tavoli aggregati condividono la stessa sessione, quindi sono
  già inclusi). Le righe sono aggregate per voce di menu e prezzo
  congelato. Consultabile in qualunque momento, non richiede che le
  portate siano tutte `SERVITO` (a differenza della chiusura sessione).

## Endpoint (KDS — tablet cucina, ruolo `CUCINA`)

- `GET /api/kds/coda` — stato corrente della coda cucina, ordinata per
  `seq_coda` (righe in `IN_CODA`, `IN_PREP`, `PRONTO`). Ogni gruppo include
  le righe d'ordine (`menuItemId`, `nome`, `quantita`, `note`): il cuoco deve
  vedere cosa preparare, non solo l'id comanda/portata.
- `GET /api/kds/comande/{id}` — dettaglio completo di una comanda: tutte le
  sue portate (gruppi) con stato e righe, più tavolo, cameriere e orario
  d'invio. Serve a correlare le portate di una stessa comanda quando finiscono
  in colonne diverse della coda (una per stato).
- `POST /api/kds/gruppi/{id}/inizia-preparazione`
- `POST /api/kds/gruppi/{id}/pronto` — chiama `CoursingService.segnaPronto`.
- `POST /api/kds/gruppi/{id}/servito`
- **Feed WebSocket** (`/ws-kds`, STOMP su `/topic/kds`): alternativa al
  polling di `/api/kds/coda`, alimentata dagli eventi outbox via Kafka (vedi
  sotto), righe incluse nel payload. Handshake autenticato da
  `KdsHandshakeAuthInterceptor` (JWT via header `Authorization` o query
  param `access_token`, ruolo `CUCINA` richiesto — per questo il dispositivo
  cameriere non può riusare questo canale, vedi docs/06-offline-sync.md).

## Endpoint (autenticazione, `/api/auth/**`)

- `POST /api/auth/login` — pubblico. Body: `username`, `password`.
- `POST /api/auth/cambia-password` — autenticato, qualunque ruolo. Body:
  `vecchiaPassword`, `nuovaPassword`.

## Endpoint (admin, ruolo `ADMIN`, `/api/admin/**`)

- `GET /api/admin/utenti`, `POST /api/admin/utenti`,
  `POST /api/admin/utenti/{id}/disattiva` — gestione utenti.
- `DELETE /api/admin/utenti/{id}` — elimina definitivamente un utente. 409
  se l'utente è ancora attivo (va prima disattivato) o se ha già aperto
  sessioni/comande in passato (`cameriere_id` referenziato senza `ON
  DELETE`): in quel caso resta solo disattivabile, coerente con lo storico
  append-only.
- `GET /api/admin/analytics` — coperti totali, numero sessioni/comande,
  tempi medi di preparazione per `numeroPortata`.
- `POST /api/admin/tavoli` — crea un tavolo (409 se `numero` già in uso).
- `POST /api/admin/menu`, `POST /api/admin/menu/{id}/attiva`,
  `POST /api/admin/menu/{id}/disattiva` — gestione voci menu.
- `DELETE /api/admin/menu/{id}` — elimina una voce di menu. 409 se la voce
  è referenziata da almeno una `RigaOrdine` (append-only per storico/analytics,
  vedi docs/02-domain-model.md): in quel caso va disattivata, non eliminata.
- `POST /api/admin/categorie`, `DELETE /api/admin/categorie/{id}` — gestione
  categorie di menu. La `DELETE` fallisce con 409 se almeno una voce di menu
  è ancora collegata alla categoria.

## Eventi outbox (`aggregate_type` / `event_type`)

| aggregate_type | event_type | Quando |
|---|---|---|
| `GRUPPO_INVIO` | `GRUPPO_IN_CODA` | al fire (registrazione o fire-on-ready) |
| `GRUPPO_INVIO` | `GRUPPO_IN_PREP` | a inizio preparazione |
| `GRUPPO_INVIO` | `GRUPPO_PRONTO` | a `segnaPronto` |
| `GRUPPO_INVIO` | `GRUPPO_SERVITO` | a `segnaServito`, o subito al fire se il gruppo non richiede cucina (vedi docs/02-domain-model.md) |
| `GRUPPO_INVIO` | `GRUPPO_RIGHE_AGGIORNATE` | ad ogni modifica di una comanda già inviata (`ComandaModificaService`: aggiunta/rimozione voce, aggiornamento nota) — senza, il KDS vedrebbe la modifica solo al prossimo refresh manuale, non sul feed WebSocket |
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
