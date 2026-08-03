package com.alnumerocinque.web;

import com.alnumerocinque.security.AuthenticatedUser;
import com.alnumerocinque.service.ComandaSyncService;
import com.alnumerocinque.web.dto.ComandaResponse;
import com.alnumerocinque.web.dto.SincronizzaComandaRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comande")
public class ComandaController {

    private final ComandaSyncService comandaSyncService;

    public ComandaController(ComandaSyncService comandaSyncService) {
        this.comandaSyncService = comandaSyncService;
    }

    /**
     * Sincronizza una comanda costruita offline. Idempotente: un retry sulla
     * stessa comanda (stesso UUID) dopo un ack perso restituisce lo stato
     * gia' registrato senza ripetere il fire del primo gruppo.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ComandaResponse sincronizza(@Valid @RequestBody SincronizzaComandaRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser utente) {
        return ComandaResponse.of(comandaSyncService.sincronizza(request, utente.id()));
    }
}
