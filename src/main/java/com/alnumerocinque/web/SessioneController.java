package com.alnumerocinque.web;

import com.alnumerocinque.security.AuthenticatedUser;
import com.alnumerocinque.service.SessioneService;
import com.alnumerocinque.web.dto.ApriSessioneRequest;
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
}
