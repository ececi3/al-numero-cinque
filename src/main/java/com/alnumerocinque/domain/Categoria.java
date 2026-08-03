package com.alnumerocinque.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Categoria di menu' (es. "Antipasti", "Primi"), gestita da admin come
 * elenco chiuso invece che stringa libera su MenuItem: cosi' il cameriere
 * puo' navigare il menu raggruppato in modo consistente.
 */
@Entity
@Table(name = "categoria")
@Getter
@NoArgsConstructor
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String nome;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Categoria(String nome) {
        this.nome = nome;
        this.createdAt = OffsetDateTime.now();
    }
}
