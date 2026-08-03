package com.alnumerocinque.web;

import com.alnumerocinque.domain.RuoloUtente;
import com.alnumerocinque.domain.Utente;
import com.alnumerocinque.repository.UtenteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login e cambio password: percorso felice, credenziali errate, validazione,
 * accesso senza autenticazione.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    private static final String PASSWORD_INIZIALE = "password-iniziale-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UtenteRepository utenteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        Utente cameriere = new Utente("cameriere.auth", passwordEncoder.encode(PASSWORD_INIZIALE), RuoloUtente.CAMERIERE);
        utenteRepository.save(cameriere);
    }

    private String login(String username, String password) throws Exception {
        String risposta = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("token").asText();
    }

    @Test
    void cambiaPassword_percorsoFelice_vecchiaNonFunzionaPiu() throws Exception {
        String token = login("cameriere.auth", PASSWORD_INIZIALE);

        mockMvc.perform(post("/api/auth/cambia-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vecchiaPassword", PASSWORD_INIZIALE,
                                "nuovaPassword", "password-nuova-456"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "cameriere.auth", "password", PASSWORD_INIZIALE))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "cameriere.auth", "password", "password-nuova-456"))))
                .andExpect(status().isOk());
    }

    @Test
    void cambiaPassword_conVecchiaPasswordErrata_restituisce401() throws Exception {
        String token = login("cameriere.auth", PASSWORD_INIZIALE);

        mockMvc.perform(post("/api/auth/cambia-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vecchiaPassword", "sbagliata",
                                "nuovaPassword", "password-nuova-456"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiaPassword_conNuovaPasswordTroppoCorta_restituisce400() throws Exception {
        String token = login("cameriere.auth", PASSWORD_INIZIALE);

        mockMvc.perform(post("/api/auth/cambia-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vecchiaPassword", PASSWORD_INIZIALE,
                                "nuovaPassword", "corta"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_revocaIlToken_richiesteSuccessiveConLoStessoTokenFallisco() throws Exception {
        String token = login("cameriere.auth", PASSWORD_INIZIALE);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/cambia-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vecchiaPassword", PASSWORD_INIZIALE,
                                "nuovaPassword", "password-nuova-456"))))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isIn(401, 403);
                });
    }

    @Test
    void logout_nonInvalidaUnNuovoLoginSuccessivo() throws Exception {
        String primoToken = login("cameriere.auth", PASSWORD_INIZIALE);
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + primoToken))
                .andExpect(status().isOk());

        String secondoToken = login("cameriere.auth", PASSWORD_INIZIALE);

        mockMvc.perform(post("/api/auth/cambia-password")
                        .header("Authorization", "Bearer " + secondoToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vecchiaPassword", PASSWORD_INIZIALE,
                                "nuovaPassword", "password-nuova-456"))))
                .andExpect(status().isOk());
    }

    @Test
    void cambiaPassword_senzaToken_restituisce401o403() throws Exception {
        mockMvc.perform(post("/api/auth/cambia-password")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vecchiaPassword", PASSWORD_INIZIALE,
                                "nuovaPassword", "password-nuova-456"))))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isIn(401, 403);
                });
    }
}
