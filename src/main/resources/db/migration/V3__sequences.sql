-- V3: sequenze monotone usate dal motore di coursing per assegnare
-- seq_server (ordine di registrazione comande) e seq_coda (ordine di
-- ingresso in coda cucina dei gruppi di invio). Backate da sequenze DB
-- native per garantire monotonicita' e atomicita' anche in caso di accessi
-- concorrenti, senza bisogno di lock applicativi.

CREATE SEQUENCE seq_comanda_server START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_gruppo_coda START WITH 1 INCREMENT BY 1;
