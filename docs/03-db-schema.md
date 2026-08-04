# 03 — Schema del database

Migrazioni Flyway in `src/main/resources/db/migration/`.

## V1__core_schema.sql

Tabelle core: `utente`, `tavolo`, `menu_item`, `sessione`, `comanda`,
`gruppo_invio`, `riga_ordine`.

Punti degni di nota:

- `sessione.id` e `comanda.id` sono `UUID` (generati lato client, non
  `SERIAL`): coerente con la natura append-capable di queste entità.
- `comanda.seq_server` è `BIGINT UNIQUE` **nullable**: è `NULL` finché la
  comanda non viene registrata online; viene valorizzato in modo monotono
  da `registraComanda()`.
- `gruppo_invio.seq_coda` è `BIGINT UNIQUE` nullable con la stessa logica,
  ma per l'ingresso in coda cucina (fire).
- `riga_ordine.prezzo_congelato` è indipendente dal prezzo corrente in
  `menu_item`: nessun trigger o vincolo li tiene sincronizzati, per design.

## V2__outbox.sql

Tabella `outbox_event` per il pattern outbox transazionale: ogni mutazione
di dominio che deve generare un evento verso Redpanda (feed KDS, analytics)
scrive la riga outbox nella **stessa transazione** della mutazione. Un
publisher separato (non ancora implementato, vedi roadmap) legge le righe
non pubblicate (`published_at IS NULL`) e le invia al broker.

## V3__sequences.sql

Due sequenze native (`seq_comanda_server`, `seq_gruppo_coda`) usate dal
componente `SequenzaGenerator` per assegnare `seq_server` e `seq_coda` in
modo monotono e concorrency-safe, senza lock applicativi. Funzionano
identiche in PostgreSQL (prod) e in H2 in modalità di compatibilità
PostgreSQL (test), tramite la sintassi `nextval('nome_sequenza')`.

## V4__seed_admin_utente.sql

Inserisce l'utente `admin` di bootstrap (password `cambiami-subito`,
hash bcrypt), necessario per il primissimo accesso — vedi
docs/07-auth.md.

## V5__tavolo_aggregato.sql

Tabella `tavolo_aggregato` per l'aggregazione di più tavoli a una
sessione (online-only, non cambia il tavolo primario). Vedi
docs/02-domain-model.md.

## V6__token_revocato.sql

Tabella `token_revocato` (blacklist di `jti` JWT) per il logout
esplicito — vedi docs/07-auth.md.

## V7__categoria.sql

Sostituisce `menu_item.categoria` (stringa libera) con una vera tabella
`categoria` (`nome` univoco), gestita da admin (create/elimina) invece
che testo libero — vedi docs/02-domain-model.md. La migrazione converte
i valori distinti già presenti in `menu_item.categoria` in righe della
nuova tabella e ricollega `menu_item.categoria_id` di conseguenza, prima
di rimuovere la vecchia colonna testuale.
