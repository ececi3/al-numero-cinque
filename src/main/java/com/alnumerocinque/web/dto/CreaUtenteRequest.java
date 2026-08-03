package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.RuoloUtente;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreaUtenteRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8, message = "deve essere lunga almeno 8 caratteri") String password,
        @NotNull RuoloUtente ruolo
) {
}
