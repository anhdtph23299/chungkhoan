package com.vntrade.backend.repository;

import com.vntrade.backend.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    Optional<Watchlist> findBySymbol(String symbol);
    List<Watchlist> findByExchangeOrderBySymbol(String exchange);
    boolean existsBySymbol(String symbol);
}
