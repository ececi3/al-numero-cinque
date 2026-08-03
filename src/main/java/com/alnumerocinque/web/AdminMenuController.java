package com.alnumerocinque.web;

import com.alnumerocinque.domain.Categoria;
import com.alnumerocinque.domain.MenuItem;
import com.alnumerocinque.repository.CategoriaRepository;
import com.alnumerocinque.repository.MenuItemRepository;
import com.alnumerocinque.web.dto.CreaMenuItemRequest;
import com.alnumerocinque.web.dto.MenuItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Gestione menu, riservata al ruolo ADMIN (vedi SecurityConfig).
 * L'eliminazione di una voce gia' ordinata fallisce per violazione del
 * vincolo di integrita' (riga_ordine.menu_item_id REFERENCES menu_item(id)
 * senza ON DELETE), gestita da ApiExceptionHandler come 409: le righe
 * d'ordine sono append-only per storico/analytics, quindi una voce con
 * ordini associati va disattivata (vedi disattiva()), non eliminata.
 */
@RestController
@RequestMapping("/api/admin/menu")
public class AdminMenuController {

    private final MenuItemRepository menuItemRepository;
    private final CategoriaRepository categoriaRepository;

    public AdminMenuController(MenuItemRepository menuItemRepository, CategoriaRepository categoriaRepository) {
        this.menuItemRepository = menuItemRepository;
        this.categoriaRepository = categoriaRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse crea(@Valid @RequestBody CreaMenuItemRequest request) {
        Categoria categoria = request.categoriaId() != null
                ? categoriaRepository.findById(request.categoriaId())
                        .orElseThrow(() -> new IllegalArgumentException("Categoria non trovata: " + request.categoriaId()))
                : null;
        MenuItem menuItem = new MenuItem(
                request.nome(), request.descrizione(), request.prezzo(), categoria, request.inviaInCucina());
        return MenuItemResponse.of(menuItemRepository.save(menuItem));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void elimina(@PathVariable Long id) {
        MenuItem menuItem = trova(id);
        menuItemRepository.delete(menuItem);
        // Flush esplicito: forza l'esecuzione immediata del DELETE cosi' una
        // violazione del vincolo di integrita' (voce gia' ordinata) emerge
        // come eccezione sincrona in questa richiesta, invece di restare
        // pendente fino al commit della transazione.
        menuItemRepository.flush();
    }

    @PostMapping("/{id}/attiva")
    @ResponseStatus(HttpStatus.OK)
    public MenuItemResponse attiva(@PathVariable Long id) {
        MenuItem menuItem = trova(id);
        menuItem.rendiDisponibile();
        return MenuItemResponse.of(menuItemRepository.save(menuItem));
    }

    @PostMapping("/{id}/disattiva")
    @ResponseStatus(HttpStatus.OK)
    public MenuItemResponse disattiva(@PathVariable Long id) {
        MenuItem menuItem = trova(id);
        menuItem.rendiNonDisponibile();
        return MenuItemResponse.of(menuItemRepository.save(menuItem));
    }

    private MenuItem trova(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Voce di menu non trovata: " + id));
    }
}
