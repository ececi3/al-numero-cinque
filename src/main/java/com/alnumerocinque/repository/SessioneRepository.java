package com.alnumerocinque.repository;

import com.alnumerocinque.domain.Sessione;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SessioneRepository extends JpaRepository<Sessione, UUID> {

    @Query("select coalesce(sum(s.numeroCoperti), 0) from Sessione s")
    long sommaCoperti();

    /**
     * Sessione APERTA il cui tavolo primario o aggregato e' quello dato.
     * Serve a recuperare la sessione di un tavolo occupato da un
     * dispositivo diverso da quello che l'ha aperta (l'indice tavolo ->
     * sessione del cameriere e' altrimenti solo nel localStorage del
     * dispositivo di apertura, vedi frontend/src/offline/indiceTavoli.ts).
     */
    @Query("select s from Sessione s where s.stato = com.alnumerocinque.domain.StatoSessione.APERTA " +
            "and (s.tavolo.id = :tavoloId " +
            "or exists (select 1 from TavoloAggregato ta where ta.sessione = s and ta.tavolo.id = :tavoloId))")
    Optional<Sessione> findApertaPerTavolo(@Param("tavoloId") Long tavoloId);
}
