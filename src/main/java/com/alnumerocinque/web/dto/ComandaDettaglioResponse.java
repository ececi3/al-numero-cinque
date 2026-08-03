package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.Comanda;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dettaglio completo di una comanda per la cucina: tutte le sue portate
 * (gruppi) con stato e righe, piu' tavolo/cameriere/orario. Serve a
 * correlare le portate di una stessa comanda quando finiscono in colonne
 * diverse della coda cucina (una per stato), cosa che le card per-portata
 * da sole non permettono di vedere.
 */
public record ComandaDettaglioResponse(
        UUID id,
        String tavoloNumero,
        Long cameriereId,
        String cameriereUsername,
        OffsetDateTime creataAt,
        List<GruppoInvioResponse> gruppi
) {
    public static ComandaDettaglioResponse of(Comanda comanda, String cameriereUsername) {
        return new ComandaDettaglioResponse(
                comanda.getId(),
                comanda.getSessione().getTavolo().getNumero(),
                comanda.getCameriereId(),
                cameriereUsername,
                comanda.getCreataAt(),
                comanda.getGruppi().stream().map(GruppoInvioResponse::of).toList()
        );
    }
}
