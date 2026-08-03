package com.alnumerocinque.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Blacklist di logout per JWT stateless: una riga per ogni token revocato
 * prima della sua scadenza naturale, identificato dal claim "jti".
 * {@code scadeAt} consente la pulizia periodica delle righe non piu' utili
 * (il token e' comunque scaduto naturalmente, vedi TokenRevocatoService).
 */
@Entity
@Table(name = "token_revocato")
@Getter
@NoArgsConstructor
public class TokenRevocato {

    @Id
    @Column(length = 64)
    private String jti;

    @Column(name = "revocato_at", nullable = false)
    private OffsetDateTime revocatoAt;

    @Column(name = "scade_at", nullable = false)
    private OffsetDateTime scadeAt;

    public TokenRevocato(String jti, OffsetDateTime scadeAt) {
        this.jti = jti;
        this.revocatoAt = OffsetDateTime.now();
        this.scadeAt = scadeAt;
    }
}
