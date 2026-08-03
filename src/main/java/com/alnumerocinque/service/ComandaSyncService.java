package com.alnumerocinque.service;

import com.alnumerocinque.domain.Comanda;
import com.alnumerocinque.domain.GruppoInvio;
import com.alnumerocinque.domain.MenuItem;
import com.alnumerocinque.domain.Sessione;
import com.alnumerocinque.repository.MenuItemRepository;
import com.alnumerocinque.repository.SessioneRepository;
import com.alnumerocinque.web.dto.SincronizzaComandaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Assembla l'aggregato Comanda/GruppoInvio/RigaOrdine a partire dal payload
 * costruito offline dal dispositivo cameriere, congelando i prezzi
 * server-side (mai fidandosi di un prezzo eventualmente inviato dal
 * client), e delega a CoursingService.registraComanda l'assegnazione di
 * seq_server e il fire del primo gruppo.
 */
@Service
public class ComandaSyncService {

    private final SessioneRepository sessioneRepository;
    private final MenuItemRepository menuItemRepository;
    private final CoursingService coursingService;

    public ComandaSyncService(SessioneRepository sessioneRepository,
                               MenuItemRepository menuItemRepository,
                               CoursingService coursingService) {
        this.sessioneRepository = sessioneRepository;
        this.menuItemRepository = menuItemRepository;
        this.coursingService = coursingService;
    }

    @Transactional
    public Comanda sincronizza(SincronizzaComandaRequest request, Long cameriereId) {
        Sessione sessione = sessioneRepository.findById(request.sessioneId())
                .orElseThrow(() -> new IllegalArgumentException("Sessione non trovata: " + request.sessioneId()));

        if (!sessione.isAperta()) {
            throw new IllegalStateException("Sessione " + sessione.getId() + " non e' aperta");
        }

        Comanda comanda = new Comanda(request.id(), sessione, cameriereId, OffsetDateTime.now());

        for (SincronizzaComandaRequest.GruppoRequest gruppoReq : request.gruppi()) {
            GruppoInvio gruppo = comanda.aggiungiGruppo(gruppoReq.numeroPortata());
            for (SincronizzaComandaRequest.RigaRequest rigaReq : gruppoReq.righe()) {
                MenuItem menuItem = menuItemRepository.findById(rigaReq.menuItemId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Voce di menu non trovata: " + rigaReq.menuItemId()));
                // Prezzo congelato al momento della registrazione, letto dal
                // MenuItem corrente: il client non invia mai un prezzo.
                gruppo.aggiungiRiga(menuItem, rigaReq.quantita(), menuItem.getPrezzo(), rigaReq.note());
            }
        }

        return coursingService.registraComanda(comanda);
    }
}
