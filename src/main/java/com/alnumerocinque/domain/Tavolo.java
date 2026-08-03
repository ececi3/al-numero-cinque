package com.alnumerocinque.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Un tavolo fisico del ristorante. La mutazione di stato (occupazione,
 * aggregazione con altri tavoli) e' un'operazione online-only: a differenza
 * di Sessione e Comanda, Tavolo non ha id client-generated perche' non deve
 * mai essere creato offline.
 */
@Entity
@Table(name = "tavolo")
@Getter
@NoArgsConstructor
public class Tavolo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatoTavolo stato = StatoTavolo.LIBERO;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Tavolo(String numero) {
        this.numero = numero;
        this.stato = StatoTavolo.LIBERO;
        this.createdAt = OffsetDateTime.now();
    }

    public void occupa() {
        if (this.stato == StatoTavolo.OCCUPATO) {
            throw new IllegalStateException("Tavolo " + numero + " gia' occupato");
        }
        this.stato = StatoTavolo.OCCUPATO;
    }

    public void libera() {
        this.stato = StatoTavolo.LIBERO;
    }
}
