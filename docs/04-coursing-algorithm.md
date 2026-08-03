# 04 — Algoritmo di coursing (fire-on-ready)

## Obiettivo

Dato che una comanda può essere suddivisa in più portate (`GruppoInvio`,
ordinati per `numeroPortata`), il sistema deve:

1. Inviare in cucina automaticamente **solo la prima portata** di ogni
   comanda al momento della registrazione.
2. Quando la cucina segna una portata come `PRONTO`, inviare
   automaticamente in cucina la portata successiva della **stessa comanda**,
   se esiste ed è ancora `TRATTENUTO`.
3. Garantire un ordinamento assoluto e stabile della coda cucina
   (`seq_coda`), indipendente dall'ordine fisico con cui i dispositivi
   cameriere sincronizzano le comande quando tornano online.

## Implementazione

`CoursingService` (in `service/`):

- **`registraComanda(Comanda)`**: assegna `seq_server` (via
  `SequenzaGenerator.prossimoSeqServer()`), poi cerca il primo gruppo
  `TRATTENUTO` per `numeroPortata` e lo "spara" (`GruppoInvio.fire`),
  assegnandogli `seq_coda`. Idempotente: se la comanda risulta già
  registrata (stesso UUID, `seqServer` già valorizzato), la chiamata è un
  no-op che ritorna lo stato persistito — necessario per gestire in modo
  sicuro i retry di sync dopo un ack di rete perso.
- **`segnaPronto(gruppoInvioId)`**: transiziona il gruppo a `PRONTO`, poi
  cerca il primo gruppo `TRATTENUTO` della stessa comanda (per
  `numeroPortata`) e, se esiste, lo spara nella **stessa transazione**.
  Questo è un'esecuzione **sincrona**, non un consumer Kafka: il
  fire-on-ready deve essere atomico con la transizione a `PRONTO`, non
  eventualmente-consistente.

## Test di riferimento (caso canonico)

Dato:
- `O1`: due portate, A (`numeroPortata=1`) e B (`numeroPortata=2`)
- `O2`: una sola portata (`numeroPortata=1`)

Sequenza di chiamate: `registraComanda(O1)` → `registraComanda(O2)` →
`segnaPronto(O1-A)`.

Risultato atteso, indipendentemente dall'ordine fisico di sync dei
dispositivi: la coda cucina, ordinata per `seq_coda`, deve essere

```
O1-A (seq_coda=1) → O2 (seq_coda=2) → O1-B (seq_coda=3)
```

`O1-B` non entra in coda subito dopo `O1-A` nonostante appartenga alla
comanda "più vecchia": entra in coda solo quando la cucina segnala che
`O1-A` è pronto, quindi la sua posizione in coda dipende dal *momento del
fire*, non dal momento di creazione della comanda. Questo comportamento è
verificato da `CoursingServiceIntegrationTest.flussoCanonico_ordineCodaCucina_O1A_O2_O1B`.

Non c'è bisogno di alcuna assunzione di reset giornaliero delle sequenze:
`seq_server` e `seq_coda` sono monotone e globali.
