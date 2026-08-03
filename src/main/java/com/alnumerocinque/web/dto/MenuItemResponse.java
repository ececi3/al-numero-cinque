package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.MenuItem;

import java.math.BigDecimal;

public record MenuItemResponse(
        Long id,
        String nome,
        String descrizione,
        BigDecimal prezzo,
        Long categoriaId,
        String categoria,
        boolean inviaInCucina,
        boolean disponibile
) {
    public static MenuItemResponse of(MenuItem menuItem) {
        return new MenuItemResponse(
                menuItem.getId(),
                menuItem.getNome(),
                menuItem.getDescrizione(),
                menuItem.getPrezzo(),
                menuItem.getCategoria() != null ? menuItem.getCategoria().getId() : null,
                menuItem.getCategoria() != null ? menuItem.getCategoria().getNome() : null,
                menuItem.isInviaInCucina(),
                menuItem.isDisponibile()
        );
    }
}
