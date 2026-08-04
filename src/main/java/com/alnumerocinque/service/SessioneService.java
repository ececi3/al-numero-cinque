package com.alnumerocinque.service;

import com.alnumerocinque.domain.Comanda;
import com.alnumerocinque.domain.GruppoInvio;
import com.alnumerocinque.domain.Sessione;
import com.alnumerocinque.domain.StatoGruppo;
import com.alnumerocinque.domain.Tavolo;
import com.alnumerocinque.domain.TavoloAggregato;
import com.alnumerocinque.repository.ComandaRepository;
import com.alnumerocinque.repository.SessioneRepository;
import com.alnumerocinque.repository.TavoloAggregatoRepository;
import com.alnumerocinque.repository.TavoloRepository;
import com.alnumerocinque.web.dto.ApriSessioneRequest;
import com.alnumerocinque.web.dto.ContoResponse;
import com.alnumerocinque.web.dto.SessioneDettaglioResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Apertura/chiusura sessione: operazioni sul tavolo (occupazione,
 * liberazione) sono mutazioni online-only eseguite qui, anche se la
 * creazione della sessione e' append-capable (id UUID client-generated).
 * Idempotente sull'id in apertura: un retry di sync su una sessione già
 * aperta restituisce semplicemente quella esistente, senza rioccupare il
 * tavolo.
 */
@Service
public class SessioneService {

    private static final String AGGREGATE_TYPE_SESSIONE = "SESSIONE";

    private final SessioneRepository sessioneRepository;
    private final TavoloRepository tavoloRepository;
    private final ComandaRepository comandaRepository;
    private final TavoloAggregatoRepository tavoloAggregatoRepository;
    private final OutboxEventWriter outboxEventWriter;

    public SessioneService(SessioneRepository sessioneRepository,
                            TavoloRepository tavoloRepository,
                            ComandaRepository comandaRepository,
                            TavoloAggregatoRepository tavoloAggregatoRepository,
                            OutboxEventWriter outboxEventWriter) {
        this.sessioneRepository = sessioneRepository;
        this.tavoloRepository = tavoloRepository;
        this.comandaRepository = comandaRepository;
        this.tavoloAggregatoRepository = tavoloAggregatoRepository;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public Sessione apriSessione(ApriSessioneRequest request, Long cameriereId) {
        return sessioneRepository.findById(request.id())
                .orElseGet(() -> creaNuovaSessione(request, cameriereId));
    }

    /**
     * Chiude la sessione e libera il tavolo. Rifiuta la chiusura se esiste
     * almeno una portata non ancora SERVITA (non si libera un tavolo con
     * lavoro ancora in corso in cucina o non ancora consegnato).
     */
    @Transactional
    public Sessione chiudiSessione(UUID sessioneId) {
        Sessione sessione = sessioneRepository.findById(sessioneId)
                .orElseThrow(() -> new IllegalArgumentException("Sessione non trovata: " + sessioneId));

        boolean esistonoPortateNonServite = comandaRepository.findBySessione(sessione).stream()
                .map(Comanda::getGruppi)
                .flatMap(List::stream)
                .map(GruppoInvio::getStato)
                .anyMatch(stato -> stato != StatoGruppo.SERVITO);

        if (esistonoPortateNonServite) {
            throw new IllegalStateException(
                    "Sessione " + sessioneId + " ha ancora portate non servite, non puo' essere chiusa");
        }

        sessione.chiudi();
        sessione.getTavolo().libera();
        tavoloAggregatoRepository.findBySessione(sessione)
                .forEach(aggregato -> aggregato.getTavolo().libera());

        outboxEventWriter.registraEvento(
                AGGREGATE_TYPE_SESSIONE, sessione.getId().toString(), "SESSIONE_CHIUSA",
                SessioneEventPayload.of(sessione));

        return sessione;
    }

    /**
     * Aggrega un tavolo libero a una sessione gia' aperta (es. due tavoli
     * accostati per un gruppo numeroso). Non cambia il tavolo primario della
     * sessione: registra solo l'associazione e occupa il tavolo aggiuntivo,
     * che viene liberato insieme al primario alla chiusura della sessione.
     */
    @Transactional
    public Sessione aggregaTavolo(UUID sessioneId, Long tavoloId) {
        Sessione sessione = sessioneRepository.findById(sessioneId)
                .orElseThrow(() -> new IllegalArgumentException("Sessione non trovata: " + sessioneId));

        if (!sessione.isAperta()) {
            throw new IllegalStateException("Sessione " + sessioneId + " non e' aperta");
        }

        Tavolo tavolo = tavoloRepository.findById(tavoloId)
                .orElseThrow(() -> new IllegalArgumentException("Tavolo non trovato: " + tavoloId));

        tavolo.occupa();
        tavoloAggregatoRepository.save(new TavoloAggregato(sessione, tavolo));

        return sessione;
    }

    @Transactional(readOnly = true)
    public List<Long> tavoliAggregatiDi(Sessione sessione) {
        return tavoloAggregatoRepository.findBySessione(sessione).stream()
                .map(aggregato -> aggregato.getTavolo().getId())
                .toList();
    }

    /**
     * Stato completo di una sessione, incluse le comande gia' sincronizzate:
     * usato per recuperarla da un dispositivo diverso da quello che l'ha
     * aperta (vedi SessioneDettaglioResponse).
     */
    @Transactional(readOnly = true)
    public SessioneDettaglioResponse dettaglio(UUID sessioneId) {
        Sessione sessione = sessioneRepository.findById(sessioneId)
                .orElseThrow(() -> new IllegalArgumentException("Sessione non trovata: " + sessioneId));
        return SessioneDettaglioResponse.of(sessione, tavoliAggregatiDi(sessione), comandaRepository.findBySessione(sessione));
    }

    /** Come {@link #dettaglio(UUID)}, ma partendo dal tavolo invece che dall'id sessione gia' noto. */
    @Transactional(readOnly = true)
    public SessioneDettaglioResponse dettaglioPerTavolo(Long tavoloId) {
        Sessione sessione = sessioneRepository.findApertaPerTavolo(tavoloId)
                .orElseThrow(() -> new IllegalArgumentException("Nessuna sessione aperta per il tavolo: " + tavoloId));
        return dettaglio(sessione.getId());
    }

    /**
     * Conto della sessione: puo' essere consultato in qualunque momento
     * (anche con portate ancora in coda/preparazione), utile al cameriere
     * prima di chiedere la chiusura del tavolo.
     */
    @Transactional(readOnly = true)
    public ContoResponse conto(UUID sessioneId) {
        Sessione sessione = sessioneRepository.findById(sessioneId)
                .orElseThrow(() -> new IllegalArgumentException("Sessione non trovata: " + sessioneId));
        return ContoResponse.of(sessione, comandaRepository.findBySessione(sessione));
    }

    private Sessione creaNuovaSessione(ApriSessioneRequest request, Long cameriereId) {
        Tavolo tavolo = tavoloRepository.findById(request.tavoloId())
                .orElseThrow(() -> new IllegalArgumentException("Tavolo non trovato: " + request.tavoloId()));

        tavolo.occupa();

        Sessione sessione = new Sessione(
                request.id(), tavolo, cameriereId, request.numeroCoperti(), OffsetDateTime.now());

        return sessioneRepository.save(sessione);
    }
}
