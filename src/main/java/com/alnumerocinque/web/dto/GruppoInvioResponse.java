package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.GruppoInvio;

import java.util.UUID;

public record GruppoInvioResponse(
        Long id,
        UUID comandaId,
        int numeroPortata,
        String stato,
        Long seqCoda
) {
    public static GruppoInvioResponse of(GruppoInvio gruppo) {
        return new GruppoInvioResponse(
                gruppo.getId(),
                gruppo.getComanda().getId(),
                gruppo.getNumeroPortata(),
                gruppo.getStato().name(),
                gruppo.getSeqCoda()
        );
    }
}
