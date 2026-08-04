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
 * un'intera voce di menu o aggiornamento della nota di una riga esistente,
 * entrambe permesse solo se il gruppo non e' ancora in preparazione (vedi
 * GruppoInvio.puoModificare) — una nota comunicata dopo che la cucina ha
 * gia' iniziato a lavorare la portata non la raggiungerebbe in tempo utile.
 * Distinta da ComandaSyncService, che riguarda solo l'assemblaggio iniziale
 * della comanda al sync.
 *
 * Ogni mutazione emette un evento outbox (stesso aggregate_type "GRUPPO_INVIO"
 * usato da CoursingService per fire/pronto/servito): senza, il tablet cucina
 * vedrebbe una nota o una voce aggiunta solo al prossimo refresh manuale di
 * /api/kds/coda, non in tempo reale sul feed WebSocket a cui e' gia'
 * abbonato. KdsPage.applicaEvento sa gia' applicare un evento.righe
 * aggiornato a parita' di stato, quindi non serve alcuna modifica lato
 * frontend per il rendering.
 */
@Service
public class ComandaModificaService {

    private static final String AGGREGATE_TYPE_GRUPPO_INVIO = "GRUPPO_INVIO";
    private static final String EVENTO_RIGHE_AGGIORNATE = "GRUPPO_RIGHE_AGGIORNATE";

    private final GruppoInvioRepository gruppoInvioRepository;
    private final RigaOrdineRepository rigaOrdineRepository;
    private final MenuItemRepository menuItemRepository;
    private final OutboxEventWriter outboxEventWriter;

    public ComandaModificaService(GruppoInvioRepository gruppoInvioRepository,
                                   RigaOrdineRepository rigaOrdineRepository,
                                   MenuItemRepository menuItemRepository,
                                   OutboxEventWriter outboxEventWriter) {
        this.gruppoInvioRepository = gruppoInvioRepository;
        this.rigaOrdineRepository = rigaOrdineRepository;
        this.menuItemRepository = menuItemRepository;
        this.outboxEventWriter = outboxEventWriter;
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
        emettiEvento(gruppo);
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
        emettiEvento(gruppo);
        return gruppo;
    }

    @Transactional
    public void aggiornaNoteRiga(Long rigaId, String note) {
        RigaOrdine riga = rigaOrdineRepository.findById(rigaId)
                .orElseThrow(() -> new IllegalArgumentException("Riga non trovata: " + rigaId));
        riga.aggiornaNote(note);
        emettiEvento(riga.getGruppoInvio());
    }

    private void emettiEvento(GruppoInvio gruppo) {
        outboxEventWriter.registraEvento(
                AGGREGATE_TYPE_GRUPPO_INVIO,
                String.valueOf(gruppo.getId()),
                EVENTO_RIGHE_AGGIORNATE,
                GruppoInvioEventPayload.of(gruppo));
    }

    private GruppoInvio trovaGruppo(Long gruppoId) {
        return gruppoInvioRepository.findById(gruppoId)
                .orElseThrow(() -> new IllegalArgumentException("Gruppo di invio non trovato: " + gruppoId));
    }
}
