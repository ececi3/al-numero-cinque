# 07 — Autenticazione e autorizzazione

## Implementazione

- **JWT stateless** (`SessionCreationPolicy.STATELESS`, nessuna sessione
  server-side): il client invia `Authorization: Bearer <token>` a ogni
  richiesta. Validità di default 12 ore (`app.jwt.validita-ore`), pensata
  per coprire un turno di lavoro senza richiedere ri-autenticazioni,
  coerente con dispositivi che possono avere connettività intermittente.
- **`POST /api/auth/login`** (`{ "username", "password" }`, pubblico):
  verifica le credenziali contro `Utente` (password hashata con
  `BCryptPasswordEncoder`) e restituisce `{ token, username, ruolo }`.
- **Autorizzazione per ruolo**, applicata in `SecurityConfig`:
  - `/api/auth/login` — pubblico (unico endpoint di `/api/auth/**` che lo è;
    `/api/auth/cambia-password` richiede un utente autenticato, qualunque
    ruolo).
  - `/api/sessioni/**`, `/api/comande/**` — richiedono ruolo `CAMERIERE`.
  - `/api/kds/**` — richiede ruolo `CUCINA`.
  - `/api/admin/**` — richiede ruolo `ADMIN`.
  - `/ws-kds/**` (handshake WebSocket del KDS) — `permitAll` nella catena
    Spring Security: l'autenticazione per-ruolo è delegata a
    `KdsHandshakeAuthInterceptor`, che legge il JWT da header `Authorization`
    o da query param `access_token` (necessario perché un client WebSocket
    nativo da browser non può impostare header custom sull'handshake) e
    richiede ruolo `CUCINA`.
  - qualunque altra rotta — richiede comunque autenticazione (nessun
    endpoint resta aperto per omissione).
- **`cameriereId` non è mai accettato dal client**: `ApriSessioneRequest` e
  `SincronizzaComandaRequest` non hanno più questo campo nel payload; viene
  ricavato dalle claim del JWT (`AuthenticatedUser`, iniettato nei
  controller via `@AuthenticationPrincipal`), per evitare che un
  dispositivo compromesso possa intestare comande a un altro cameriere.

## Segreto JWT

`app.jwt.secret` in `application.yml` ha un valore di default valido **solo
per sviluppo**. In ogni installazione on-premise va sovrascritto (es. con
la variabile d'ambiente `APP_JWT_SECRET`) con una stringa lunga e casuale,
generata una volta per installazione.

## Utente admin di bootstrap

La migrazione `V4__seed_admin_utente.sql` crea un utente `admin` /
`cambiami-subito` con ruolo `ADMIN`, necessario per il primo accesso (senza
di esso nessun utente esisterebbe per crearne altri). **Questa password va
cambiata immediatamente dopo il primo login**, prima di creare gli utenti
reali (cameriere, cucina) — non è pensata per sopravvivere oltre il primo
accesso in un ambiente reale.

## Cambio password e gestione utenti

- **`POST /api/auth/cambia-password`** (autenticato, qualunque ruolo):
  richiede la password corrente (`vecchiaPassword`) e la nuova
  (`nuovaPassword`, minimo 8 caratteri); usa questo endpoint per cambiare la
  password `cambiami-subito` dell'admin di bootstrap al primo accesso.
- **`POST /api/admin/utenti`** (ruolo `ADMIN`): crea un utente
  (`username`, `password`, `ruolo`), 409 se lo username è già in uso.
- **`POST /api/admin/utenti/{id}/disattiva`** (ruolo `ADMIN`): disattiva un
  utente (`Utente.disattiva()`); un utente disattivato non può più fare
  login.
- **`GET /api/admin/utenti`** (ruolo `ADMIN`): elenco utenti.

## Logout

- **`POST /api/auth/logout`** (autenticato): revoca il token corrente
  aggiungendo il suo `jti` a una blacklist (tabella `token_revocato`,
  `TokenRevocatoService`), controllata da `JwtAuthenticationFilter` e da
  `KdsHandshakeAuthInterceptor` a ogni richiesta. Le righe scadute vengono
  pulite periodicamente (`TokenRevocatoService.pulisciScaduti`,
  `@Scheduled`). Non è un vero refresh token: con JWT stateless a validità
  fissa (12h) non c'è altro stato da gestire lato client dopo il logout —
  il dispositivo dovrà rifare login.

## Non ancora implementato

- Refresh token (token di breve durata + refresh separato): non necessario
  finché la validità di 12h per turno resta la scelta di design corretta
  per i dispositivi a connettività intermittente (vedi sopra); da
  rivalutare solo se emergesse un requisito di sicurezza più stringente.
