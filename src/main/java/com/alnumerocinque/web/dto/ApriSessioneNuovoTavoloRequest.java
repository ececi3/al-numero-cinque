package com.alnumerocinque.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Apertura sessione su un tavolo identificato per numero anziche' per id: se
 * il numero non corrisponde a nessun tavolo esistente ne viene creato uno
 * nuovo al volo (vedi SessioneService.apriSessioneNuovoTavolo). A differenza
 * di ApriSessioneRequest, e' un'operazione online-only (Tavolo non puo' mai
 * essere creato offline, vedi Tavolo), quindi non e' accodabile nella coda
 * di sync offline del dispositivo cameriere.
 */
public record ApriSessioneNuovoTavoloRequest(
        @NotNull UUID id,
        @NotBlank String numeroTavolo,
        @Positive int numeroCoperti
) {
}
