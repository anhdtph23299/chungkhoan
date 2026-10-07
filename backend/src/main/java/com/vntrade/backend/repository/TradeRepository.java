package com.vntrade.backend.repository;

import com.vntrade.backend.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findBySymbolOrderByTradeDateDesc(String symbol);

    List<Trade> findByStatusOrderByTradeDateDesc(String status);

    List<Trade> findAllByOrderByTradeDateDesc();

    @Query("SELECT DISTINCT t.symbol FROM Trade t WHERE t.status = 'open'")
    List<String> findOpenSymbols();

    @Query("SELECT t FROM Trade t WHERE t.type = 'buy' AND t.status = 'open' ORDER BY t.tradeDate DESC")
    List<Trade> findOpenBuyTrades();

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.status = 'closed' AND t.pnl > 0")
    long countWinningTrades();

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.status = 'closed'")
    long countClosedTrades();
}
