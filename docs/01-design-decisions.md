# 01 — Decisioni di design (v1)

Scope: sistema di gestione ordini per un singolo ristorante. Copre presa
comande su dispositivo mobile del cameriere (offline-capable), Kitchen
Display System (KDS) su tablet, gestione dinamica di tavoli/sessioni,
sezione analytics per l'admin.

## Decisioni chiave

- **Presa comande offline è un requisito hard.** Il cameriere deve poter
  aprire una sessione e registrare una comanda anche senza connettività, e
  sincronizzare quando torna online. L'**aggregazione tra tavoli** è invece
  un'operazione **online-only**: richiede uno stato condiviso e coerente che
  non ha senso costruire in modo distribuito/offline.
- **Fire-on-ready è cucina-driven, non cameriere-driven.** Il cameriere non
  decide quando "sparare" la portata successiva: lo decide il completamento
  della portata precedente, segnalato dalla cucina (`segnaPronto`).
- **Le righe d'ordine sono append-only, con due eccezioni deliberate.**
  La nota di una riga è sempre modificabile; una voce intera si può
  aggiungere/rimuovere solo finché il gruppo non è ancora in preparazione
  (vedi `GruppoInvio.puoModificareVoci` in docs/02-domain-model.md). Da
  `IN_PREP` in poi la comanda torna append-only: nessuna correzione
  retroattiva del prezzo congelato o delle voci già viste dalla cucina.
- **Prezzi congelati al momento della presa comanda.** Il conto finale non
  ricalcola mai i prezzi correnti del menu: usa lo snapshot preso in ogni
  `RigaOrdine`.
- **Fatturazione a totale unico**, senza split per persona, in v1.
- **Autenticazione obbligatoria** per tutti i ruoli (cameriere, cucina,
  admin); sezione analytics riservata all'admin.
- **Distribuzione Android via APK** per i dispositivi cameriere (nessun
  Play Store in v1, per poter iterare rapidamente e installare direttamente
  sui device del ristorante).
- **Deployment on-premise.** Il ristorante deve continuare a operare (presa
  comande, KDS) anche se la connessione Internet salta; solo funzioni
  esplicitamente online-only (aggregazione tavoli, eventualmente
  sync/backup) risentono di un'interruzione.
- **Redpanda invece di Kafka "vero"** come broker per gli eventi interni
  (feed KDS, outbox verso analytics): stesso protocollo wire-compatible,
  footprint molto più leggero per un nodo singolo on-premise.

## Principio guida: append vs. mutazione

Questo principio risolve la tensione tra "deve funzionare offline" e "deve
essere consistente online":

- **Append-capable** (può nascere offline, id generato dal client, tipicamente
  UUID): apertura sessione, registrazione comanda, righe d'ordine.
- **Online-only** (richiede stato server coordinato): aggregazione tavoli,
  transizioni della coda cucina (fire, pronto, servito), chiusura sessione.

Le entità append-capable usano UUID generati lato client proprio per poter
essere create senza round-trip col server; le entità online-only usano id
numerici assegnati dal database.
