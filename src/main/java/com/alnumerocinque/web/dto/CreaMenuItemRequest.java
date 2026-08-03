package com.alnumerocinque.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreaMenuItemRequest(
        @NotBlank String nome,
        String descrizione,
        @NotNull @PositiveOrZero BigDecimal prezzo,
        Long categoriaId,
        boolean inviaInCucina
) {
}
