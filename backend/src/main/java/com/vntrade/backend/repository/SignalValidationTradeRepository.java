package com.vntrade.backend.repository;

import com.vntrade.backend.entity.SignalValidationTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SignalValidationTradeRepository extends JpaRepository<SignalValidationTrade, Long> {
    List<SignalValidationTrade> findByOutcomeOrderBySignalDateDesc(String outcome);
    List<SignalValidationTrade> findAllByOrderBySignalDateDesc();
    boolean existsBySymbolAndSignalDate(String symbol, LocalDate signalDate);
    List<SignalValidationTrade> findBySignalDateBetweenOrderBySignalDateDesc(LocalDate from, LocalDate to);

    @Query("SELECT s FROM SignalValidationTrade s WHERE s.outcome = 'PENDING' ORDER BY s.signalDate DESC")
    List<SignalValidationTrade> findAllPending();
}
