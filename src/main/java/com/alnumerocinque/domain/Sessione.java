package com.alnumerocinque.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sessione di un tavolo. L'id e' generato lato client (waiter device) per
 * poter essere creata offline senza coordinamento col server: la sua
 * creazione e' un'operazione append-capable, mentre l'aggregazione tra
 * sessioni/tavoli e' online-only e gestita altrove.
 *
 * numeroCoperti e' obbligatorio ed e' richiesto per l'analytics.
 */
@Entity
@Table(name = "sessione")
@Getter
@NoArgsConstructor
public class Sessione {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tavolo_id", nullable = false)
    private Tavolo tavolo;

    @Column(name = "cameriere_id", nullable = false)
    private Long cameriereId;

    @Column(name = "numero_coperti", nullable = false)
    private int numeroCoperti;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatoSessione stato = StatoSessione.APERTA;

    @Column(name = "aperta_at", nullable = false)
    private OffsetDateTime apertaAt;

    @Column(name = "chiusa_at")
    private OffsetDateTime chiusaAt;

    public Sessione(UUID id, Tavolo tavolo, Long cameriereId, int numeroCoperti, OffsetDateTime apertaAt) {
        if (numeroCoperti <= 0) {
            throw new IllegalArgumentException("numeroCoperti deve essere maggiore di zero");
        }
        this.id = id;
        this.tavolo = tavolo;
        this.cameriereId = cameriereId;
        this.numeroCoperti = numeroCoperti;
        this.stato = StatoSessione.APERTA;
        this.apertaAt = apertaAt;
    }

    public void chiudi() {
        if (this.stato == StatoSessione.CHIUSA) {
            throw new IllegalStateException("Sessione " + id + " gia' chiusa");
        }
        this.stato = StatoSessione.CHIUSA;
        this.chiusaAt = OffsetDateTime.now();
    }

    public boolean isAperta() {
        return this.stato == StatoSessione.APERTA;
    }
}
