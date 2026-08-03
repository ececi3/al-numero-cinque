package com.alnumerocinque.security;

import com.alnumerocinque.domain.Utente;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

/**
 * Genera e valida i JWT usati dai dispositivi (cameriere, KDS) per
 * autenticarsi. Validita' lunga (default 12 ore, un turno di lavoro): il
 * dispositivo cameriere deve poter continuare a operare offline con
 * l'ultimo token valido, senza dover ri-autenticarsi a ogni richiesta
 * quando la connettivita' torna solo a intermittenza.
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long validitaMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.validita-ore:12}") long validitaOre) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validitaMs = validitaOre * 3_600_000L;
    }

    public String generaToken(Utente utente) {
        Instant ora = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(utente.getUsername())
                .claim("utenteId", utente.getId())
                .claim("ruolo", utente.getRuolo().name())
                .issuedAt(Date.from(ora))
                .expiration(Date.from(ora.plusMillis(validitaMs)))
                .signWith(key)
                .compact();
    }

    /**
     * Limite superiore di scadenza per un token emesso ora, usato per
     * datare le righe della blacklist di logout (TokenRevocatoService):
     * dopo questo istante il token sarebbe comunque scaduto naturalmente,
     * quindi la riga di revoca puo' essere pulita.
     */
    public OffsetDateTime scadenzaMassima() {
        return OffsetDateTime.now().plus(Duration.ofMillis(validitaMs));
    }

    /** Lancia una JwtException (o sottoclasse) se il token non e' valido o e' scaduto. */
    public Jws<Claims> valida(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }
}
