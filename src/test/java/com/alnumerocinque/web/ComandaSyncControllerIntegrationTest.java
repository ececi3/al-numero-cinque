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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifica il wiring end-to-end: login -> JWT -> controller REST protetto
 * per ruolo -> service di assemblaggio -> CoursingService -> persistenza.
 * Copre il percorso felice di apertura sessione e sincronizzazione di una
 * comanda, oltre ai casi di autenticazione/autorizzazione mancante.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ComandaSyncControllerIntegrationTest {

    private static final String PASSWORD_IN_CHIARO = "password-test-123";

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
    private Long tavoloSecondarioId;
    private Long menuItemId;

    @BeforeEach
    void setUp() {
        Tavolo tavolo = tavoloRepository.save(new Tavolo("T7"));
        tavoloId = tavolo.getId();

        Tavolo tavoloSecondario = tavoloRepository.save(new Tavolo("T8"));
        tavoloSecondarioId = tavoloSecondario.getId();

        MenuItem menuItem = menuItemRepository.save(
                new MenuItem("Bruschette", "antipasto", new BigDecimal("6.50"), null, true));
        menuItemId = menuItem.getId();

        Utente cameriere = new Utente("cameriere.test", passwordEncoder.encode(PASSWORD_IN_CHIARO), RuoloUtente.CAMERIERE);
        utenteRepository.save(cameriere);
    }

    private String ottieniToken(String username) throws Exception {
        String risposta = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", PASSWORD_IN_CHIARO
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(risposta).get("token").asText();
    }

    @Test
    void loginConCredenzialiErrate_restituisce401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "cameriere.test",
                                "password", "sbagliata"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chiamataSenzaToken_restituisce401o403() throws Exception {
        mockMvc.perform(post("/api/sessioni")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(),
                                "tavoloId", tavoloId,
                                "numeroCoperti", 2
                        ))))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isIn(401, 403);
                });
    }

    @Test
    void aprireSessioneESincronizzareComanda_percorsoFelice() throws Exception {
        String token = ottieniToken("cameriere.test");
        UUID sessioneId = UUID.randomUUID();
        UUID comandaId = UUID.randomUUID();

        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", sessioneId,
                                "tavoloId", tavoloId,
                                "numeroCoperti", 3
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessioneId.toString()))
                .andExpect(jsonPath("$.stato").value("APERTA"));

        mockMvc.perform(post("/api/comande")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", comandaId,
                                "sessioneId", sessioneId,
                                "gruppi", new Object[]{
                                        Map.of(
                                                "numeroPortata", 1,
                                                "righe", new Object[]{
                                                        Map.of("menuItemId", menuItemId, "quantita", 2, "note", "senza aglio")
                                                }
                                        )
                                }
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(comandaId.toString()))
                .andExpect(jsonPath("$.seqServer").isNumber())
                .andExpect(jsonPath("$.gruppi[0].stato").value("IN_CODA"))
                .andExpect(jsonPath("$.gruppi[0].seqCoda").isNumber());
    }

    @Test
    void chiudereSessioneConPortateNonServite_restituisce409() throws Exception {
        String token = ottieniToken("cameriere.test");
        UUID sessioneId = UUID.randomUUID();

        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", sessioneId, "tavoloId", tavoloId, "numeroCoperti", 2))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/comande")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(),
                                "sessioneId", sessioneId,
                                "gruppi", new Object[]{
                                        Map.of("numeroPortata", 1, "righe", new Object[]{
                                                Map.of("menuItemId", menuItemId, "quantita", 1)
                                        })
                                }
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessioni/" + sessioneId + "/chiudi")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void chiudereSessioneSenzaPortateAperte_liberaTavolo() throws Exception {
        String token = ottieniToken("cameriere.test");
        UUID sessioneId = UUID.randomUUID();

        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", sessioneId, "tavoloId", tavoloId, "numeroCoperti", 2))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessioni/" + sessioneId + "/chiudi")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("CHIUSA"));

        org.assertj.core.api.Assertions.assertThat(tavoloRepository.findById(tavoloId).orElseThrow().getStato())
                .isEqualTo(com.alnumerocinque.domain.StatoTavolo.LIBERO);
    }

    @Test
    void aggregaTavolo_percorsoFelice_occupaIlTavoloENeIncludeIdNellaRisposta() throws Exception {
        String token = ottieniToken("cameriere.test");
        UUID sessioneId = UUID.randomUUID();

        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", sessioneId, "tavoloId", tavoloId, "numeroCoperti", 2))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessioni/" + sessioneId + "/aggrega-tavolo/" + tavoloSecondarioId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tavoliAggregatiIds[0]").value(tavoloSecondarioId));

        org.assertj.core.api.Assertions.assertThat(tavoloRepository.findById(tavoloSecondarioId).orElseThrow().getStato())
                .isEqualTo(com.alnumerocinque.domain.StatoTavolo.OCCUPATO);
    }

    @Test
    void aggregaTavolo_giaOccupato_restituisce409() throws Exception {
        String token = ottieniToken("cameriere.test");
        UUID sessioneId = UUID.randomUUID();
        UUID altraSessioneId = UUID.randomUUID();

        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", sessioneId, "tavoloId", tavoloId, "numeroCoperti", 2))))
                .andExpect(status().isOk());

        // Il tavolo secondario e' gia' occupato come primario di un'altra sessione.
        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", altraSessioneId, "tavoloId", tavoloSecondarioId, "numeroCoperti", 2))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessioni/" + sessioneId + "/aggrega-tavolo/" + tavoloSecondarioId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void chiudereSessione_liberaAncheITavoliAggregati() throws Exception {
        String token = ottieniToken("cameriere.test");
        UUID sessioneId = UUID.randomUUID();

        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", sessioneId, "tavoloId", tavoloId, "numeroCoperti", 2))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessioni/" + sessioneId + "/aggrega-tavolo/" + tavoloSecondarioId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessioni/" + sessioneId + "/chiudi")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(tavoloRepository.findById(tavoloSecondarioId).orElseThrow().getStato())
                .isEqualTo(com.alnumerocinque.domain.StatoTavolo.LIBERO);
    }

    @Test
    void sincronizzareComandaSuSessioneInesistente_restituisce404() throws Exception {
        String token = ottieniToken("cameriere.test");

        mockMvc.perform(post("/api/comande")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(),
                                "sessioneId", UUID.randomUUID(),
                                "gruppi", new Object[]{
                                        Map.of("numeroPortata", 1, "righe", new Object[]{
                                                Map.of("menuItemId", menuItemId, "quantita", 1)
                                        })
                                }
                        ))))
                .andExpect(status().isNotFound());
    }
}
