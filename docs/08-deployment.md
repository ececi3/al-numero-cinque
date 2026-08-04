# 08 — Deployment

## Target: on-premise, nodo singolo

Il server (Spring Boot + PostgreSQL + Redpanda) gira su una macchina fisica
o mini-PC all'interno della rete del ristorante. Motivazione: la cucina
deve poter continuare a ricevere e processare comande anche se la
connessione Internet del locale salta — una dipendenza cloud avrebbe reso
il KDS inutilizzabile proprio nei momenti di maggior pressione operativa.

## Componenti

- **Applicazione Spring Boot**: singolo processo JVM, espone REST + WebSocket.
- **PostgreSQL**: dati transazionali (sessioni, comande, gruppi, righe).
- **Redpanda**: broker per il feed WebSocket del KDS e per gli eventi
  outbox verso l'analytics; scelto al posto di Kafka "vero" per il
  footprint ridotto, adeguato a un singolo nodo.
- **Dispositivi cameriere**: APK Android, comunicano col server via rete
  locale (Wi-Fi del ristorante), con presa comande offline-capable quando
  il Wi-Fi stesso non è raggiungibile.
- **Tablet cucina (KDS)**: sempre connesso alla rete locale, riceve il feed
  in tempo reale via WebSocket.

## Ambiente di sviluppo

`docker-compose.dev.yml` fornisce Postgres + Redpanda in locale. Profilo
Spring `dev` punta a questo compose; profilo `test` usa H2 in-memory in
modalità di compatibilità PostgreSQL, per non richiedere Docker per i test
automatici.

## Reverse proxy

Non ancora presente davanti all'applicazione (Spring Boot è esposto
direttamente). Un nginx davanti servirebbe a terminare TLS, servire i
file statici del build frontend ed esporre un unico punto d'ingresso
invece della porta 8080 nuda — vedi la valutazione in
docs/09-roadmap.md § Prossimi passi (non ancora deciso/implementato).

## Backup

Non ancora definito nel dettaglio (vedi roadmap): dato il deployment
on-premise, andrà previsto un backup periodico di PostgreSQL verso uno
storage esterno quando la connettività è disponibile.
