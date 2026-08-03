package com.alnumerocinque.service;

import com.alnumerocinque.domain.GruppoInvio;
import com.alnumerocinque.domain.RigaOrdine;

import java.util.List;

/**
 * Payload serializzato nell'outbox per gli eventi di transizione di stato
 * di un GruppoInvio. Usato dal feed WebSocket del KDS e dall'analytics.
 */
public record GruppoInvioEventPayload(
        Long gruppoInvioId,
        String comandaId,
        int numeroPortata,
        Long seqCoda,
        String stato,
        List<RigaEventPayload> righe
) {
    public static GruppoInvioEventPayload of(GruppoInvio gruppo) {
        return new GruppoInvioEventPayload(
                gruppo.getId(),
                gruppo.getComanda().getId().toString(),
                gruppo.getNumeroPortata(),
                gruppo.getSeqCoda(),
                gruppo.getStato().name(),
                gruppo.getRighe().stream().map(RigaEventPayload::of).toList()
        );
    }

    public record RigaEventPayload(String nome, int quantita, String note) {
        public static RigaEventPayload of(RigaOrdine riga) {
            return new RigaEventPayload(riga.getMenuItem().getNome(), riga.getQuantita(), riga.getNote());
        }
    }
}
