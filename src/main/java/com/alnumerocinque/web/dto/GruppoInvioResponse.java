package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.GruppoInvio;
import com.alnumerocinque.domain.RigaOrdine;

import java.util.List;
import java.util.UUID;

public record GruppoInvioResponse(
        Long id,
        UUID comandaId,
        int numeroPortata,
        String stato,
        Long seqCoda,
        List<RigaKdsResponse> righe
) {
    public static GruppoInvioResponse of(GruppoInvio gruppo) {
        return new GruppoInvioResponse(
                gruppo.getId(),
                gruppo.getComanda().getId(),
                gruppo.getNumeroPortata(),
                gruppo.getStato().name(),
                gruppo.getSeqCoda(),
                gruppo.getRighe().stream().map(RigaKdsResponse::of).toList()
        );
    }

    public record RigaKdsResponse(Long id, Long menuItemId, String nome, int quantita, String note) {
        public static RigaKdsResponse of(RigaOrdine riga) {
            return new RigaKdsResponse(
                    riga.getId(), riga.getMenuItem().getId(), riga.getMenuItem().getNome(), riga.getQuantita(), riga.getNote());
        }
    }
}
