package com.alnumerocinque.web;

import com.alnumerocinque.domain.MenuItem;
import com.alnumerocinque.repository.MenuItemRepository;
import com.alnumerocinque.web.dto.CreaMenuItemRequest;
import com.alnumerocinque.web.dto.MenuItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Gestione menu, riservata al ruolo ADMIN (vedi SecurityConfig). */
@RestController
@RequestMapping("/api/admin/menu")
public class AdminMenuController {

    private final MenuItemRepository menuItemRepository;

    public AdminMenuController(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse crea(@Valid @RequestBody CreaMenuItemRequest request) {
        MenuItem menuItem = new MenuItem(
                request.nome(), request.descrizione(), request.prezzo(), request.categoria(), request.inviaInCucina());
        return MenuItemResponse.of(menuItemRepository.save(menuItem));
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
