package com.alnumerocinque.web;

import com.alnumerocinque.domain.Utente;
import com.alnumerocinque.repository.UtenteRepository;
import com.alnumerocinque.security.AuthenticatedUser;
import com.alnumerocinque.security.JwtService;
import com.alnumerocinque.service.TokenRevocatoService;
import com.alnumerocinque.service.UtenteService;
import com.alnumerocinque.web.dto.CambiaPasswordRequest;
import com.alnumerocinque.web.dto.LoginRequest;
import com.alnumerocinque.web.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UtenteRepository utenteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UtenteService utenteService;
    private final TokenRevocatoService tokenRevocatoService;

    public AuthController(UtenteRepository utenteRepository, PasswordEncoder passwordEncoder,
                           JwtService jwtService, UtenteService utenteService,
                           TokenRevocatoService tokenRevocatoService) {
        this.utenteRepository = utenteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.utenteService = utenteService;
        this.tokenRevocatoService = tokenRevocatoService;
    }

    /** Pubblico (vedi SecurityConfig: solo /api/auth/login e' permitAll). */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Utente utente = utenteRepository.findByUsername(request.username())
                .filter(Utente::isAttivo)
                .orElseThrow(() -> new BadCredentialsException("Credenziali non valide"));

        if (!passwordEncoder.matches(request.password(), utente.getPasswordHash())) {
            throw new BadCredentialsException("Credenziali non valide");
        }

        String token = jwtService.generaToken(utente);
        return new LoginResponse(token, utente.getUsername(), utente.getRuolo().name());
    }

    /** Autenticato (qualunque ruolo): richiede la password corrente per cambiarla. */
    @PostMapping("/cambia-password")
    @ResponseStatus(HttpStatus.OK)
    public void cambiaPassword(@Valid @RequestBody CambiaPasswordRequest request,
                                @AuthenticationPrincipal AuthenticatedUser utente) {
        utenteService.cambiaPassword(utente.id(), request.vecchiaPassword(), request.nuovaPassword());
    }

    /**
     * Revoca il token corrente (vedi TokenRevocatoService): con JWT
     * stateless non c'e' altro modo di forzare un logout prima della
     * scadenza naturale (12h di default).
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public void logout(@AuthenticationPrincipal AuthenticatedUser utente) {
        tokenRevocatoService.revoca(utente.jti(), jwtService.scadenzaMassima());
    }
}
