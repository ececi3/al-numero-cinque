package com.alnumerocinque.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Voce di menu'. {@code inviaInCucina} e' la fonte di verita' per il routing
 * cucina: se false (es. una bibita), la riga d'ordine relativa non genera
 * lavoro per il KDS pur facendo parte della comanda e del conto.
 */
@Entity
@Table(name = "menu_item")
@Getter
@NoArgsConstructor
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String nome;

    @Column(length = 512)
    private String descrizione;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal prezzo;

    // EAGER: relazione verso una tabella di lookup piccola (categorie), letta
    // ad ogni MenuItemResponse.of(); evita di dover annotare @EntityGraph su
    // ogni query di MenuItemRepository per scongiurare LazyInitializationException
    // fuori transazione (open-in-view e' disattivato, vedi application.yml).
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(name = "invia_in_cucina", nullable = false)
    private boolean inviaInCucina = true;

    @Column(nullable = false)
    private boolean disponibile = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public MenuItem(String nome, String descrizione, BigDecimal prezzo, Categoria categoria, boolean inviaInCucina) {
        this.nome = nome;
        this.descrizione = descrizione;
        this.prezzo = prezzo;
        this.categoria = categoria;
        this.inviaInCucina = inviaInCucina;
        this.disponibile = true;
        this.createdAt = OffsetDateTime.now();
    }

    public void rendiNonDisponibile() {
        this.disponibile = false;
    }

    public void rendiDisponibile() {
        this.disponibile = true;
    }
}
