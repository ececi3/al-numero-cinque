package com.alnumerocinque.service;

import com.alnumerocinque.domain.Sessione;

/**
 * Payload serializzato nell'outbox per gli eventi di ciclo di vita di una
 * Sessione (vedi docs/05-api-events.md: SESSIONE / SESSIONE_CHIUSA).
 */
public record SessioneEventPayload(
        String sessioneId,
        Long tavoloId,
        int numeroCoperti,
        String stato
) {
    public static SessioneEventPayload of(Sessione sessione) {
        return new SessioneEventPayload(
                sessione.getId().toString(),
                sessione.getTavolo().getId(),
                sessione.getNumeroCoperti(),
                sessione.getStato().name()
        );
    }
}
