package com.vntrade.backend.service;

import com.vntrade.backend.entity.Watchlist;
import com.vntrade.backend.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;

    public List<Watchlist> getAll() {
        return watchlistRepository.findAll();
    }

    @Transactional
    public Watchlist addOrUpdate(Watchlist item) {
        item.setSymbol(item.getSymbol().toUpperCase().trim());
        return watchlistRepository.findBySymbol(item.getSymbol())
            .map(existing -> {
                existing.setExchange(item.getExchange());
                existing.setTargetPrice(item.getTargetPrice());
                existing.setCurrentPrice(item.getCurrentPrice());
                existing.setStopLoss(item.getStopLoss());
                existing.setTakeProfit(item.getTakeProfit());
                existing.setRsi(item.getRsi());
                existing.setNotes(item.getNotes());
                return watchlistRepository.save(existing);
            })
            .orElseGet(() -> watchlistRepository.save(item));
    }

    @Transactional
    public Watchlist updatePrice(Long id, BigDecimal price, BigDecimal rsi) {
        Watchlist item = watchlistRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Watchlist item không tồn tại: " + id));
        if (price != null) item.setCurrentPrice(price);
        if (rsi != null) item.setRsi(rsi);
        return watchlistRepository.save(item);
    }

    @Transactional
    public void delete(Long id) {
        watchlistRepository.deleteById(id);
    }
}
