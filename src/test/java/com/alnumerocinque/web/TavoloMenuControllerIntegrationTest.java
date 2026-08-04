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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Lettura tavoli/menu (qualunque ruolo autenticato) e gestione voci menu da
 * admin (creazione, attiva/disattiva). La creazione tavoli non e' piu' una
 * rotta admin (vedi SessioneNuovoTavoloControllerIntegrationTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TavoloMenuControllerIntegrationTest {

    private static final String PASSWORD = "password-tavmenu-123";

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

    @BeforeEach
    void setUp() {
        utenteRepository.save(new Utente("admin.tavmenu", passwordEncoder.encode(PASSWORD), RuoloUtente.ADMIN));
        utenteRepository.save(new Utente("cameriere.tavmenu", passwordEncoder.encode(PASSWORD), RuoloUtente.CAMERIERE));
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
    void elencoTavoli_visibileAlCameriere_includeUnTavoloCreatoInSetup() throws Exception {
        tavoloRepository.save(new Tavolo("T1"));
        String tokenCameriere = login("cameriere.tavmenu");

        mockMvc.perform(get("/api/tavoli").header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numero == 'T1')]").exists());
    }

    /** I tavoli non si creano piu' dall'admin: nascono solo aprendo una sessione (vedi SessioneNuovoTavoloControllerIntegrationTest). */
    @Test
    void postAdminTavoli_rottaRimossa_restituisce404() throws Exception {
        String tokenAdmin = login("admin.tavmenu");

        mockMvc.perform(post("/api/admin/tavoli")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("numero", "T3"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void creaMenuItem_comeAdmin_eDisattivalo_nonPiuNellElencoDefault() throws Exception {
        String tokenAdmin = login("admin.tavmenu");
        String tokenCameriere = login("cameriere.tavmenu");

        String rispostaCategoria = mockMvc.perform(post("/api/admin/categorie")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nome", "dolci"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long categoriaId = objectMapper.readTree(rispostaCategoria).get("id").asLong();

        String risposta = mockMvc.perform(post("/api/admin/menu")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Tiramisu'",
                                "descrizione", "dolce della casa",
                                "prezzo", new BigDecimal("6.00"),
                                "categoriaId", categoriaId,
                                "inviaInCucina", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Tiramisu'"))
                .andExpect(jsonPath("$.categoria").value("dolci"))
                .andExpect(jsonPath("$.disponibile").value(true))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(risposta).get("id").asLong();

        mockMvc.perform(get("/api/menu").header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());

        mockMvc.perform(post("/api/admin/menu/" + id + "/disattiva")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponibile").value(false));

        mockMvc.perform(get("/api/menu").header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());

        mockMvc.perform(get("/api/menu?tutti=true").header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());
    }

    @Test
    void creaMenuItem_comeCameriere_restituisce403() throws Exception {
        String tokenCameriere = login("cameriere.tavmenu");

        mockMvc.perform(post("/api/admin/menu")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Acqua", "prezzo", new BigDecimal("2.00"), "inviaInCucina", false))))
                .andExpect(status().isForbidden());
    }
}
