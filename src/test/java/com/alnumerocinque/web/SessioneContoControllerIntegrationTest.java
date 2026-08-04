package com.alnumerocinque.web;

import com.alnumerocinque.domain.MenuItem;
import com.alnumerocinque.domain.RuoloUtente;
import com.alnumerocinque.domain.Tavolo;
import com.alnumerocinque.domain.Utente;
import com.alnumerocinque.repository.MenuItemRepository;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/sessioni/{id}/conto: totale da pagare per una sessione, somma di
 * prezzoCongelato*quantita su tutte le sue RigaOrdine (vedi
 * SessioneService.conto). Consultabile in qualunque momento, non solo a
 * portate tutte SERVITO (a differenza della chiusura sessione).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessioneContoControllerIntegrationTest {

    private static final String PASSWORD = "password-conto-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TavoloRepository tavoloRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private UtenteRepository utenteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long tavoloId;
    private Long tagliereId;
    private Long grissiniId;

    @BeforeEach
    void setUp() {
        tavoloId = tavoloRepository.save(new Tavolo("C1")).getId();
        tagliereId = menuItemRepository.save(
                new MenuItem("Tagliere misto", "antipasto", new BigDecimal("9.00"), null, true)).getId();
        grissiniId = menuItemRepository.save(
                new MenuItem("Grissini", "antipasto", new BigDecimal("2.50"), null, false)).getId();

        utenteRepository.save(new Utente("cameriere.conto", passwordEncoder.encode(PASSWORD), RuoloUtente.CAMERIERE));
        utenteRepository.save(new Utente("cucina.conto", passwordEncoder.encode(PASSWORD), RuoloUtente.CUCINA));
    }

    private String login(String username) throws Exception {
        String risposta = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("token").asText();
    }

    private UUID apriSessione(String tokenCameriere) throws Exception {
        UUID sessioneId = UUID.randomUUID();
        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", sessioneId, "tavoloId", tavoloId, "numeroCoperti", 2))))
                .andExpect(status().isOk());
        return sessioneId;
    }

    @Test
    void conto_conVociRipetuteSuComandeDiverse_aggregaQuantitaEPrezzo() throws Exception {
        String tokenCameriere = login("cameriere.conto");
        UUID sessioneId = apriSessione(tokenCameriere);

        // Prima comanda: 1x Tagliere + 2x Grissini.
        mockMvc.perform(post("/api/comande")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(),
                                "sessioneId", sessioneId,
                                "gruppi", new Object[]{
                                        Map.of("numeroPortata", 1, "righe", new Object[]{
                                                Map.of("menuItemId", tagliereId, "quantita", 1),
                                                Map.of("menuItemId", grissiniId, "quantita", 2)
                                        })
                                }))))
                .andExpect(status().isOk());

        // Seconda comanda, stesso tavolo/sessione: altri 2x Tagliere (stesso prezzo congelato).
        mockMvc.perform(post("/api/comande")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(),
                                "sessioneId", sessioneId,
                                "gruppi", new Object[]{
                                        Map.of("numeroPortata", 1, "righe", new Object[]{
                                                Map.of("menuItemId", tagliereId, "quantita", 2)
                                        })
                                }))))
                .andExpect(status().isOk());

        // Totale atteso: 3x9.00 (Tagliere aggregato tra le due comande) + 2x2.50 (Grissini) = 27.00 + 5.00 = 32.00
        mockMvc.perform(get("/api/sessioni/" + sessioneId + "/conto")
                        .header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessioneId").value(sessioneId.toString()))
                .andExpect(jsonPath("$.tavoloId").value(tavoloId))
                .andExpect(jsonPath("$.voci.length()").value(2))
                .andExpect(jsonPath("$.voci[0].nome").value("Tagliere misto"))
                .andExpect(jsonPath("$.voci[0].quantita").value(3))
                .andExpect(jsonPath("$.voci[0].prezzoUnitario").value(9.00))
                .andExpect(jsonPath("$.voci[0].totaleVoce").value(27.00))
                .andExpect(jsonPath("$.voci[1].nome").value("Grissini"))
                .andExpect(jsonPath("$.voci[1].quantita").value(2))
                .andExpect(jsonPath("$.voci[1].totaleVoce").value(5.00))
                .andExpect(jsonPath("$.totale").value(32.00));
    }

    @Test
    void conto_suSessioneAppenaAperta_totaleZero() throws Exception {
        String tokenCameriere = login("cameriere.conto");
        UUID sessioneId = apriSessione(tokenCameriere);

        mockMvc.perform(get("/api/sessioni/" + sessioneId + "/conto")
                        .header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci.length()").value(0))
                .andExpect(jsonPath("$.totale").value(0));
    }

    @Test
    void conto_nonRichiedeChePortateSianoServite() throws Exception {
        String tokenCameriere = login("cameriere.conto");
        UUID sessioneId = apriSessione(tokenCameriere);

        mockMvc.perform(post("/api/comande")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(),
                                "sessioneId", sessioneId,
                                "gruppi", new Object[]{
                                        Map.of("numeroPortata", 1, "righe", new Object[]{
                                                Map.of("menuItemId", tagliereId, "quantita", 1)
                                        })
                                }))))
                .andExpect(status().isOk());

        // La portata resta IN_CODA (nessuna transizione KDS): il conto deve comunque riflettere l'ordine.
        mockMvc.perform(get("/api/sessioni/" + sessioneId + "/conto")
                        .header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totale").value(9.00));
    }

    @Test
    void conto_comeCucina_restituisce403() throws Exception {
        String tokenCameriere = login("cameriere.conto");
        String tokenCucina = login("cucina.conto");
        UUID sessioneId = apriSessione(tokenCameriere);

        mockMvc.perform(get("/api/sessioni/" + sessioneId + "/conto")
                        .header("Authorization", "Bearer " + tokenCucina))
                .andExpect(status().isForbidden());
    }

    @Test
    void conto_suSessioneInesistente_restituisce404() throws Exception {
        String tokenCameriere = login("cameriere.conto");

        mockMvc.perform(get("/api/sessioni/" + UUID.randomUUID() + "/conto")
                        .header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isNotFound());
    }
}
