package com.alnumerocinque.repository;

import com.alnumerocinque.domain.TokenRevocato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface TokenRevocatoRepository extends JpaRepository<TokenRevocato, String> {

    long deleteByScadeAtBefore(OffsetDateTime istante);
}
