package com.alnumerocinque.web;

import com.alnumerocinque.domain.Tavolo;
import com.alnumerocinque.repository.TavoloRepository;
import com.alnumerocinque.web.dto.CreaTavoloRequest;
import com.alnumerocinque.web.dto.TavoloResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Creazione tavoli, riservata al ruolo ADMIN (vedi SecurityConfig). */
@RestController
@RequestMapping("/api/admin/tavoli")
public class AdminTavoloController {

    private final TavoloRepository tavoloRepository;

    public AdminTavoloController(TavoloRepository tavoloRepository) {
        this.tavoloRepository = tavoloRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TavoloResponse crea(@Valid @RequestBody CreaTavoloRequest request) {
        return TavoloResponse.of(tavoloRepository.save(new Tavolo(request.numero())));
    }
}
