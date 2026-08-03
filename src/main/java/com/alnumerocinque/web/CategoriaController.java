package com.alnumerocinque.web;

import com.alnumerocinque.repository.CategoriaRepository;
import com.alnumerocinque.web.dto.CategoriaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lettura categorie, per qualunque utente autenticato (il cameriere deve
 * poter raggruppare il menu per categoria). La gestione (creazione,
 * eliminazione) e' riservata all'admin (vedi AdminCategoriaController).
 */
@RestController
@RequestMapping("/api/categorie")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;

    public CategoriaController(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @GetMapping
    public List<CategoriaResponse> elenca() {
        return categoriaRepository.findAll().stream().map(CategoriaResponse::of).toList();
    }
}
