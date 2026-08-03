package com.alnumerocinque.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreaCategoriaRequest(@NotBlank String nome) {
}
