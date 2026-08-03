package com.alnumerocinque.service;

import com.alnumerocinque.domain.RuoloUtente;
import com.alnumerocinque.domain.Utente;
import com.alnumerocinque.repository.UtenteRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestione utenti: cambio password self-service e amministrazione utenti
 * (creazione/disattivazione, riservata al ruolo ADMIN, vedi
 * AdminUtenteController).
 */
@Service
public class UtenteService {

    private final UtenteRepository utenteRepository;
    private final PasswordEncoder passwordEncoder;

    public UtenteService(UtenteRepository utenteRepository, PasswordEncoder passwordEncoder) {
        this.utenteRepository = utenteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void cambiaPassword(Long utenteId, String vecchiaPassword, String nuovaPassword) {
        Utente utente = utenteRepository.findById(utenteId)
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato: " + utenteId));

        if (!passwordEncoder.matches(vecchiaPassword, utente.getPasswordHash())) {
            throw new BadCredentialsException("Vecchia password non corretta");
        }

        utente.cambiaPassword(passwordEncoder.encode(nuovaPassword));
    }

    @Transactional(readOnly = true)
    public List<Utente> elencaUtenti() {
        return utenteRepository.findAll();
    }

    @Transactional
    public Utente creaUtente(String username, String password, RuoloUtente ruolo) {
        if (utenteRepository.findByUsername(username).isPresent()) {
            throw new IllegalStateException("Username gia' in uso: " + username);
        }
        return utenteRepository.save(new Utente(username, passwordEncoder.encode(password), ruolo));
    }

    @Transactional
    public void disattivaUtente(Long utenteId) {
        Utente utente = utenteRepository.findById(utenteId)
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato: " + utenteId));
        utente.disattiva();
    }

    /**
     * Elimina definitivamente un utente disattivato. Rifiuta un utente
     * ancora attivo (va prima disattivato: la disattivazione resta il modo
     * normale per revocare l'accesso). Fallisce anche per violazione del
     * vincolo di integrita' (409, gestito da ApiExceptionHandler) se
     * l'utente ha gia' aperto sessioni o comande: cameriere_id vi fa
     * riferimento senza ON DELETE, coerente con lo storico append-only
     * (vedi docs/02-domain-model.md) — un utente con attivita' pregressa
     * resta quindi solo disattivabile, non eliminabile.
     */
    @Transactional
    public void eliminaUtente(Long utenteId) {
        Utente utente = utenteRepository.findById(utenteId)
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato: " + utenteId));
        if (utente.isAttivo()) {
            throw new IllegalStateException("Utente " + utenteId + " e' attivo: disattivalo prima di eliminarlo");
        }
        utenteRepository.delete(utente);
        utenteRepository.flush();
    }
}
