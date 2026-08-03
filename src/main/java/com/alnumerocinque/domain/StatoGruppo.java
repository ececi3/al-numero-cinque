package com.alnumerocinque.domain;

/**
 * Stati del ciclo di vita di un {@link GruppoInvio}.
 *
 * TRATTENUTO -> IN_CODA -> IN_PREP -> PRONTO -> SERVITO
 *
 * - TRATTENUTO: gruppo creato ma non ancora inviato in cucina (portata successiva
 *   in attesa di essere "sparata").
 * - IN_CODA: il gruppo e' stato inviato in cucina (fire), ha un seq_coda assegnato
 *   ed e' in attesa che la cucina inizi la preparazione.
 * - IN_PREP: la cucina ha iniziato a preparare il gruppo.
 * - PRONTO: la cucina ha completato la preparazione (segnaPronto). Questo e'
 *   anche il momento in cui, se esiste un gruppo TRATTENUTO successivo nella
 *   stessa comanda, viene sparato automaticamente (fire-on-ready), in modo
 *   sincrono nella stessa transazione.
 * - SERVITO: il gruppo e' stato consegnato al tavolo.
 *
 * Un gruppo le cui righe non richiedono lavorazione in cucina (nessuna riga
 * con {@code MenuItem.inviaInCucina == true}, es. una portata di sole
 * bevande) salta interamente la coda: transisce direttamente da TRATTENUTO a
 * SERVITO (vedi GruppoInvio.servaSenzaCucina() e CoursingService), senza mai
 * passare da IN_CODA/IN_PREP/PRONTO.
 */
public enum StatoGruppo {
    TRATTENUTO,
    IN_CODA,
    IN_PREP,
    PRONTO,
    SERVITO;

    public boolean puoTransireA(StatoGruppo target) {
        return switch (this) {
            case TRATTENUTO -> target == IN_CODA || target == SERVITO;
            case IN_CODA -> target == IN_PREP;
            case IN_PREP -> target == PRONTO;
            case PRONTO -> target == SERVITO;
            case SERVITO -> false;
        };
    }
}
