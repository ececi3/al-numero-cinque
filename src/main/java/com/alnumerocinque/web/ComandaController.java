package com.alnumerocinque.web;

import com.alnumerocinque.security.AuthenticatedUser;
import com.alnumerocinque.service.ComandaModificaService;
import com.alnumerocinque.service.ComandaSyncService;
import com.alnumerocinque.web.dto.AggiornaNoteRequest;
import com.alnumerocinque.web.dto.AggiungiRigaRequest;
import com.alnumerocinque.web.dto.ComandaResponse;
import com.alnumerocinque.web.dto.GruppoInvioResponse;
import com.alnumerocinque.web.dto.SincronizzaComandaRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Sincronizzazione e modifica delle comande, riservate al ruolo CAMERIERE
 * (vedi SecurityConfig). La modifica di una comanda gia' inviata e' distinta
 * dal sync iniziale (vedi ComandaModificaService per i vincoli di stato).
 */
@RestController
@RequestMapping("/api/comande")
public class ComandaController {

    private final ComandaSyncService comandaSyncService;
    private final ComandaModificaService comandaModificaService;

    public ComandaController(ComandaSyncService comandaSyncService, ComandaModificaService comandaModificaService) {
        this.comandaSyncService = comandaSyncService;
        this.comandaModificaService = comandaModificaService;
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

    /**
     * Aggiunge una voce a un gruppo gia' inviato. 409 se il gruppo e' gia'
     * in preparazione o oltre (GruppoInvio.puoModificareVoci).
     */
    @PostMapping("/gruppi/{gruppoId}/righe")
    @ResponseStatus(HttpStatus.CREATED)
    public GruppoInvioResponse aggiungiRiga(@PathVariable Long gruppoId, @Valid @RequestBody AggiungiRigaRequest request) {
        return GruppoInvioResponse.of(
                comandaModificaService.aggiungiRiga(gruppoId, request.menuItemId(), request.quantita(), request.note()));
    }

    /** Rimuove una voce da un gruppo gia' inviato. Stesso vincolo di stato di aggiungiRiga. */
    @DeleteMapping("/gruppi/{gruppoId}/righe/{rigaId}")
    @ResponseStatus(HttpStatus.OK)
    public GruppoInvioResponse rimuoviRiga(@PathVariable Long gruppoId, @PathVariable Long rigaId) {
        return GruppoInvioResponse.of(comandaModificaService.rimuoviRiga(gruppoId, rigaId));
    }

    /** Aggiorna la nota di una riga esistente. Sempre permesso, qualunque stato del gruppo. */
    @PatchMapping("/righe/{rigaId}/note")
    @ResponseStatus(HttpStatus.OK)
    public void aggiornaNote(@PathVariable Long rigaId, @Valid @RequestBody AggiornaNoteRequest request) {
        comandaModificaService.aggiornaNoteRiga(rigaId, request.note());
    }
}
