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
- [x] **Frontend React completo** (`frontend/`): pagine cameriere (tavoli,
      sessione/presa comanda), KDS, admin (menu, categorie, utenti, tavoli,
      analytics), autenticazione, coda di sync offline
- [x] **Logout esplicito con blacklist token** (tabella `token_revocato`, V6)
- [x] **Categorie di menu gestibili da admin** (V7): entità `Categoria`
      invece di stringa libera su `MenuItem`, con creazione/eliminazione
      (409 se ancora collegata a voci di menu); menu del cameriere
      raggruppato per categoria
- [x] **Eliminazione reale di voci di menu e utenti disattivati** (oltre
      alla sola disattivazione preesistente), bloccata (409) se referenziate
      da storico (righe d'ordine / sessioni-comande)
- [x] **Coda cucina con i prodotti da preparare**: `GET /api/kds/coda`
      include le righe d'ordine di ogni gruppo (prima esponeva solo
      comanda/portata); dettaglio comanda completo (`GET
      /api/kds/comande/{id}`) per correlare le portate di una stessa
      comanda finite in colonne diverse
- [x] **Recupero sessione da un altro dispositivo** (`GET
      /api/sessioni/{id}`, `GET /api/sessioni/per-tavolo/{tavoloId}`):
      l'indice tavolo → sessione del cameriere viveva solo nel localStorage
      del dispositivo di apertura
- [x] **Stato delle portate aggiornato lato cameriere** via polling (il
      dispositivo cameriere non può ascoltare il feed WebSocket del KDS,
      riservato al ruolo CUCINA)
- [x] **Modifica di una comanda già inviata**: nota sempre modificabile,
      voci aggiungibili/rimovibili solo se il gruppo non è ancora in
      preparazione (`GruppoInvio.puoModificareVoci`)

## Prossimi passi

1. **Client dispositivi nativi** (app cameriere offline-capable, tablet
   KDS): il frontend è oggi una web app React responsive, non un'app
   nativa Android come originariamente previsto in
   docs/01-design-decisions.md — da rivalutare se l'installazione reale
   lo richiede.
2. **Reverse proxy (nginx) davanti all'applicazione**: valutato ma non
   ancora deciso/implementato. Servirebbe a terminare TLS (anche su LAN
   del ristorante), servire i file statici del build frontend senza
   passare dalla JVM, ed esporre un unico punto d'ingresso (80/443) invece
   della porta 8080 nuda; richiede configurazione esplicita per il
   proxying del WebSocket STOMP (`/ws-kds`, header `Upgrade`/`Connection`).
   Trade-off principale: un componente in più da mantenere in
   un'installazione on-premise a nodo singolo, a fronte di un modello di
   minaccia già contenuto (rete chiusa del locale). Va di pari passo col
   punto successivo.
3. **Deploy**: `docker-compose.dev.yml` copre solo Postgres + Redpanda per
   sviluppo locale; manca ancora un profilo/immagine per l'installazione
   on-premise nel ristorante (vedi docs/08-deployment.md).

## Note d'ambiente (build locale)

`mvn clean verify` richiede **JDK 21** (o comunque non JDK 25): con la BOM
`spring-boot-dependencies:3.3.2` che fissa Lombok 1.18.34, il compilatore di
JDK 25 fa fallire silenziosamente l'annotation processing di Lombok (nessun
errore visibile, ma `@Getter`/`@NoArgsConstructor` non generano i membri, con
una cascata di errori "cannot find symbol" a valle). Su questa macchina il
JDK compatibile è in `/Library/Java/JavaVirtualMachines/jdk-21.jdk`; puntarci
`JAVA_HOME` prima di lanciare Maven.
