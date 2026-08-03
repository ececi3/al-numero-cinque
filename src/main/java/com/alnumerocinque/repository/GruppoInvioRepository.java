package com.alnumerocinque.repository;

import com.alnumerocinque.domain.GruppoInvio;
import com.alnumerocinque.domain.StatoGruppo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GruppoInvioRepository extends JpaRepository<GruppoInvio, Long> {

    // Override del metodo base findById per applicare l'entity graph, invece
    // di un nome derivato tipo "findWithComandaById" (ambiguo per il parser
    // di query derivation: contiene un secondo "By" dentro "ComandaById").
    @Override
    @EntityGraph(attributePaths = {"comanda", "righe"})
    Optional<GruppoInvio> findById(Long id);

    /** Coda cucina corrente, ordinata per ordine di ingresso (fire). */
    List<GruppoInvio> findByStatoInOrderBySeqCodaAsc(Collection<StatoGruppo> stati);

    /**
     * Proiezione grezza (numeroPortata, inCodaAt, prontoAt) dei gruppi che
     * hanno completato la preparazione, usata da AnalyticsService per
     * calcolare i tempi medi in Java (evita funzioni data/ora specifiche del
     * dialetto SQL, che differirebbero tra H2 nei test e PostgreSQL in
     * produzione).
     */
    @Query("select g.numeroPortata as numeroPortata, g.inCodaAt as inCodaAt, g.prontoAt as prontoAt " +
            "from GruppoInvio g where g.inCodaAt is not null and g.prontoAt is not null")
    List<TempoPreparazioneProiezione> trovaTempiPreparazione();

    interface TempoPreparazioneProiezione {
        int getNumeroPortata();

        OffsetDateTime getInCodaAt();

        OffsetDateTime getProntoAt();
    }
}
