package com.alnumerocinque.web;

import com.alnumerocinque.repository.TavoloRepository;
import com.alnumerocinque.web.dto.TavoloResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lettura tavoli, per qualunque utente autenticato (il cameriere deve poter
 * scegliere il tavolo per aprire una sessione). La creazione e' riservata
 * all'admin (vedi AdminTavoloController).
 */
@RestController
@RequestMapping("/api/tavoli")
public class TavoloController {

    private final TavoloRepository tavoloRepository;

    public TavoloController(TavoloRepository tavoloRepository) {
        this.tavoloRepository = tavoloRepository;
    }

    @GetMapping
    public List<TavoloResponse> elenca() {
        return tavoloRepository.findAll().stream().map(TavoloResponse::of).toList();
    }
}
