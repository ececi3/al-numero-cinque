package com.alnumerocinque.repository;

import com.alnumerocinque.domain.Sessione;
import com.alnumerocinque.domain.TavoloAggregato;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TavoloAggregatoRepository extends JpaRepository<TavoloAggregato, Long> {

    @EntityGraph(attributePaths = {"tavolo"})
    List<TavoloAggregato> findBySessione(Sessione sessione);
}
