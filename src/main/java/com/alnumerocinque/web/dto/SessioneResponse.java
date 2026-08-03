package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.Sessione;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SessioneResponse(
        UUID id,
        Long tavoloId,
        Long cameriereId,
        int numeroCoperti,
        String stato,
        OffsetDateTime apertaAt,
        List<Long> tavoliAggregatiIds
) {
    public static SessioneResponse of(Sessione sessione) {
        return of(sessione, List.of());
    }

    public static SessioneResponse of(Sessione sessione, List<Long> tavoliAggregatiIds) {
        return new SessioneResponse(
                sessione.getId(),
                sessione.getTavolo().getId(),
                sessione.getCameriereId(),
                sessione.getNumeroCoperti(),
                sessione.getStato().name(),
                sessione.getApertaAt(),
                tavoliAggregatiIds
        );
    }
}
