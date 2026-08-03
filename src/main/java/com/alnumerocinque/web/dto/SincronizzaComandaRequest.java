package com.alnumerocinque.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * Comanda completa costruita offline dal dispositivo cameriere (id, gruppi
 * per portata, righe per gruppo) e inviata al sync quando torna online. Il
 * prezzo NON viene mai accettato dal client: viene congelato server-side
 * leggendo il MenuItem corrente, per evitare manomissioni. cameriereId NON
 * e' nel payload: viene ricavato dal JWT autenticato.
 */
public record SincronizzaComandaRequest(
        @NotNull UUID id,
        @NotNull UUID sessioneId,
        @NotEmpty @Valid List<GruppoRequest> gruppi
) {
    public record GruppoRequest(
            @Positive int numeroPortata,
            @NotEmpty @Valid List<RigaRequest> righe
    ) {
    }

    public record RigaRequest(
            @NotNull Long menuItemId,
            @Positive int quantita,
            String note
    ) {
    }
}
