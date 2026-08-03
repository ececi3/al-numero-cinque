package com.alnumerocinque.web;

import com.alnumerocinque.domain.RuoloUtente;
import com.alnumerocinque.domain.Tavolo;
import com.alnumerocinque.domain.Utente;
import com.alnumerocinque.repository.TavoloRepository;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Amministrazione utenti: creazione/disattivazione riservata al ruolo ADMIN,
 * negata agli altri ruoli.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUtenteControllerIntegrationTest {

    private static final String PASSWORD = "password-admin-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UtenteRepository utenteRepository;

    @Autowired
    private TavoloRepository tavoloRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        utenteRepository.save(new Utente("admin.test", passwordEncoder.encode(PASSWORD), RuoloUtente.ADMIN));
        utenteRepository.save(new Utente("cameriere.noadmin", passwordEncoder.encode(PASSWORD), RuoloUtente.CAMERIERE));
    }

    private String login(String username) throws Exception {
        String risposta = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("token").asText();
    }

    @Test
    void creaUtente_comeAdmin_percorsoFelice() throws Exception {
        String tokenAdmin = login("admin.test");

        mockMvc.perform(post("/api/admin/utenti")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "cucina.nuova",
                                "password", "password-cucina-123",
                                "ruolo", "CUCINA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cucina.nuova"))
                .andExpect(jsonPath("$.ruolo").value("CUCINA"))
                .andExpect(jsonPath("$.attivo").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "cucina.nuova", "password", "password-cucina-123"))))
                .andExpect(status().isOk());
    }

    @Test
    void creaUtente_conUsernameGiaInUso_restituisce409() throws Exception {
        String tokenAdmin = login("admin.test");

        mockMvc.perform(post("/api/admin/utenti")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin.test",
                                "password", "password-qualsiasi-123",
                                "ruolo", "CAMERIERE"))))
                .andExpect(status().isConflict());
    }

    @Test
    void creaUtente_comeCameriere_restituisce403() throws Exception {
        String tokenCameriere = login("cameriere.noadmin");

        mockMvc.perform(post("/api/admin/utenti")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "altro.utente",
                                "password", "password-qualsiasi-123",
                                "ruolo", "CAMERIERE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void disattivaUtente_impediscePoiIlLogin() throws Exception {
        String tokenAdmin = login("admin.test");
        Utente daDisattivare = utenteRepository.save(
                new Utente("da.disattivare", passwordEncoder.encode(PASSWORD), RuoloUtente.CAMERIERE));

        mockMvc.perform(post("/api/admin/utenti/" + daDisattivare.getId() + "/disattiva")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "da.disattivare", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elencaUtenti_comeAdmin_restituisceLista() throws Exception {
        String tokenAdmin = login("admin.test");

        mockMvc.perform(get("/api/admin/utenti").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'admin.test')]").exists());
    }

    @Test
    void eliminaUtente_disattivatoESenzaStorico_loRimuoveDefinitivamente() throws Exception {
        String tokenAdmin = login("admin.test");
        Utente daEliminare = utenteRepository.save(
                new Utente("da.eliminare", passwordEncoder.encode(PASSWORD), RuoloUtente.CAMERIERE));

        mockMvc.perform(post("/api/admin/utenti/" + daEliminare.getId() + "/disattiva")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/utenti/" + daEliminare.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/utenti").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'da.eliminare')]").doesNotExist());
    }

    @Test
    void eliminaUtente_ancoraAttivo_restituisce409() throws Exception {
        String tokenAdmin = login("admin.test");
        Utente attivo = utenteRepository.save(
                new Utente("ancora.attivo", passwordEncoder.encode(PASSWORD), RuoloUtente.CAMERIERE));

        mockMvc.perform(delete("/api/admin/utenti/" + attivo.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminaUtente_conStoricoSessioni_restituisce409() throws Exception {
        String tokenAdmin = login("admin.test");
        Tavolo tavolo = tavoloRepository.save(new Tavolo("UT1"));
        String tokenCameriereConStorico = login("cameriere.noadmin");

        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + tokenCameriereConStorico)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(), "tavoloId", tavolo.getId(), "numeroCoperti", 2))))
                .andExpect(status().isOk());

        Long cameriereId = utenteRepository.findByUsername("cameriere.noadmin").orElseThrow().getId();
        mockMvc.perform(post("/api/admin/utenti/" + cameriereId + "/disattiva")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/utenti/" + cameriereId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict());
    }
}
