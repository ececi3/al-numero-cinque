package com.alnumerocinque.service;

import com.alnumerocinque.domain.*;
import com.alnumerocinque.repository.ComandaRepository;
import com.alnumerocinque.repository.GruppoInvioRepository;
import com.alnumerocinque.repository.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test di riferimento del motore di coursing.
 *
 * Scenario canonico: O1 ha due gruppi (A sparato subito, B trattenuto),
 * O2 ha un solo gruppo (sparato subito alla registrazione). Quando la
 * cucina segna pronto A, B viene sparato automaticamente. L'ordine di
 * uscita dalla coda cucina (seq_coda) deve essere O1-A -> O2 -> O1-B,
 * indipendentemente dall'ordine fisico con cui i dispositivi cameriere
 * sincronizzano le comande.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoursingServiceIntegrationTest {

    @Autowired
    private CoursingService coursingService;

    @Autowired
    private ComandaRepository comandaRepository;

    @Autowired
    private GruppoInvioRepository gruppoInvioRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Utente cameriere;
    private Tavolo tavolo;
    private MenuItem menuItem;
    private MenuItem bevanda;

    @BeforeEach
    void setUp() {
        cameriere = new Utente("mario.rossi", "hash", RuoloUtente.CAMERIERE);
        entityManager.persist(cameriere);

        tavolo = new Tavolo("T5");
        entityManager.persist(tavolo);

        menuItem = new MenuItem("Tagliatelle al ragu'", "primo piatto", new BigDecimal("12.00"), null, true);
        entityManager.persist(menuItem);

        bevanda = new MenuItem("Acqua naturale", "bevanda", new BigDecimal("2.00"), null, false);
        entityManager.persist(bevanda);

        entityManager.flush();
    }

    private Sessione nuovaSessione() {
        Sessione sessione = new Sessione(UUID.randomUUID(), tavolo, cameriere.getId(), 2, OffsetDateTime.now());
        entityManager.persist(sessione);
        return sessione;
    }

    private Comanda nuovaComanda(Sessione sessione, int... numeriPortata) {
        Comanda comanda = new Comanda(UUID.randomUUID(), sessione, cameriere.getId(), OffsetDateTime.now());
        for (int numeroPortata : numeriPortata) {
            GruppoInvio gruppo = comanda.aggiungiGruppo(numeroPortata);
            gruppo.aggiungiRiga(menuItem, 1, menuItem.getPrezzo(), null);
        }
        return comanda;
    }

    @Test
    void flussoCanonico_ordineCodaCucina_O1A_O2_O1B() {
        // O1: due portate, A (1) e B (2). O2: una sola portata (1).
        Comanda o1 = nuovaComanda(nuovaSessione(), 1, 2);
        Comanda o2 = nuovaComanda(nuovaSessione(), 1);

        Comanda o1Registrata = coursingService.registraComanda(o1);
        Comanda o2Registrata = coursingService.registraComanda(o2);

        GruppoInvio o1A = o1Registrata.getGruppi().get(0);
        GruppoInvio o1B = o1Registrata.getGruppi().get(1);
        GruppoInvio o2Gruppo = o2Registrata.getGruppi().get(0);

        // Alla registrazione: solo il primo gruppo di ciascuna comanda e' in coda.
        assertThat(o1A.getStato()).isEqualTo(StatoGruppo.IN_CODA);
        assertThat(o1B.getStato()).isEqualTo(StatoGruppo.TRATTENUTO);
        assertThat(o2Gruppo.getStato()).isEqualTo(StatoGruppo.IN_CODA);

        // seq_coda e' una sequenza DB non transazionale: non azzerata tra test,
        // quindi si verifica l'ordine relativo rispetto al primo valore osservato
        // piuttosto che valori assoluti.
        long seqBase = o1A.getSeqCoda();
        assertThat(o2Gruppo.getSeqCoda()).isEqualTo(seqBase + 1);
        assertThat(o1B.getSeqCoda()).isNull();

        // La cucina segna pronto O1-A: deve sparare automaticamente O1-B.
        coursingService.iniziaPreparazione(o1A.getId());
        coursingService.segnaPronto(o1A.getId());

        entityManager.flush();
        entityManager.clear();

        GruppoInvio o1BAggiornato = gruppoInvioRepository.findById(o1B.getId()).orElseThrow();
        assertThat(o1BAggiornato.getStato()).isEqualTo(StatoGruppo.IN_CODA);
        assertThat(o1BAggiornato.getSeqCoda()).isEqualTo(seqBase + 2);

        // Ordine finale della coda (esclusi i TRATTENUTO/SERVITO): O1-A, O2, O1-B.
        List<GruppoInvio> coda = gruppoInvioRepository.findByStatoInOrderBySeqCodaAsc(
                List.of(StatoGruppo.IN_CODA, StatoGruppo.IN_PREP, StatoGruppo.PRONTO));

        assertThat(coda).extracting(GruppoInvio::getId)
                .containsExactly(o1A.getId(), o2Gruppo.getId(), o1B.getId());

        // Ogni fire e ogni pronto devono aver scritto un evento nell'outbox,
        // nella stessa transazione della mutazione di dominio.
        List<OutboxEvent> eventi = outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc();
        assertThat(eventi).extracting(OutboxEvent::getEventType)
                .containsExactly("GRUPPO_IN_CODA", "GRUPPO_IN_CODA", "GRUPPO_IN_PREP", "GRUPPO_PRONTO", "GRUPPO_IN_CODA");
        assertThat(eventi).allMatch(e -> !e.isPubblicato());
    }

    @Test
    void registraComanda_eIdempotenteSuRetryDopoAckPerso() {
        Comanda o1 = nuovaComanda(nuovaSessione(), 1);

        Comanda primaRegistrazione = coursingService.registraComanda(o1);
        Long seqServerAssegnato = primaRegistrazione.getSeqServer();
        Long seqCodaAssegnato = primaRegistrazione.getGruppi().get(0).getSeqCoda();

        entityManager.flush();
        entityManager.clear();

        // Il client re-invia la stessa comanda (id UUID identico) dopo un ack perso.
        Comanda comandaRicostruitaDalClient = comandaRepository.findById(o1.getId()).orElseThrow();
        Comanda secondaRegistrazione = coursingService.registraComanda(comandaRicostruitaDalClient);

        assertThat(secondaRegistrazione.getSeqServer()).isEqualTo(seqServerAssegnato);
        assertThat(secondaRegistrazione.getGruppi().get(0).getSeqCoda()).isEqualTo(seqCodaAssegnato);
    }

    @Test
    void segnaPronto_suGruppoGiaServito_lanciaEccezione() {
        Comanda o1 = nuovaComanda(nuovaSessione(), 1);
        Comanda registrata = coursingService.registraComanda(o1);
        Long gruppoId = registrata.getGruppi().get(0).getId();

        coursingService.iniziaPreparazione(gruppoId);
        coursingService.segnaPronto(gruppoId);
        coursingService.segnaServito(gruppoId);
        entityManager.flush();

        assertThrows(IllegalStateException.class, () -> coursingService.segnaPronto(gruppoId));
    }

    @Test
    void cicloCompletoStati_emetteEventoPerOgniTransizione() {
        Comanda o1 = nuovaComanda(nuovaSessione(), 1);
        Comanda registrata = coursingService.registraComanda(o1);
        Long gruppoId = registrata.getGruppi().get(0).getId();

        coursingService.iniziaPreparazione(gruppoId);
        coursingService.segnaPronto(gruppoId);
        coursingService.segnaServito(gruppoId);

        GruppoInvio finale = gruppoInvioRepository.findById(gruppoId).orElseThrow();
        assertThat(finale.getStato()).isEqualTo(StatoGruppo.SERVITO);

        List<OutboxEvent> eventi = outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc();
        assertThat(eventi).extracting(OutboxEvent::getEventType)
                .containsExactly("GRUPPO_IN_CODA", "GRUPPO_IN_PREP", "GRUPPO_PRONTO", "GRUPPO_SERVITO");
    }

    @Test
    void gruppoDiSoleBevande_vieneServitoDirettamenteSenzaPassareDallaCoda() {
        Comanda comanda = new Comanda(UUID.randomUUID(), nuovaSessione(), cameriere.getId(), OffsetDateTime.now());
        GruppoInvio soloBevande = comanda.aggiungiGruppo(1);
        soloBevande.aggiungiRiga(bevanda, 2, bevanda.getPrezzo(), null);

        Comanda registrata = coursingService.registraComanda(comanda);
        GruppoInvio gruppo = registrata.getGruppi().get(0);

        assertThat(gruppo.getStato()).isEqualTo(StatoGruppo.SERVITO);
        assertThat(gruppo.getSeqCoda()).isNull();

        List<OutboxEvent> eventi = outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc();
        assertThat(eventi).extracting(OutboxEvent::getEventType).containsExactly("GRUPPO_SERVITO");
    }

    @Test
    void portataSenzaCucina_nonBloccaLaPortataSuccessiva() {
        Comanda comanda = new Comanda(UUID.randomUUID(), nuovaSessione(), cameriere.getId(), OffsetDateTime.now());
        GruppoInvio bevande = comanda.aggiungiGruppo(1);
        bevande.aggiungiRiga(bevanda, 1, bevanda.getPrezzo(), null);
        GruppoInvio primo = comanda.aggiungiGruppo(2);
        primo.aggiungiRiga(menuItem, 1, menuItem.getPrezzo(), null);

        Comanda registrata = coursingService.registraComanda(comanda);
        GruppoInvio bevandeRegistrato = registrata.getGruppi().get(0);
        GruppoInvio primoRegistrato = registrata.getGruppi().get(1);

        // La portata di sole bevande salta la coda; la portata successiva
        // (che richiede cucina) viene sparata subito, non resta TRATTENUTO.
        assertThat(bevandeRegistrato.getStato()).isEqualTo(StatoGruppo.SERVITO);
        assertThat(primoRegistrato.getStato()).isEqualTo(StatoGruppo.IN_CODA);
        assertThat(primoRegistrato.getSeqCoda()).isNotNull();

        List<OutboxEvent> eventi = outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc();
        assertThat(eventi).extracting(OutboxEvent::getEventType)
                .containsExactly("GRUPPO_SERVITO", "GRUPPO_IN_CODA");
    }
}
