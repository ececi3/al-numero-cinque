package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.Utente;

public record UtenteResponse(
        Long id,
        String username,
        String ruolo,
        boolean attivo
) {
    public static UtenteResponse of(Utente utente) {
        return new UtenteResponse(utente.getId(), utente.getUsername(), utente.getRuolo().name(), utente.isAttivo());
    }
}
