package com.alnumerocinque.service;

import com.alnumerocinque.domain.GruppoInvio;

/**
 * Payload serializzato nell'outbox per gli eventi di transizione di stato
 * di un GruppoInvio. Usato dal feed WebSocket del KDS e dall'analytics.
 */
public record GruppoInvioEventPayload(
        Long gruppoInvioId,
        String comandaId,
        int numeroPortata,
        Long seqCoda,
        String stato
) {
    public static GruppoInvioEventPayload of(GruppoInvio gruppo) {
        return new GruppoInvioEventPayload(
                gruppo.getId(),
                gruppo.getComanda().getId().toString(),
                gruppo.getNumeroPortata(),
                gruppo.getSeqCoda(),
                gruppo.getStato().name()
        );
    }
}
