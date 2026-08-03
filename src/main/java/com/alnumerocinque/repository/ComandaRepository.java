package com.alnumerocinque.repository;

import com.alnumerocinque.domain.Comanda;
import com.alnumerocinque.domain.Sessione;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComandaRepository extends JpaRepository<Comanda, UUID> {

    // Override del metodo base per applicare l'entity graph: evita di usare un
    // nome derivato tipo "findWithGruppiById", che contiene una occorrenza
    // ambigua di "By" (...ComandaById) e verrebbe interpretata scorrettamente
    // dal query-derivation di Spring Data.
    @Override
    @EntityGraph(attributePaths = {"gruppi", "gruppi.righe"})
    Optional<Comanda> findById(UUID id);

    // Solo "gruppi": un @EntityGraph che includa anche "gruppi.righe" causa
    // MultipleBagFetchException in Hibernate (due collezioni-List annidate
    // fetchate nella stessa query). "righe" e "righe.menuItem" restano quindi
    // lazy: va bene per i chiamanti attuali, entrambi @Transactional
    // (SessioneService.chiudiSessione, SessioneService.dettaglio).
    @EntityGraph(attributePaths = {"gruppi"})
    List<Comanda> findBySessione(Sessione sessione);

    // Query esplicita (non "findWithTavoloEGruppiById", per lo stesso motivo
    // di ambiguita' del parser di query derivation gia' annotato su
    // findById sopra). Niente "gruppi.righe" nell'entity graph: fetchare
    // insieme due collezioni-List annidate (gruppi -> righe) causa
    // MultipleBagFetchException in Hibernate; "righe"/"menuItem" restano
    // percio' lazy, caricati on-demand dentro il metodo @Transactional
    // chiamante (CoursingService.dettaglioComanda).
    @EntityGraph(attributePaths = {"sessione", "sessione.tavolo", "gruppi"})
    @Query("select c from Comanda c where c.id = :id")
    Optional<Comanda> trovaConTavoloEGruppiById(@Param("id") UUID id);
}
