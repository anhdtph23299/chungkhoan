package com.vntrade.backend.repository;

import com.vntrade.backend.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByIsReadFalseOrderByCreatedAtDesc();
    List<Alert> findAllByOrderByCreatedAtDesc();
    List<Alert> findBySymbolOrderByCreatedAtDesc(String symbol);
}
