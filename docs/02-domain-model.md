# 02 — Modello di dominio

## Entità

| Entità | Id | Natura | Note |
|---|---|---|---|
| `Utente` | Long (auto) | online-only | `ruolo`: CAMERIERE / CUCINA / ADMIN |
| `Tavolo` | Long (auto) | online-only | `stato`: LIBERO / OCCUPATO |
| `MenuItem` | Long (auto) | online-only | `inviaInCucina` è la fonte di verità per il routing cucina; `categoria` è una FK verso `Categoria` (opzionale) |
| `Categoria` | Long (auto) | online-only | categoria di menu gestita da admin (create/elimina), `nome` univoco |
| `Sessione` | UUID (client) | append-capable | `numeroCoperti` obbligatorio (richiesto per analytics), riferisce un `Tavolo` |
| `Comanda` | UUID (client) | append-capable | `seqServer` assegnato **solo** alla registrazione (online) |
| `GruppoInvio` | Long (auto) | online-only | rappresenta una "portata"; stato macchina a stati |
| `RigaOrdine` | Long (auto) | append-only con eccezioni | `prezzoCongelato` sempre immutabile; nota e riga intera (aggiunta/rimozione) modificabili solo se il `GruppoInvio` non è ancora in preparazione (vedi sotto) |
| `TavoloAggregato` | Long (auto) | online-only | associa un tavolo aggiuntivo a una sessione (aggregazione tavoli), non cambia il tavolo primario |

## Relazioni

```
Tavolo 1──* Sessione 1──* Comanda 1──* GruppoInvio 1──* RigaOrdine *──1 MenuItem
                                          (numeroPortata determina l'ordine)
```

Una `Comanda` può generare più `GruppoInvio` (una per portata dichiarata dal
cameriere al momento della presa comanda). Ogni `GruppoInvio` contiene una o
più `RigaOrdine`.

## Macchina a stati di `GruppoInvio`

```
TRATTENUTO ──fire()──────────────> IN_CODA ──iniziaPreparazione()──> IN_PREP
     │                                                                   │
     │                                                         segnaPronto()
     │                                                                   ▼
     │                                                                PRONTO ──segnaServito()──> SERVITO
     │                                                                                                ▲
     └──servaSenzaCucina()───────────────────────────────────────────────────────────────────────────┘
```

Le transizioni sono incapsulate nell'entità (`GruppoInvio.puoTransireA` via
`StatoGruppo`); ogni tentativo di transizione illegale lancia
`IllegalStateException`. L'orchestrazione del **fire-on-ready** (sparare
automaticamente il gruppo successivo trattenuto quando il precedente della
stessa comanda diventa `PRONTO`) è responsabilità del `CoursingService`, non
dell'entità stessa: l'entità conosce solo le proprie transizioni legali, il
servizio conosce la relazione tra i gruppi di una comanda.

`TRATTENUTO -> SERVITO` è la transizione diretta usata quando nessuna riga
del gruppo richiede lavorazione in cucina (`GruppoInvio.richiedeCucina() ==
false`, es. una portata di sole bevande, vedi `MenuItem.inviaInCucina`): il
gruppo salta interamente la coda (nessun `seq_coda` assegnato) ed è marcato
servito subito. `CoursingService.fireProssimoTrattenuto` gestisce questo
caso cascando immediatamente sul gruppo successivo, cosi' che una portata
"senza cucina" non blocchi l'avanzamento di quelle dopo.

## Modifica di una comanda già inviata

`RigaOrdine` è append-only *di default*, ma con un'eccezione deliberata,
incapsulata in `GruppoInvio`/`RigaOrdine` (non solo a livello di
controller, così qualunque chiamante rispetta lo stesso vincolo): nota e
voce intera (aggiunta/rimozione) sono modificabili solo se
`GruppoInvio.puoModificare()` è vero, cioè stato `TRATTENUTO` o `IN_CODA`
— il gruppo non è ancora stato visto dalla cucina. Da `IN_PREP` in poi
qualunque modifica lancia `IllegalStateException` (409): una nota
comunicata dopo che la cucina ha già iniziato a lavorare la portata non la
raggiungerebbe in tempo utile. Il prezzo di una voce aggiunta
successivamente resta congelato al momento dell'aggiunta, mai retroattivo
(stessa regola del sync iniziale, vedi `ComandaSyncService`).

## Conto della sessione

Non è un'entità persistita: `ContoResponse` (`SessioneService.conto`) è un
read model calcolato al volo sommando `RigaOrdine.totaleRiga()`
(`prezzoCongelato * quantita`) su tutte le righe delle comande della
sessione, aggregate per voce di menu e prezzo congelato. Riflette sempre lo
stato corrente delle `RigaOrdine` (comprese le eventuali aggiunte/rimozioni
successive, vedi sopra), senza bisogno di invalidare una cache.

Aggiungere/togliere righe non altera lo stato del gruppo, `seq_coda` né la
logica di fire-on-ready (`CoursingService`): sono ortogonali alla state
machine del gruppo, riguardano solo il contenuto delle sue righe.

## Convenzioni di naming

- Dominio in italiano: `Sessione`, `Comanda`, `GruppoInvio`, `RigaOrdine`,
  `seqServer`, `seqCoda`, `registraComanda()`, `segnaPronto()`,
  `inviaInCucina`.
- Stati: `TRATTENUTO` / `IN_CODA` / `IN_PREP` / `PRONTO` / `SERVITO`.
- Campo cameriere: **`cameriereId`** (non `camerId`).
