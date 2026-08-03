package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.Comanda;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ComandaResponse(
        UUID id,
        UUID sessioneId,
        Long cameriereId,
        Long seqServer,
        OffsetDateTime creataAt,
        List<GruppoInvioResponse> gruppi
) {
    public static ComandaResponse of(Comanda comanda) {
        return new ComandaResponse(
                comanda.getId(),
                comanda.getSessione().getId(),
                comanda.getCameriereId(),
                comanda.getSeqServer(),
                comanda.getCreataAt(),
                comanda.getGruppi().stream().map(GruppoInvioResponse::of).toList()
        );
    }
}
