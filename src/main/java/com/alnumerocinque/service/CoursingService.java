package com.alnumerocinque.service;

import com.alnumerocinque.domain.Comanda;
import com.alnumerocinque.domain.GruppoInvio;
import com.alnumerocinque.domain.Utente;
import com.alnumerocinque.repository.ComandaRepository;
import com.alnumerocinque.repository.GruppoInvioRepository;
import com.alnumerocinque.repository.UtenteRepository;
import com.alnumerocinque.web.dto.ComandaDettaglioResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Motore di coursing: assegna l'ordine assoluto di arrivo delle comande
 * (seq_server) e orchestra il fire-on-ready dei gruppi di invio verso la
 * coda cucina (seq_coda). Ogni transizione di stato scrive anche il
 * corrispondente evento nell'outbox transazionale (stessa transazione),
 * cosi' che il feed KDS via WebSocket e l'analytics possano consumarlo in
 * modo affidabile senza doppia scrittura DB+broker non atomica.
 *
 * Regola cardine: alla registrazione di una comanda viene sparato (fire)
 * SOLO il primo gruppo (numeroPortata piu' basso, ancora TRATTENUTO); gli
 * altri restano TRATTENUTO finche' il gruppo precedente della STESSA
 * comanda non viene segnato PRONTO dalla cucina — a quel punto il gruppo
 * successivo viene sparato automaticamente, in modo sincrono, nella stessa
 * transazione di segnaPronto() (fire-on-ready cucina-driven, non
 * cameriere-driven).
 *
 * Questo produce l'ordinamento assoluto della coda cucina in base
 * all'istante di fire (seq_coda), indipendentemente dall'ordine fisico di
 * sincronizzazione dei dispositivi cameriere quando tornano online.
 */
@Service
public class CoursingService {

    private static final String AGGREGATE_TYPE_GRUPPO_INVIO = "GRUPPO_INVIO";

    private final ComandaRepository comandaRepository;
    private final GruppoInvioRepository gruppoInvioRepository;
    private final UtenteRepository utenteRepository;
    private final SequenzaGenerator sequenzaGenerator;
    private final OutboxEventWriter outboxEventWriter;

    public CoursingService(ComandaRepository comandaRepository,
                            GruppoInvioRepository gruppoInvioRepository,
                            UtenteRepository utenteRepository,
                            SequenzaGenerator sequenzaGenerator,
                            OutboxEventWriter outboxEventWriter) {
        this.comandaRepository = comandaRepository;
        this.gruppoInvioRepository = gruppoInvioRepository;
        this.utenteRepository = utenteRepository;
        this.sequenzaGenerator = sequenzaGenerator;
        this.outboxEventWriter = outboxEventWriter;
    }

    /**
     * Registra una comanda (gia' completa di gruppi/righe costruiti lato
     * client durante la presa comanda offline) assegnandole seq_server e
     * sparando il primo gruppo TRATTENUTO. Idempotente rispetto a re-invii
     * della stessa comanda dopo un ack perso in fase di sync: in tal caso
     * non viene ne' riassegnato seq_server ne' riemesso l'evento di fire.
     *
     * Il salvataggio (con flush) avviene PRIMA del fire, e il fire opera sul
     * valore DI RITORNO del salvataggio, non sull'oggetto transiente
     * originale: Comanda ha id assegnato dal client (UUID), non generato
     * (vedi Comanda), quindi per Spring Data e' "gia' esistente" e save()
     * passa da entityManager.merge() invece che persist() — merge restituisce
     * una NUOVA istanza managed (con id IDENTITY assegnati a gruppi/righe
     * dopo il flush), lasciando l'originale transiente e invariato. Sparare
     * sull'oggetto sbagliato (quello pre-merge) catturerebbe
     * gruppoInvioId: null nell'evento outbox del fire (letto da emettiEvento
     * -> GruppoInvioEventPayload.of), che il feed WebSocket del KDS
     * renderebbe come una card "fantasma" separata da quella reale invece di
     * aggiornarla (il dedup lato frontend e' per id, vedi
     * KdsPage.applicaEvento).
     */
    @Transactional
    public Comanda registraComanda(Comanda comanda) {
        Optional<Comanda> esistente = comandaRepository.findById(comanda.getId());
        if (esistente.isPresent() && esistente.get().isRegistrata()) {
            return esistente.get();
        }

        Comanda daRegistrare = esistente.orElse(comanda);
        daRegistrare.registraSuServer(sequenzaGenerator.prossimoSeqServer());
        daRegistrare = comandaRepository.saveAndFlush(daRegistrare);

        fireProssimoTrattenuto(daRegistrare);

        return daRegistrare;
    }

    /** La cucina inizia la preparazione di un gruppo gia' in coda. */
    @Transactional
    public GruppoInvio iniziaPreparazione(Long gruppoInvioId) {
        GruppoInvio gruppo = trovaGruppo(gruppoInvioId);
        gruppo.iniziaPreparazione();
        emettiEvento(gruppo, "GRUPPO_IN_PREP");
        return gruppo;
    }

    /**
     * La cucina segna un gruppo come pronto. Se esiste un gruppo TRATTENUTO
     * successivo nella stessa comanda, viene sparato automaticamente qui,
     * nella stessa transazione (fire-on-ready).
     */
    @Transactional
    public GruppoInvio segnaPronto(Long gruppoInvioId) {
        GruppoInvio gruppo = trovaGruppo(gruppoInvioId);
        gruppo.segnaPronto();
        emettiEvento(gruppo, "GRUPPO_PRONTO");

        fireProssimoTrattenuto(gruppo.getComanda());

        return gruppo;
    }

    /** Il gruppo viene consegnato al tavolo. */
    @Transactional
    public GruppoInvio segnaServito(Long gruppoInvioId) {
        GruppoInvio gruppo = trovaGruppo(gruppoInvioId);
        gruppo.segnaServito();
        emettiEvento(gruppo, "GRUPPO_SERVITO");
        return gruppo;
    }

    /**
     * Dettaglio completo di una comanda (tutte le portate con stato, tavolo,
     * cameriere, orario): serve alla cucina per correlare le portate di una
     * stessa comanda quando finiscono in colonne diverse della coda (una per
     * stato), cosa che le card per-portata da sole non permettono di vedere.
     */
    @Transactional(readOnly = true)
    public ComandaDettaglioResponse dettaglioComanda(UUID comandaId) {
        Comanda comanda = comandaRepository.trovaConTavoloEGruppiById(comandaId)
                .orElseThrow(() -> new IllegalArgumentException("Comanda non trovata: " + comandaId));
        String cameriereUsername = utenteRepository.findById(comanda.getCameriereId())
                .map(Utente::getUsername)
                .orElse(null);
        return ComandaDettaglioResponse.of(comanda, cameriereUsername);
    }

    private GruppoInvio trovaGruppo(Long gruppoInvioId) {
        return gruppoInvioRepository.findById(gruppoInvioId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Gruppo di invio non trovato: " + gruppoInvioId));
    }

    private void emettiEvento(GruppoInvio gruppo, String eventType) {
        outboxEventWriter.registraEvento(
                AGGREGATE_TYPE_GRUPPO_INVIO,
                String.valueOf(gruppo.getId()),
                eventType,
                GruppoInvioEventPayload.of(gruppo));
    }

    private Optional<GruppoInvio> primoGruppoTrattenuto(Comanda comanda) {
        return comanda.getGruppi().stream()
                .filter(GruppoInvio::isTrattenuto)
                .min(Comparator.comparingInt(GruppoInvio::getNumeroPortata));
    }

    /**
     * Spara il primo gruppo TRATTENUTO della comanda (per numeroPortata). Se
     * il gruppo non richiede lavorazione in cucina (nessuna riga con
     * MenuItem.inviaInCucina == true, es. una portata di sole bevande), lo
     * marca direttamente SERVITO senza passare dalla coda e sparalo
     * ricorsivamente il gruppo successivo — cosi' una portata "senza cucina"
     * non blocca l'avanzamento delle portate successive in attesa.
     */
    private void fireProssimoTrattenuto(Comanda comanda) {
        primoGruppoTrattenuto(comanda).ifPresent(gruppo -> {
            if (gruppo.richiedeCucina()) {
                gruppo.fire(sequenzaGenerator.prossimoSeqCoda());
                emettiEvento(gruppo, "GRUPPO_IN_CODA");
            } else {
                gruppo.servaSenzaCucina();
                emettiEvento(gruppo, "GRUPPO_SERVITO");
                fireProssimoTrattenuto(comanda);
            }
        });
    }
}
