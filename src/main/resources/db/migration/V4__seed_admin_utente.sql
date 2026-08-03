-- V4: utente ADMIN di bootstrap, necessario per poter accedere al sistema
-- la primissima volta (altrimenti nessun utente esisterebbe per fare login
-- e creare gli altri utenti). Password: "cambiami-subito" (hash bcrypt).
--
-- ATTENZIONE OPERATIVA: questa password va cambiata IMMEDIATAMENTE dopo il
-- primo login in ogni installazione, prima di creare gli utenti reali
-- (cameriere, cucina). Non e' pensata per essere usata in produzione oltre
-- il primo accesso.

INSERT INTO utente (username, password_hash, ruolo, attivo, created_at)
VALUES ('admin', '$2b$10$fhmuuyIy2ADTTvfiZPwolePlqcbyR6u1d3Zn6I0g5Et1n0sqwMrV2', 'ADMIN', TRUE, now());
