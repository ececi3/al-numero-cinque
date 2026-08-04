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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * POST /api/sessioni/nuovo-tavolo: il cameriere apre una sessione dando solo
 * il numero del tavolo, che viene creato al volo se non esiste ancora (vedi
 * SessioneService.apriSessioneNuovoTavolo). Sostituisce la creazione tavoli
 * lato admin, rimossa (vedi TavoloMenuControllerIntegrationTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessioneNuovoTavoloControllerIntegrationTest {

    private static final String PASSWORD = "password-nuovotav-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TavoloRepository tavoloRepository;

    @Autowired
    private UtenteRepository utenteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        utenteRepository.save(new Utente("cameriere.nuovotav", passwordEncoder.encode(PASSWORD), RuoloUtente.CAMERIERE));
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
    void apriNuovoTavolo_conNumeroInesistente_creaIlTavoloEApreLaSessione() throws Exception {
        String tokenCameriere = login("cameriere.nuovotav");
        UUID sessioneId = UUID.randomUUID();

        mockMvc.perform(post("/api/sessioni/nuovo-tavolo")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", sessioneId, "numeroTavolo", "N1", "numeroCoperti", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessioneId.toString()))
                .andExpect(jsonPath("$.numeroCoperti").value(3))
                .andExpect(jsonPath("$.stato").value("APERTA"));

        Tavolo tavolo = tavoloRepository.findByNumero("N1").orElseThrow();
        assertThat(tavolo.getStato().name()).isEqualTo("OCCUPATO");
    }

    @Test
    void apriNuovoTavolo_conNumeroDiUnTavoloLibero_riusaIlTavoloEsistente() throws Exception {
        Long tavoloEsistenteId = tavoloRepository.save(new Tavolo("N2")).getId();
        String tokenCameriere = login("cameriere.nuovotav");

        mockMvc.perform(post("/api/sessioni/nuovo-tavolo")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(), "numeroTavolo", "N2", "numeroCoperti", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tavoloId").value(tavoloEsistenteId));

        // Non deve essere stato creato un secondo tavolo con lo stesso numero.
        assertThat(tavoloRepository.findAll())
                .filteredOn(t -> t.getNumero().equals("N2"))
                .hasSize(1);
    }

    @Test
    void apriNuovoTavolo_conNumeroDiUnTavoloGiaOccupato_restituisce409() throws Exception {
        Tavolo tavolo = tavoloRepository.save(new Tavolo("N3"));
        tavolo.occupa();
        tavoloRepository.save(tavolo);
        String tokenCameriere = login("cameriere.nuovotav");

        mockMvc.perform(post("/api/sessioni/nuovo-tavolo")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(), "numeroTavolo", "N3", "numeroCoperti", 2))))
                .andExpect(status().isConflict());
    }

    @Test
    void apriNuovoTavolo_retryConStessoId_eIdempotenteNonDuplicaIlTavolo() throws Exception {
        String tokenCameriere = login("cameriere.nuovotav");
        UUID sessioneId = UUID.randomUUID();
        Map<String, Object> body = Map.of("id", sessioneId, "numeroTavolo", "N4", "numeroCoperti", 4);

        mockMvc.perform(post("/api/sessioni/nuovo-tavolo")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // Stesso id sessione: idempotente, non deve tentare di ri-occupare il tavolo (che darebbe 409).
        mockMvc.perform(post("/api/sessioni/nuovo-tavolo")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessioneId.toString()));

        assertThat(tavoloRepository.findAll())
                .filteredOn(t -> t.getNumero().equals("N4"))
                .hasSize(1);
    }

    @Test
    void apriNuovoTavolo_comeCucina_restituisce403() throws Exception {
        utenteRepository.save(new Utente("cucina.nuovotav", passwordEncoder.encode(PASSWORD), RuoloUtente.CUCINA));
        String tokenCucina = login("cucina.nuovotav");

        mockMvc.perform(post("/api/sessioni/nuovo-tavolo")
                        .header("Authorization", "Bearer " + tokenCucina)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(), "numeroTavolo", "N5", "numeroCoperti", 2))))
                .andExpect(status().isForbidden());
    }
}
