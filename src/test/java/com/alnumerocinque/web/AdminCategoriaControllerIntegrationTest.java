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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Categorie come elenco gestito da admin (creazione/eliminazione) invece di
 * stringa libera su MenuItem, e vera eliminazione delle voci di menu (non
 * solo attiva/disattiva). Entrambe le eliminazioni sono vincolate
 * dall'integrita' referenziale: una categoria ancora usata da una voce, o
 * una voce gia' ordinata, non possono essere eliminate (409), vedi
 * AdminCategoriaController e AdminMenuController.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminCategoriaControllerIntegrationTest {

    private static final String PASSWORD = "password-categoria-123";

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

    @BeforeEach
    void setUp() {
        utenteRepository.save(new Utente("admin.categoria", passwordEncoder.encode(PASSWORD), RuoloUtente.ADMIN));
        utenteRepository.save(new Utente("cameriere.categoria", passwordEncoder.encode(PASSWORD), RuoloUtente.CAMERIERE));
        tavoloId = tavoloRepository.save(new Tavolo("C1")).getId();
    }

    private String login(String username) throws Exception {
        String risposta = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("token").asText();
    }

    private long creaCategoria(String tokenAdmin, String nome) throws Exception {
        String risposta = mockMvc.perform(post("/api/admin/categorie")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nome", nome))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(risposta).get("id").asLong();
    }

    @Test
    void creaCategoria_comeAdmin_eLaRendeVisibileTramiteElenco() throws Exception {
        String tokenAdmin = login("admin.categoria");
        String tokenCameriere = login("cameriere.categoria");
        long id = creaCategoria(tokenAdmin, "Antipasti");

        mockMvc.perform(get("/api/categorie").header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + " && @.nome == 'Antipasti')]").exists());
    }

    @Test
    void creaCategoria_comeCameriere_restituisce403() throws Exception {
        String tokenCameriere = login("cameriere.categoria");

        mockMvc.perform(post("/api/admin/categorie")
                        .header("Authorization", "Bearer " + tokenCameriere)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nome", "Dolci"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminaCategoria_nonUsata_laRimuoveDallElenco() throws Exception {
        String tokenAdmin = login("admin.categoria");
        long id = creaCategoria(tokenAdmin, "Bevande");

        mockMvc.perform(delete("/api/admin/categorie/" + id).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categorie").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());
    }

    @Test
    void eliminaCategoria_ancoraUsataDaUnaVoceDiMenu_restituisce409() throws Exception {
        String tokenAdmin = login("admin.categoria");
        long categoriaId = creaCategoria(tokenAdmin, "Primi");

        mockMvc.perform(post("/api/admin/menu")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Lasagne", "prezzo", new BigDecimal("11.00"),
                                "categoriaId", categoriaId, "inviaInCucina", true))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/categorie/" + categoriaId).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminaVoceDiMenu_maiOrdinata_laRimuoveDefinitivamente() throws Exception {
        String tokenAdmin = login("admin.categoria");
        String tokenCameriere = login("cameriere.categoria");

        String risposta = mockMvc.perform(post("/api/admin/menu")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Panna cotta", "prezzo", new BigDecimal("5.00"), "inviaInCucina", false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(risposta).get("id").asLong();

        mockMvc.perform(delete("/api/admin/menu/" + id).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/menu?tutti=true").header("Authorization", "Bearer " + tokenCameriere))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());
    }

    @Test
    void eliminaVoceDiMenu_giaOrdinata_restituisce409() throws Exception {
        String tokenAdmin = login("admin.categoria");
        String tokenCameriere = login("cameriere.categoria");

        MenuItem menuItem = menuItemRepository.save(
                new MenuItem("Cotoletta", "secondo", new BigDecimal("14.00"), null, true));

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
                                        Map.of("numeroPortata", 1, "righe", new Object[]{
                                                Map.of("menuItemId", menuItem.getId(), "quantita", 1)
                                        })
                                }))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/menu/" + menuItem.getId()).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict());
    }
}
