package com.alnumerocinque.web;

import com.alnumerocinque.domain.*;
import com.alnumerocinque.repository.MenuItemRepository;
import com.alnumerocinque.repository.SessioneRepository;
import com.alnumerocinque.repository.TavoloRepository;
import com.alnumerocinque.repository.UtenteRepository;
import com.alnumerocinque.service.CoursingService;
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
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminAnalyticsControllerIntegrationTest {

    private static final String PASSWORD = "password-analytics-123";

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

    @Autowired
    private SessioneRepository sessioneRepository;

    @Autowired
    private CoursingService coursingService;

    private Long cameriereId;
    private Tavolo tavolo;
    private MenuItem menuItem;

    @BeforeEach
    void setUp() {
        utenteRepository.save(new Utente("admin.analytics", passwordEncoder.encode(PASSWORD), RuoloUtente.ADMIN));
        Utente cameriere = utenteRepository.save(
                new Utente("cameriere.analytics", passwordEncoder.encode(PASSWORD), RuoloUtente.CAMERIERE));
        cameriereId = cameriere.getId();

        tavolo = tavoloRepository.save(new Tavolo("A1"));
        menuItem = menuItemRepository.save(new MenuItem("Risotto", "primo", new BigDecimal("10.00"), "primi", true));
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
    void analytics_calcolaCopertiComandeETempiMediPreparazione() throws Exception {
        Sessione sessione = new Sessione(UUID.randomUUID(), tavolo, cameriereId, 4, OffsetDateTime.now());
        sessioneRepository.save(sessione);

        Comanda comanda = new Comanda(UUID.randomUUID(), sessione, cameriereId, OffsetDateTime.now());
        GruppoInvio gruppo = comanda.aggiungiGruppo(1);
        gruppo.aggiungiRiga(menuItem, 1, menuItem.getPrezzo(), null);

        Comanda registrata = coursingService.registraComanda(comanda);
        Long gruppoId = registrata.getGruppi().get(0).getId();
        coursingService.iniziaPreparazione(gruppoId);
        coursingService.segnaPronto(gruppoId);

        String tokenAdmin = login("admin.analytics");

        mockMvc.perform(get("/api/admin/analytics").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroSessioni").value(1))
                .andExpect(jsonPath("$.totaleCoperti").value(4))
                .andExpect(jsonPath("$.numeroComande").value(1))
                .andExpect(jsonPath("$.tempiMediPreparazionePerPortata[0].numeroPortata").value(1))
                .andExpect(jsonPath("$.tempiMediPreparazionePerPortata[0].campioni").value(1));
    }

    @Test
    void analytics_comeCameriere_restituisce403() throws Exception {
        String token = login("cameriere.analytics");

        mockMvc.perform(get("/api/admin/analytics").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
