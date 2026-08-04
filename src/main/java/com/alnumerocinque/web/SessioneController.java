package com.alnumerocinque.web;

import com.alnumerocinque.security.AuthenticatedUser;
import com.alnumerocinque.service.SessioneService;
import com.alnumerocinque.web.dto.ApriSessioneNuovoTavoloRequest;
import com.alnumerocinque.web.dto.ApriSessioneRequest;
import com.alnumerocinque.web.dto.ContoResponse;
import com.alnumerocinque.web.dto.SessioneDettaglioResponse;
import com.alnumerocinque.web.dto.SessioneResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessioni")
public class SessioneController {

    private final SessioneService sessioneService;

    public SessioneController(SessioneService sessioneService) {
        this.sessioneService = sessioneService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public SessioneResponse apri(@Valid @RequestBody ApriSessioneRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser utente) {
        return SessioneResponse.of(sessioneService.apriSessione(request, utente.id()));
    }

    /**
     * Apre una sessione su un tavolo identificato per numero: se il tavolo
     * non esiste ancora viene creato al volo (vedi
     * SessioneService.apriSessioneNuovoTavolo). Il cameriere non deve piu'
     * passare dall'admin per far nascere un tavolo nuovo; operazione
     * online-only, a differenza di {@link #apri}.
     */
    @PostMapping("/nuovo-tavolo")
    @ResponseStatus(HttpStatus.OK)
    public SessioneResponse apriNuovoTavolo(@Valid @RequestBody ApriSessioneNuovoTavoloRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser utente) {
        return SessioneResponse.of(sessioneService.apriSessioneNuovoTavolo(request, utente.id()));
    }

    @PostMapping("/{id}/chiudi")
    @ResponseStatus(HttpStatus.OK)
    public SessioneResponse chiudi(@PathVariable UUID id) {
        return SessioneResponse.of(sessioneService.chiudiSessione(id));
    }

    @PostMapping("/{id}/aggrega-tavolo/{tavoloId}")
    @ResponseStatus(HttpStatus.OK)
    public SessioneResponse aggregaTavolo(@PathVariable UUID id, @PathVariable Long tavoloId) {
        var sessione = sessioneService.aggregaTavolo(id, tavoloId);
        return SessioneResponse.of(sessione, sessioneService.tavoliAggregatiDi(sessione));
    }

    /**
     * Recupera lo stato completo di una sessione gia' aperta (comande incluse).
     * Serve a un dispositivo diverso da quello che ha aperto la sessione per
     * poterla riprendere: l'indice tavolo -> sessione del cameriere e' per
     * design solo nel localStorage del dispositivo di apertura (vedi
     * frontend/src/offline/indiceTavoli.ts), quindi senza questo endpoint un
     * secondo dispositivo non avrebbe modo di scoprire/visualizzare una
     * sessione aperta altrove.
     */
    @GetMapping("/{id}")
    public SessioneDettaglioResponse dettaglio(@PathVariable UUID id) {
        return sessioneService.dettaglio(id);
    }

    @GetMapping("/per-tavolo/{tavoloId}")
    public SessioneDettaglioResponse dettaglioPerTavolo(@PathVariable Long tavoloId) {
        return sessioneService.dettaglioPerTavolo(tavoloId);
    }

    /** Totale da pagare per la sessione, consultabile in qualunque momento (vedi SessioneService.conto). */
    @GetMapping("/{id}/conto")
    public ContoResponse conto(@PathVariable UUID id) {
        return sessioneService.conto(id);
    }
}
