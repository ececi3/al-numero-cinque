# al numero cinque

Sistema di gestione ordini per un ristorante singolo: presa comande su
dispositivo cameriere (offline-capable), Kitchen Display System (KDS) per
la cucina, gestione dinamica di tavoli/sessioni, sezione analytics e
amministrazione per l'admin.

Deployment target: on-premise, nodo singolo, all'interno della rete del
ristorante (vedi [`docs/08-deployment.md`](docs/08-deployment.md) — il
locale deve poter continuare a operare anche se salta la connessione
Internet).

## Stack

- **Backend**: Spring Boot 3 / Java 21, PostgreSQL, Redpanda (broker
  compatibile Kafka per il feed WebSocket del KDS e gli eventi verso
  l'analytics), Flyway per le migrazioni, JWT stateless per
  l'autenticazione.
- **Frontend**: React 19 + TypeScript, Vite, react-router. Nessuna
  libreria UI: stile e componenti sono scritti a mano
  (`frontend/src/index.css`).

## Struttura del repo

```
src/main/java/com/alnumerocinque/   backend (domain, service, web, repository, security)
src/main/resources/db/migration/    migrazioni Flyway
src/test/java/                      test di integrazione (MockMvc + H2)
frontend/src/                       app React (pages/admin, pages/cameriere, pages/kds, api, offline)
docs/                               architettura e decisioni di design (vedi sotto)
docker-compose.dev.yml              Postgres + Redpanda per sviluppo locale
```

## Avviare il progetto in locale

**1. Postgres + Redpanda**

```bash
docker compose -f docker-compose.dev.yml up -d postgres redpanda
```

**2. Backend** (porta 8080). Il progetto richiede **JDK 21**: con JDK 25 (es.
l'ultima versione installata via Homebrew) l'annotation processing di
Lombok fallisce silenziosamente — nessun errore visibile, ma
`@Getter`/`@NoArgsConstructor` non generano i membri, con una cascata di
errori "cannot find symbol" a valle. Punta esplicitamente `JAVA_HOME`
prima di lanciare Maven:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

Al primo avvio Flyway crea/aggiorna automaticamente lo schema.

**3. Frontend** (porta 5173, con proxy verso il backend su 8080 — vedi
`frontend/vite.config.ts`):

```bash
cd frontend
npm install
npm run dev
```

**Login**: utente admin di bootstrap `admin` / `cambiami-subito` (creato
dalla migrazione `V4__seed_admin_utente.sql`), **da cambiare subito dopo
il primo accesso** — non è pensato per sopravvivere oltre il primo avvio
in un'installazione reale. Da lì si creano gli utenti cameriere/cucina
reali in Admin → Utenti.

## Test

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test          # backend: H2 in-memory in modalità di compatibilità PostgreSQL, nessun Docker richiesto
cd frontend && npm run build && npm run lint   # type-check + lint frontend
```

## Documentazione

Le decisioni di architettura e i vincoli di dominio sono in `docs/`,
un file per argomento — da tenere aggiornati insieme al codice:

- [`01-design-decisions.md`](docs/01-design-decisions.md) — scope e decisioni chiave
- [`02-domain-model.md`](docs/02-domain-model.md) — entità, relazioni, macchina a stati
- [`03-db-schema.md`](docs/03-db-schema.md) — migrazioni Flyway
- [`04-coursing-algorithm.md`](docs/04-coursing-algorithm.md) — algoritmo di fire-on-ready
- [`05-api-events.md`](docs/05-api-events.md) — contratto REST ed eventi outbox
- [`06-offline-sync.md`](docs/06-offline-sync.md) — protocollo di sync offline
- [`07-auth.md`](docs/07-auth.md) — autenticazione e autorizzazione
- [`08-deployment.md`](docs/08-deployment.md) — target di deployment
- [`09-roadmap.md`](docs/09-roadmap.md) — cosa è fatto, prossimi passi

## Workflow

Un branch per modifica, da `develop`, pushato su `origin` e rimergiato in
`develop`; `main` resta la baseline/release. Vedi la cronologia dei commit
per degli esempi.
