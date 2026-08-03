package com.alnumerocinque.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Riga d'ordine. Il prezzo viene congelato al momento della presa comanda
 * (prezzoCongelato) e non e' mai ricalcolato, anche se il prezzo del
 * MenuItem cambia successivamente. Nessuna modifica/cancellazione in v1:
 * le righe sono append-only una volta create.
 */
@Entity
@Table(name = "riga_ordine")
@Getter
@NoArgsConstructor
public class RigaOrdine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gruppo_invio_id", nullable = false)
    private GruppoInvio gruppoInvio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private int quantita;

    @Column(name = "prezzo_congelato", nullable = false, precision = 10, scale = 2)
    private BigDecimal prezzoCongelato;

    @Column(length = 256)
    private String note;

    @Column(name = "creata_at", nullable = false)
    private OffsetDateTime creataAt;

    RigaOrdine(GruppoInvio gruppoInvio, MenuItem menuItem, int quantita, BigDecimal prezzoCongelato, String note) {
        if (quantita <= 0) {
            throw new IllegalArgumentException("quantita deve essere maggiore di zero");
        }
        this.gruppoInvio = gruppoInvio;
        this.menuItem = menuItem;
        this.quantita = quantita;
        this.prezzoCongelato = prezzoCongelato;
        this.note = note;
        this.creataAt = OffsetDateTime.now();
    }

    public BigDecimal totaleRiga() {
        return prezzoCongelato.multiply(BigDecimal.valueOf(quantita));
    }
}
