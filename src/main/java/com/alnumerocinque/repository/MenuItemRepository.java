package com.alnumerocinque.repository;

import com.alnumerocinque.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByDisponibileTrue();
}
