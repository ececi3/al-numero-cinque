package com.alnumerocinque.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AggiungiRigaRequest(
        @NotNull Long menuItemId,
        @Positive int quantita,
        String note
) {
}
