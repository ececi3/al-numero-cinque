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
 * La coda cucina deve esporre i piatti effettivi di ogni gruppo (nome,
 * quantita', note), non solo l'identificativo di comanda/portata: e' cio'
 * che il cuoco legge per sapere cosa preparare.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KdsControllerIntegrationTest {

    private static final String PASSWORD = "password-kds-test-123";

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
    private Long menuItemId;

    @BeforeEach
    void setUp() {
        tavoloId = tavoloRepository.save(new Tavolo("T9")).getId();
        menuItemId = menuItemRepository.save(
                new MenuItem("Cotoletta alla milanese", "secondo", new BigDecimal("14.00"), "secondi", true)).getId();

        utenteRepository.save(new Utente("cameriere.kds", passwordEncoder.encode(PASSWORD), RuoloUtente.CAMERIERE));
        utenteRepository.save(new Utente("cucina.kds", passwordEncoder.encode(PASSWORD), RuoloUtente.CUCINA));
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
    void codaCucina_espongeIPiattiDiOgniGruppo() throws Exception {
        String tokenCameriere = login("cameriere.kds");
        String tokenCucina = login("cucina.kds");

        UUID sessioneId = UUID.randomUUID();
        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", sessioneId, "tavoloId", tavoloId, "numeroCoperti", 2))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/comande")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(),
                                "sessioneId", sessioneId,
                                "gruppi", new Object[]{
                                        Map.of(
                                                "numeroPortata", 1,
                                                "righe", new Object[]{
                                                        Map.of("menuItemId", menuItemId, "quantita", 2, "note", "cottura media")
                                                }
                                        )
                                }))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/kds/coda").header("Authorization", "Bearer " + tokenCucina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].righe[0].nome").value("Cotoletta alla milanese"))
                .andExpect(jsonPath("$[0].righe[0].quantita").value(2))
                .andExpect(jsonPath("$[0].righe[0].note").value("cottura media"));
    }
}
