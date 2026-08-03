# 09 — Roadmap

## Fatto

- [x] Scaffold progetto (pom.xml, application.yml, docker-compose dev)
- [x] Schema DB v1-v5 (Flyway: core schema, outbox, sequenze, seed admin,
      aggregazione tavoli)
- [x] Modello di dominio (entità JPA con state machine incapsulata)
- [x] Motore di coursing completo: `SequenzaGenerator`, `CoursingService`
      (`registraComanda`, `segnaPronto`, `iniziaPreparazione`,
      `segnaServito`, fire-on-ready sincrono), test di integrazione sul
      flusso canonico
- [x] Routing cucina: un `GruppoInvio` le cui righe hanno tutte
      `MenuItem.inviaInCucina == false` (es. una portata di sole bevande)
      salta la coda ed è marcato `SERVITO` direttamente
      (`GruppoInvio.servaSenzaCucina`, `CoursingService.fireProssimoTrattenuto`),
      senza bloccare l'avanzamento delle portate successive
- [x] Emissione eventi outbox da ogni transizione del `CoursingService` e da
      `SessioneService` (`SESSIONE_CHIUSA`)
- [x] API REST: apertura sessione (`POST /api/sessioni`), sync comanda
      (`POST /api/comande`, con congelamento prezzi server-side), endpoint
      KDS (`GET /api/kds/coda`, transizioni di stato), gestore errori
      globale, test end-to-end
- [x] **Autenticazione JWT reale per ruolo** (`POST /api/auth/login`,
      `CAMERIERE`/`CUCINA`/`ADMIN` enforced per rotta), `cameriereId` derivato
      dal token e non più dal client
- [x] **Cambio password self-service** (`POST /api/auth/cambia-password`,
      qualunque ruolo autenticato)
- [x] **Gestione utenti da admin** (`POST /api/admin/utenti`,
      `POST /api/admin/utenti/{id}/disattiva`, `GET /api/admin/utenti`),
      riservata al ruolo `ADMIN`
- [x] **Chiusura sessione** (`POST /api/sessioni/{id}/chiudi`): libera il
      tavolo (e gli eventuali tavoli aggregati), rifiuta la chiusura se
      esistono portate non ancora `SERVITO`
- [x] **Aggregazione tavoli** (online-only): `POST
      /api/sessioni/{id}/aggrega-tavolo/{tavoloId}`, tabella
      `tavolo_aggregato` (V5), non cambia il tavolo primario della sessione;
      i tavoli aggregati vengono liberati insieme al primario alla chiusura
- [x] **Publisher outbox → Redpanda/Kafka** (`OutboxPublisher`, tick
      schedulato, topic unico `app.kafka.topic-eventi`), testato con
      `@EmbeddedKafka`
- [x] **Feed WebSocket del KDS** (`KdsWebSocketBridge` consuma da Kafka e
      ripubblica su `/topic/kds`; endpoint STOMP `/ws-kds` autenticato da
      `KdsHandshakeAuthInterceptor`, ruolo `CUCINA`), testato end-to-end
      (outbox → Kafka → WebSocket) con `@EmbeddedKafka` + client STOMP
- [x] **Endpoint di analytics per l'admin** (`GET /api/admin/analytics`:
      numero sessioni, coperti totali, numero comande, tempi medi di
      preparazione per numeroPortata)

## Prossimi passi

1. **Refresh token / logout esplicito**: con JWT stateless non c'è uno stato
   da invalidare lato server; se servirà una revoca prima della scadenza
   (12h di default) andrà introdotta una blacklist (es. tabella o cache dei
   token revocati, controllata da `JwtAuthenticationFilter`).
2. **Deploy**: `docker-compose.dev.yml` copre solo Postgres + Redpanda per
   sviluppo locale; manca ancora un profilo/immagine per l'installazione
   on-premise nel ristorante (vedi docs/08-deployment.md).
3. **Client dispositivi** (app cameriere offline-capable, tablet KDS): non
   ancora nel repo, solo il backend.

## Note d'ambiente (build locale)

`mvn clean verify` richiede **JDK 21** (o comunque non JDK 25): con la BOM
`spring-boot-dependencies:3.3.2` che fissa Lombok 1.18.34, il compilatore di
JDK 25 fa fallire silenziosamente l'annotation processing di Lombok (nessun
errore visibile, ma `@Getter`/`@NoArgsConstructor` non generano i membri, con
una cascata di errori "cannot find symbol" a valle). Su questa macchina il
JDK compatibile è in `/Library/Java/JavaVirtualMachines/jdk-21.jdk`; puntarci
`JAVA_HOME` prima di lanciare Maven.
