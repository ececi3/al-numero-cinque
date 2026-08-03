package com.alnumerocinque.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiaPasswordRequest(
        @NotBlank String vecchiaPassword,
        @NotBlank @Size(min = 8, message = "deve essere lunga almeno 8 caratteri") String nuovaPassword
) {
}
