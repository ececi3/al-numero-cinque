package com.alnumerocinque.repository;

import com.alnumerocinque.domain.Sessione;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface SessioneRepository extends JpaRepository<Sessione, UUID> {

    @Query("select coalesce(sum(s.numeroCoperti), 0) from Sessione s")
    long sommaCoperti();
}
