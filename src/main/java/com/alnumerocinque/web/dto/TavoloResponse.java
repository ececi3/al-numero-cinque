package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.Tavolo;

public record TavoloResponse(
        Long id,
        String numero,
        String stato
) {
    public static TavoloResponse of(Tavolo tavolo) {
        return new TavoloResponse(tavolo.getId(), tavolo.getNumero(), tavolo.getStato().name());
    }
}
