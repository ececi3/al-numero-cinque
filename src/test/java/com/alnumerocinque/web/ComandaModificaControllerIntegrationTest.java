package com.alnumerocinque.web;

import com.alnumerocinque.domain.MenuItem;
import com.alnumerocinque.domain.OutboxEvent;
import com.alnumerocinque.domain.RuoloUtente;
import com.alnumerocinque.domain.Tavolo;
import com.alnumerocinque.domain.Utente;
import com.alnumerocinque.repository.MenuItemRepository;
import com.alnumerocinque.repository.OutboxEventRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Modifica di una comanda gia' inviata: aggiunta/rimozione di una voce e
 * aggiornamento nota, entrambe permesse solo se il gruppo non e' ancora in
 * preparazione. Vedi ComandaModificaService per i vincoli di stato e per
 * l'emissione degli eventi outbox che tengono il KDS aggiornato in tempo
 * reale su queste modifiche.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ComandaModificaControllerIntegrationTest {

    private static final String PASSWORD = "password-modifica-123";

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
    private OutboxEventRepository outboxEventRepository;

    private Long tavoloId;
    private Long menuItemId;
    private Long secondoMenuItemId;

    @BeforeEach
    void setUp() {
        tavoloId = tavoloRepository.save(new Tavolo("M1")).getId();
        menuItemId = menuItemRepository.save(
                new MenuItem("Tagliere misto", "antipasto", new BigDecimal("9.00"), null, true)).getId();
        secondoMenuItemId = menuItemRepository.save(
                new MenuItem("Grissini", "antipasto", new BigDecimal("2.00"), null, false)).getId();

        utenteRepository.save(new Utente("cameriere.modifica", passwordEncoder.encode(PASSWORD), RuoloUtente.CAMERIERE));
        utenteRepository.save(new Utente("cucina.modifica", passwordEncoder.encode(PASSWORD), RuoloUtente.CUCINA));
    }

    private String login(String username) throws Exception {
        String risposta = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("token").asText();
    }

    /** Apre una sessione e invia una comanda con due portate (1 IN_CODA per fire-on-ready, 2 TRATTENUTO); ritorna gli id dei due gruppi. */
    private long[] apriSessioneEInviaComandaConDuePortate(String tokenCameriere) throws Exception {
        UUID sessioneId = UUID.randomUUID();
        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", sessioneId, "tavoloId", tavoloId, "numeroCoperti", 2))))
                .andExpect(status().isOk());

        String risposta = mockMvc.perform(post("/api/comande")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(),
                                "sessioneId", sessioneId,
                                "gruppi", new Object[]{
                                        Map.of("numeroPortata", 1, "righe", new Object[]{
                                                Map.of("menuItemId", menuItemId, "quantita", 1)
                                        }),
                                        Map.of("numeroPortata", 2, "righe", new Object[]{
                                                Map.of("menuItemId", menuItemId, "quantita", 1)
                                        })
                                }))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var gruppi = objectMapper.readTree(risposta).get("gruppi");
        return new long[]{gruppi.get(0).get("id").asLong(), gruppi.get(1).get("id").asLong()};
    }

    @Test
    void aggiungiRiga_suGruppoInCoda_percorsoFelice() throws Exception {
        String tokenCameriere = login("cameriere.modifica");
        long gruppoInCodaId = apriSessioneEInviaComandaConDuePortate(tokenCameriere)[0];

        mockMvc.perform(post("/api/comande/gruppi/" + gruppoInCodaId + "/righe")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "menuItemId", secondoMenuItemId, "quantita", 2, "note", "extra"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.righe.length()").value(2))
                .andExpect(jsonPath("$.righe[1].nome").value("Grissini"))
                .andExpect(jsonPath("$.righe[1].quantita").value(2))
                .andExpect(jsonPath("$.righe[1].note").value("extra"));
    }

    @Test
    void aggiungiRiga_suGruppoTrattenuto_percorsoFelice() throws Exception {
        String tokenCameriere = login("cameriere.modifica");
        long gruppoTrattenutoId = apriSessioneEInviaComandaConDuePortate(tokenCameriere)[1];

        mockMvc.perform(post("/api/comande/gruppi/" + gruppoTrattenutoId + "/righe")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "menuItemId", secondoMenuItemId, "quantita", 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.righe.length()").value(2));
    }

    @Test
    void aggiungiRiga_suGruppoInPreparazione_restituisce409() throws Exception {
        String tokenCameriere = login("cameriere.modifica");
        String tokenCucina = login("cucina.modifica");
        long gruppoInCodaId = apriSessioneEInviaComandaConDuePortate(tokenCameriere)[0];

        mockMvc.perform(post("/api/kds/gruppi/" + gruppoInCodaId + "/inizia-preparazione")
                        .header("Authorization", "Bearer " + tokenCucina))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/comande/gruppi/" + gruppoInCodaId + "/righe")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "menuItemId", secondoMenuItemId, "quantita", 1))))
                .andExpect(status().isConflict());
    }

    @Test
    void aggiungiRiga_comeCucina_restituisce403() throws Exception {
        String tokenCameriere = login("cameriere.modifica");
        String tokenCucina = login("cucina.modifica");
        long gruppoInCodaId = apriSessioneEInviaComandaConDuePortate(tokenCameriere)[0];

        mockMvc.perform(post("/api/comande/gruppi/" + gruppoInCodaId + "/righe")
                        .header("Authorization", "Bearer " + tokenCucina)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "menuItemId", secondoMenuItemId, "quantita", 1))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rimuoviRiga_suGruppoInCoda_percorsoFelice() throws Exception {
        String tokenCameriere = login("cameriere.modifica");
        long gruppoInCodaId = apriSessioneEInviaComandaConDuePortate(tokenCameriere)[0];

        String rispostaAggiunta = mockMvc.perform(post("/api/comande/gruppi/" + gruppoInCodaId + "/righe")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "menuItemId", secondoMenuItemId, "quantita", 1))))
                .andReturn().getResponse().getContentAsString();
        long secondaRigaId = objectMapper.readTree(rispostaAggiunta).get("righe").get(1).get("id").asLong();

        mockMvc.perform(delete("/api/comande/gruppi/" + gruppoInCodaId + "/righe/" + secondaRigaId)
                        .header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.righe.length()").value(1));
    }

    @Test
    void rimuoviRiga_suGruppoInPreparazione_restituisce409() throws Exception {
        String tokenCameriere = login("cameriere.modifica");
        String tokenCucina = login("cucina.modifica");
        long gruppoInCodaId = apriSessioneEInviaComandaConDuePortate(tokenCameriere)[0];

        String rispostaComanda = mockMvc.perform(post("/api/kds/gruppi/" + gruppoInCodaId + "/inizia-preparazione")
                        .header("Authorization", "Bearer " + tokenCucina))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long rigaId = objectMapper.readTree(rispostaComanda).get("righe").get(0).get("id").asLong();

        mockMvc.perform(delete("/api/comande/gruppi/" + gruppoInCodaId + "/righe/" + rigaId)
                        .header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isConflict());
    }

    @Test
    void aggiornaNote_suGruppoInCoda_percorsoFelice() throws Exception {
        String tokenCameriere = login("cameriere.modifica");
        UUID sessioneId = UUID.randomUUID();
        mockMvc.perform(post("/api/sessioni")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", sessioneId, "tavoloId", tavoloId, "numeroCoperti", 2))))
                .andExpect(status().isOk());

        String rispostaComanda = mockMvc.perform(post("/api/comande")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", UUID.randomUUID(),
                                "sessioneId", sessioneId,
                                "gruppi", new Object[]{
                                        Map.of("numeroPortata", 1, "righe", new Object[]{
                                                Map.of("menuItemId", menuItemId, "quantita", 1)
                                        })
                                }))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long rigaId = objectMapper.readTree(rispostaComanda).get("gruppi").get(0).get("righe").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/comande/righe/" + rigaId + "/note")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("note", "senza aglio"))))
                .andExpect(status().isOk());
    }

    @Test
    void aggiornaNote_suGruppoInPreparazione_restituisce409() throws Exception {
        String tokenCameriere = login("cameriere.modifica");
        String tokenCucina = login("cucina.modifica");
        long gruppoId = apriSessioneEInviaComandaConDuePortate(tokenCameriere)[0];

        String rispostaComanda = mockMvc.perform(post("/api/kds/gruppi/" + gruppoId + "/inizia-preparazione")
                        .header("Authorization", "Bearer " + tokenCucina))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long rigaId = objectMapper.readTree(rispostaComanda).get("righe").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/comande/righe/" + rigaId + "/note")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("note", "troppo tardi"))))
                .andExpect(status().isConflict());
    }

    /**
     * Senza un evento outbox il tablet cucina vedrebbe una voce aggiunta o
     * una nota cambiata solo al prossimo refresh manuale, non sul feed
     * WebSocket a cui e' gia' abbonato (vedi ComandaModificaService).
     */
    @Test
    void ogniModificaComanda_scriveUnEventoOutboxPerIlKds() throws Exception {
        String tokenCameriere = login("cameriere.modifica");
        long gruppoInCodaId = apriSessioneEInviaComandaConDuePortate(tokenCameriere)[0];

        String rispostaAggiunta = mockMvc.perform(post("/api/comande/gruppi/" + gruppoInCodaId + "/righe")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "menuItemId", secondoMenuItemId, "quantita", 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long rigaId = objectMapper.readTree(rispostaAggiunta).get("righe").get(1).get("id").asLong();

        mockMvc.perform(patch("/api/comande/righe/" + rigaId + "/note")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("note", "ben cotto"))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/comande/gruppi/" + gruppoInCodaId + "/righe/" + rigaId)
                        .header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isOk());

        List<OutboxEvent> eventi = outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc();
        assertThat(eventi)
                .filteredOn(e -> e.getEventType().equals("GRUPPO_RIGHE_AGGIORNATE"))
                .hasSize(3);
    }
}
