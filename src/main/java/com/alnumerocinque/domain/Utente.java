package com.alnumerocinque.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "utente")
@Getter
@NoArgsConstructor
public class Utente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RuoloUtente ruolo;

    @Column(nullable = false)
    private boolean attivo = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Utente(String username, String passwordHash, RuoloUtente ruolo) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.ruolo = ruolo;
        this.attivo = true;
        this.createdAt = OffsetDateTime.now();
    }

    public void disattiva() {
        this.attivo = false;
    }

    public void cambiaPassword(String nuovoPasswordHash) {
        this.passwordHash = nuovoPasswordHash;
    }
}
