package com.alnumerocinque.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Apertura sessione: id generato dal client (waiter device), operazione
 * append-capable e quindi eseguibile offline (viene poi sincronizzata).
 * cameriereId NON e' nel payload: viene ricavato dal JWT autenticato, per
 * non fidarsi di un id inviato dal client.
 */
public record ApriSessioneRequest(
        @NotNull UUID id,
        @NotNull Long tavoloId,
        @Positive int numeroCoperti
) {
}
