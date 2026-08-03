package com.alnumerocinque.web;

import com.alnumerocinque.domain.StatoGruppo;
import com.alnumerocinque.repository.GruppoInvioRepository;
import com.alnumerocinque.service.CoursingService;
import com.alnumerocinque.web.dto.ComandaDettaglioResponse;
import com.alnumerocinque.web.dto.GruppoInvioResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoint per il tablet cucina (KDS). Il tablet e' per design sempre
 * online (vedi docs/06-offline-sync.md): tutte le transizioni qui esposte
 * sono operazioni online-only.
 */
@RestController
@RequestMapping("/api/kds")
public class KdsController {

    private final CoursingService coursingService;
    private final GruppoInvioRepository gruppoInvioRepository;

    public KdsController(CoursingService coursingService, GruppoInvioRepository gruppoInvioRepository) {
        this.coursingService = coursingService;
        this.gruppoInvioRepository = gruppoInvioRepository;
    }

    /** Coda cucina corrente, ordinata per ordine di ingresso (seq_coda). */
    @GetMapping("/coda")
    public List<GruppoInvioResponse> coda() {
        return gruppoInvioRepository
                .findByStatoInOrderBySeqCodaAsc(List.of(StatoGruppo.IN_CODA, StatoGruppo.IN_PREP, StatoGruppo.PRONTO))
                .stream()
                .map(GruppoInvioResponse::of)
                .toList();
    }

    /**
     * Dettaglio completo di una comanda (tutte le portate con stato, tavolo,
     * cameriere, orario): la coda mostra una card per portata, spesso in
     * colonne diverse, senza modo di vedere insieme quelle della stessa
     * comanda.
     */
    @GetMapping("/comande/{id}")
    public ComandaDettaglioResponse dettaglioComanda(@PathVariable UUID id) {
        return coursingService.dettaglioComanda(id);
    }

    @PostMapping("/gruppi/{id}/inizia-preparazione")
    public GruppoInvioResponse iniziaPreparazione(@PathVariable Long id) {
        return GruppoInvioResponse.of(coursingService.iniziaPreparazione(id));
    }

    /** Segna il gruppo come pronto; spara automaticamente il successivo trattenuto della stessa comanda. */
    @PostMapping("/gruppi/{id}/pronto")
    public GruppoInvioResponse pronto(@PathVariable Long id) {
        return GruppoInvioResponse.of(coursingService.segnaPronto(id));
    }

    @PostMapping("/gruppi/{id}/servito")
    public GruppoInvioResponse servito(@PathVariable Long id) {
        return GruppoInvioResponse.of(coursingService.segnaServito(id));
    }
}
