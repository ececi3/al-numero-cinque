package com.alnumerocinque.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Genera i valori monotoni di seq_server (ordine di registrazione comande)
 * e seq_coda (ordine di ingresso in coda cucina) appoggiandosi alle sequenze
 * native del database (V3__sequences.sql). L'uso di sequenze DB, invece di
 * un contatore in-memory, garantisce correttezza anche con piu' richieste
 * concorrenti sull'unico nodo on-premise, senza bisogno di lock applicativi.
 *
 * Nota: la sintassi nextval('...') e' supportata sia da PostgreSQL sia da H2
 * in modalita' di compatibilita' PostgreSQL (usata nel profilo di test),
 * quindi il componente funziona identico in dev/produzione e nei test.
 */
@Component
public class SequenzaGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    public long prossimoSeqServer() {
        return nextval("seq_comanda_server");
    }

    public long prossimoSeqCoda() {
        return nextval("seq_gruppo_coda");
    }

    private long nextval(String sequenceName) {
        Object result = entityManager
                .createNativeQuery("SELECT nextval('" + sequenceName + "')")
                .getSingleResult();
        return ((Number) result).longValue();
    }
}
