package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.Comanda;
import com.alnumerocinque.domain.Sessione;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Stato completo di una sessione (comande/gruppi/righe incluse), usato per
 * recuperare una sessione gia' aperta da un dispositivo diverso da quello
 * che l'ha creata: l'indice tavolo -> sessione del cameriere vive solo nel
 * localStorage del dispositivo di apertura (vedi
 * frontend/src/offline/indiceTavoli.ts), quindi un secondo dispositivo deve
 * poter richiedere l'intero stato al server invece di fare affidamento sulla
 * cache locale.
 */
public record SessioneDettaglioResponse(
        UUID id,
        Long tavoloId,
        Long cameriereId,
        int numeroCoperti,
        String stato,
        OffsetDateTime apertaAt,
        List<Long> tavoliAggregatiIds,
        List<ComandaResponse> comande
) {
    public static SessioneDettaglioResponse of(Sessione sessione, List<Long> tavoliAggregatiIds, List<Comanda> comande) {
        return new SessioneDettaglioResponse(
                sessione.getId(),
                sessione.getTavolo().getId(),
                sessione.getCameriereId(),
                sessione.getNumeroCoperti(),
                sessione.getStato().name(),
                sessione.getApertaAt(),
                tavoliAggregatiIds,
                comande.stream().map(ComandaResponse::of).toList()
        );
    }
}
