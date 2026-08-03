package com.alnumerocinque.web;

import com.alnumerocinque.service.UtenteService;
import com.alnumerocinque.web.dto.CreaUtenteRequest;
import com.alnumerocinque.web.dto.UtenteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Amministrazione utenti, riservata al ruolo ADMIN (vedi SecurityConfig).
 * Necessaria per poter creare cameriere/cucina reali e disattivare
 * l'admin di bootstrap dopo il primo accesso (vedi docs/07-auth.md).
 */
@RestController
@RequestMapping("/api/admin/utenti")
public class AdminUtenteController {

    private final UtenteService utenteService;

    public AdminUtenteController(UtenteService utenteService) {
        this.utenteService = utenteService;
    }

    @GetMapping
    public List<UtenteResponse> elenca() {
        return utenteService.elencaUtenti().stream().map(UtenteResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UtenteResponse crea(@Valid @RequestBody CreaUtenteRequest request) {
        return UtenteResponse.of(utenteService.creaUtente(request.username(), request.password(), request.ruolo()));
    }

    @PostMapping("/{id}/disattiva")
    @ResponseStatus(HttpStatus.OK)
    public void disattiva(@PathVariable Long id) {
        utenteService.disattivaUtente(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void elimina(@PathVariable Long id) {
        utenteService.eliminaUtente(id);
    }
}
