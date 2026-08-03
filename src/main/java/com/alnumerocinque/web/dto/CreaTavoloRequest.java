package com.alnumerocinque.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreaTavoloRequest(
        @NotBlank String numero
) {
}
