package com.alnumerocinque.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Unita' di invio in cucina: un raggruppamento di righe d'ordine della stessa
 * comanda destinate a essere preparate e servite insieme (una "portata").
 *
 * Le transizioni di stato sono incapsulate qui e validate contro
 * {@link StatoGruppo#puoTransireA}. L'orchestrazione del fire-on-ready
 * (sparare automaticamente il gruppo TRATTENUTO successivo quando questo
 * gruppo diventa PRONTO) e' responsabilita' del CoursingService, che opera
 * sulla comanda intera nella stessa transazione di segnaPronto().
 */
@Entity
@Table(name = "gruppo_invio")
@Getter
@NoArgsConstructor
public class GruppoInvio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comanda_id", nullable = false)
    private Comanda comanda;

    @Column(name = "numero_portata", nullable = false)
    private int numeroPortata;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatoGruppo stato = StatoGruppo.TRATTENUTO;

    @Column(name = "seq_coda", unique = true)
    private Long seqCoda;

    @Column(name = "creato_at", nullable = false)
    private OffsetDateTime creatoAt;

    @Column(name = "in_coda_at")
    private OffsetDateTime inCodaAt;

    @Column(name = "in_prep_at")
    private OffsetDateTime inPrepAt;

    @Column(name = "pronto_at")
    private OffsetDateTime prontoAt;

    @Column(name = "servito_at")
    private OffsetDateTime servitoAt;

    @OneToMany(mappedBy = "gruppoInvio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RigaOrdine> righe = new ArrayList<>();

    GruppoInvio(Comanda comanda, int numeroPortata) {
        this.comanda = comanda;
        this.numeroPortata = numeroPortata;
        this.stato = StatoGruppo.TRATTENUTO;
        this.creatoAt = OffsetDateTime.now();
    }

    /**
     * Usato sia in creazione (il gruppo e' allora TRATTENUTO, quindi passa
     * sempre il controllo) sia per aggiungere una voce a una comanda gia'
     * inviata: in quel caso richiede che il gruppo non sia ancora in
     * preparazione (vedi puoModificareVoci).
     */
    public RigaOrdine aggiungiRiga(MenuItem menuItem, int quantita, java.math.BigDecimal prezzoCongelato, String note) {
        verificaModificabileVoci();
        RigaOrdine riga = new RigaOrdine(this, menuItem, quantita, prezzoCongelato, note);
        this.righe.add(riga);
        return riga;
    }

    /** Rimuove una voce da una comanda gia' inviata; stesso vincolo di stato di aggiungiRiga. */
    public void rimuoviRiga(RigaOrdine riga) {
        verificaModificabileVoci();
        if (!this.righe.remove(riga)) {
            throw new IllegalArgumentException("Riga " + riga.getId() + " non appartiene al gruppo " + id);
        }
    }

    /**
     * True se il gruppo non ha ancora iniziato la preparazione: solo in
     * questa finestra ha senso aggiungere/togliere intere voci di menu,
     * perche' la cucina non le ha ancora viste. Una volta IN_PREP (o oltre),
     * solo le note restano modificabili (RigaOrdine.aggiornaNote).
     */
    public boolean puoModificareVoci() {
        return this.stato == StatoGruppo.TRATTENUTO || this.stato == StatoGruppo.IN_CODA;
    }

    private void verificaModificabileVoci() {
        if (!puoModificareVoci()) {
            throw new IllegalStateException(
                    "Gruppo " + id + " in stato " + stato + ": le voci si possono aggiungere/rimuovere solo prima che la preparazione sia iniziata");
        }
    }

    /** True se almeno una riga del gruppo richiede lavorazione in cucina. */
    public boolean richiedeCucina() {
        return this.righe.stream().anyMatch(r -> r.getMenuItem().isInviaInCucina());
    }

    private void transisciA(StatoGruppo target) {
        if (!this.stato.puoTransireA(target)) {
            throw new IllegalStateException(
                    "Transizione non valida per gruppo " + id + ": " + this.stato + " -> " + target);
        }
        this.stato = target;
    }

    /** Invio in cucina: assegna il seq_coda e passa a IN_CODA. */
    public void fire(long seqCoda) {
        transisciA(StatoGruppo.IN_CODA);
        this.seqCoda = seqCoda;
        this.inCodaAt = OffsetDateTime.now();
    }

    public void iniziaPreparazione() {
        transisciA(StatoGruppo.IN_PREP);
        this.inPrepAt = OffsetDateTime.now();
    }

    public void segnaPronto() {
        transisciA(StatoGruppo.PRONTO);
        this.prontoAt = OffsetDateTime.now();
    }

    public void segnaServito() {
        transisciA(StatoGruppo.SERVITO);
        this.servitoAt = OffsetDateTime.now();
    }

    /**
     * Salta la coda cucina: usato quando il gruppo non contiene alcuna riga
     * che richieda lavorazione (richiedeCucina() == false), es. una portata
     * di sole bevande. Transisce direttamente TRATTENUTO -> SERVITO, senza
     * assegnare seq_coda ne' passare da IN_PREP/PRONTO.
     */
    public void servaSenzaCucina() {
        transisciA(StatoGruppo.SERVITO);
        this.servitoAt = OffsetDateTime.now();
    }

    public boolean isTrattenuto() {
        return this.stato == StatoGruppo.TRATTENUTO;
    }
}
