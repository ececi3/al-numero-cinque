# 06 — Protocollo di sync offline

## Principio

Solo le operazioni **append-capable** (apertura sessione *su un tavolo
esistente*, registrazione comanda) possono essere eseguite offline sul
dispositivo cameriere. Tutto ciò che richiede coordinamento con lo stato
server (aggregazione tavoli, transizioni della coda cucina, creazione di un
tavolo nuovo) richiede connettività.

## Generazione degli id

Il client genera `UUID` per `Sessione` e `Comanda` (e transitivamente per i
`GruppoInvio`/`RigaOrdine` che le compongono, anche se questi ultimi usano
id numerici assegnati dal server alla persistenza — l'identità "logica"
lato client è comunque tracciabile tramite l'UUID della comanda padre).
Questo permette di creare l'intero grafo di oggetti offline senza
conflitti di id al sync.

## Idempotenza al sync

Un dispositivo può ritentare l'invio della stessa comanda più volte (ack di
rete perso, riavvio dell'app, ecc.). `CoursingService.registraComanda` è
progettato per essere idempotente: se la comanda con lo stesso UUID risulta
già registrata (`seqServer` non nullo), la chiamata ritorna lo stato
persistito senza rieseguire il fire del primo gruppo — altrimenti si
rischierebbe di sparare due volte la stessa portata in cucina.

## Ordine di sync e ordinamento in coda

L'ordine con cui i dispositivi sincronizzano le comande verso il server
**non** determina l'ordine in coda cucina in modo diretto: `seq_server` e
`seq_coda` sono assegnati nel momento in cui il server processa
effettivamente la chiamata (`registraComanda` / `segnaPronto`), quindi
l'ordinamento riflette l'ordine di elaborazione server, non l'istante di
creazione lato client. Questo è per design: non serve un clock sincronizzato
tra dispositivi, e il caso limite (due comande sincronizzate quasi
simultaneamente da tavoli diversi) è gestito correttamente dalle sequenze
DB monotone, senza bisogno di logiche di riordino a posteriori.

## Cosa NON è offline-capable

- **Apertura sessione su un tavolo nuovo** (`POST /api/sessioni/nuovo-tavolo`,
  vedi docs/05-api-events.md): il tavolo nasce solo in quel momento
  (`Tavolo` non ha id client-generated, vedi docs/02-domain-model.md), quindi
  non può essere creato senza contattare il server. L'apertura su un tavolo
  **già noto** al dispositivo (`POST /api/sessioni`, con `tavoloId` già in
  cache dal precedente `GET /api/tavoli`) resta invece append-capable come
  sopra.
- Aggregazione di più tavoli in un'unica sessione.
- Qualunque transizione di `GruppoInvio` (fire, pronto, servito): sono
  tutte operazioni lato cucina/server, il KDS è per design sempre online
  (tablet fisso in cucina, non un dispositivo mobile).
- Chiusura sessione e generazione del conto.

## Visibilità dello stato cucina lato cameriere

Il feed WebSocket del KDS (`/ws-kds`) è riservato al ruolo `CUCINA`
(`KdsHandshakeAuthInterceptor`), quindi il dispositivo cameriere non può
ascoltarlo: `CameriereSessionePage` fa **polling** di
`GET /api/sessioni/{id}` ogni 10 secondi (solo quando la sessione è già
sincronizzata, non mentre l'apertura stessa è pendente offline) per
riallineare lo stato di ogni portata (es. `IN_CODA` → `PRONTO` →
`SERVITO`), che altrimenti resterebbe fermo allo snapshot preso al momento
dell'invio o del recupero cross-device. Le comande ancora in coda di sync
offline (non ancora sul server) non vengono toccate dal polling.

## Recupero sessione da un altro dispositivo

L'indice tavolo → sessione (`frontend/src/offline/indiceTavoli.ts`) vive
solo nel `localStorage` del dispositivo che ha aperto la sessione. Un
secondo dispositivo che clicca un tavolo occupato non trovato in locale
recupera lo stato completo dal server tramite
`GET /api/sessioni/per-tavolo/{tavoloId}` (risolve anche i tavoli
aggregati) o `GET /api/sessioni/{id}` per accesso diretto via URL; è
un'operazione online-only, coerente con le altre operazioni di
coordinamento server elencate sopra.
