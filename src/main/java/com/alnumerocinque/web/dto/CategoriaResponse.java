package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.Categoria;

public record CategoriaResponse(Long id, String nome) {
    public static CategoriaResponse of(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNome());
    }
}
