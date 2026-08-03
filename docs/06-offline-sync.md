# 06 — Protocollo di sync offline

## Principio

Solo le operazioni **append-capable** (apertura sessione, registrazione
comanda) possono essere eseguite offline sul dispositivo cameriere. Tutto
ciò che richiede coordinamento con lo stato server (aggregazione tavoli,
transizioni della coda cucina) richiede connettività.

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

- Aggregazione di più tavoli in un'unica sessione.
- Qualunque transizione di `GruppoInvio` (fire, pronto, servito): sono
  tutte operazioni lato cucina/server, il KDS è per design sempre online
  (tablet fisso in cucina, non un dispositivo mobile).
- Chiusura sessione e generazione del conto.
