package com.alnumerocinque.web;

import com.alnumerocinque.repository.MenuItemRepository;
import com.alnumerocinque.web.dto.MenuItemResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lettura menu, per qualunque utente autenticato (il cameriere deve poter
 * scegliere le voci per comporre una comanda). La gestione (creazione,
 * disponibilita') e' riservata all'admin (vedi AdminMenuController).
 */
@RestController
@RequestMapping("/api/menu")
public class MenuItemController {

    private final MenuItemRepository menuItemRepository;

    public MenuItemController(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    /** Di default solo le voci disponibili; {@code tutti=true} per includere anche quelle disattivate. */
    @GetMapping
    public List<MenuItemResponse> elenca(@RequestParam(defaultValue = "false") boolean tutti) {
        var voci = tutti ? menuItemRepository.findAll() : menuItemRepository.findByDisponibileTrue();
        return voci.stream().map(MenuItemResponse::of).toList();
    }
}
