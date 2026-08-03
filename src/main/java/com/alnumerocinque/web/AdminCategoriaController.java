package com.alnumerocinque.web;

import com.alnumerocinque.domain.Categoria;
import com.alnumerocinque.repository.CategoriaRepository;
import com.alnumerocinque.web.dto.CategoriaResponse;
import com.alnumerocinque.web.dto.CreaCategoriaRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Gestione categorie di menu, riservata al ruolo ADMIN (vedi SecurityConfig).
 * L'eliminazione di una categoria ancora collegata a voci di menu fallisce
 * per violazione del vincolo di integrita' (categoria_id REFERENCES
 * categoria(id) senza ON DELETE), gestita da ApiExceptionHandler come 409:
 * bisogna prima scollegare o eliminare le voci di menu di quella categoria.
 */
@RestController
@RequestMapping("/api/admin/categorie")
public class AdminCategoriaController {

    private final CategoriaRepository categoriaRepository;

    public AdminCategoriaController(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse crea(@Valid @RequestBody CreaCategoriaRequest request) {
        return CategoriaResponse.of(categoriaRepository.save(new Categoria(request.nome())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void elimina(@PathVariable Long id) {
        if (!categoriaRepository.existsById(id)) {
            throw new IllegalArgumentException("Categoria non trovata: " + id);
        }
        categoriaRepository.deleteById(id);
        // Vedi nota in AdminMenuController.elimina(): flush esplicito per
        // rendere sincrona la violazione del vincolo (categoria ancora
        // usata da una voce di menu).
        categoriaRepository.flush();
    }
}
