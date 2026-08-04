package com.alnumerocinque.repository;

import com.alnumerocinque.domain.Tavolo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TavoloRepository extends JpaRepository<Tavolo, Long> {

    Optional<Tavolo> findByNumero(String numero);
}
