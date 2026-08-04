package com.alnumerocinque.service;

import com.alnumerocinque.domain.GruppoInvio;
import com.alnumerocinque.domain.MenuItem;
import com.alnumerocinque.domain.RigaOrdine;
import com.alnumerocinque.repository.GruppoInvioRepository;
import com.alnumerocinque.repository.MenuItemRepository;
import com.alnumerocinque.repository.RigaOrdineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modifica di una comanda gia' inviata in cucina: aggiunta/rimozione di
 * un'intera voce di menu (solo se il gruppo non e' ancora in preparazione,
 * vedi GruppoInvio.puoModificareVoci) o aggiornamento della nota di una
 * riga esistente (sempre permesso, qualunque stato). Distinta da
 * ComandaSyncService, che riguarda solo l'assemblaggio iniziale della
 * comanda al sync.
 */
@Service
public class ComandaModificaService {

    private final GruppoInvioRepository gruppoInvioRepository;
    private final RigaOrdineRepository rigaOrdineRepository;
    private final MenuItemRepository menuItemRepository;

    public ComandaModificaService(GruppoInvioRepository gruppoInvioRepository,
                                   RigaOrdineRepository rigaOrdineRepository,
                                   MenuItemRepository menuItemRepository) {
        this.gruppoInvioRepository = gruppoInvioRepository;
        this.rigaOrdineRepository = rigaOrdineRepository;
        this.menuItemRepository = menuItemRepository;
    }

    @Transactional
    public GruppoInvio aggiungiRiga(Long gruppoId, Long menuItemId, int quantita, String note) {
        GruppoInvio gruppo = trovaGruppo(gruppoId);
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new IllegalArgumentException("Voce di menu non trovata: " + menuItemId));
        // Prezzo congelato al momento dell'aggiunta, letto dal MenuItem
        // corrente: stessa regola del sync iniziale (ComandaSyncService),
        // il client non invia mai un prezzo.
        gruppo.aggiungiRiga(menuItem, quantita, menuItem.getPrezzo(), note);
        // Flush esplicito: la nuova RigaOrdine e' cascade-persistita, quindi
        // senza flush il suo id (IDENTITY, assegnato dal DB) resta nullo nel
        // valore ancora in memoria restituito al chiamante.
        gruppoInvioRepository.flush();
        return gruppo;
    }

    @Transactional
    public GruppoInvio rimuoviRiga(Long gruppoId, Long rigaId) {
        GruppoInvio gruppo = trovaGruppo(gruppoId);
        RigaOrdine riga = gruppo.getRighe().stream()
                .filter(r -> r.getId().equals(rigaId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Riga non trovata nel gruppo " + gruppoId + ": " + rigaId));
        gruppo.rimuoviRiga(riga);
        return gruppo;
    }

    @Transactional
    public void aggiornaNoteRiga(Long rigaId, String note) {
        RigaOrdine riga = rigaOrdineRepository.findById(rigaId)
                .orElseThrow(() -> new IllegalArgumentException("Riga non trovata: " + rigaId));
        riga.aggiornaNote(note);
    }

    private GruppoInvio trovaGruppo(Long gruppoId) {
        return gruppoInvioRepository.findById(gruppoId)
                .orElseThrow(() -> new IllegalArgumentException("Gruppo di invio non trovato: " + gruppoId));
    }
}
