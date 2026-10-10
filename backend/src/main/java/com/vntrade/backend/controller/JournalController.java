package com.vntrade.backend.controller;

import com.vntrade.backend.dto.PnLLedgerItemDto;
import com.vntrade.backend.dto.TradeAnalytics;
import com.vntrade.backend.dto.TradingRatiosDto;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import com.vntrade.backend.service.calculation.AdvancedTradingAnalyticsService;
import com.vntrade.backend.service.portfolio.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.vntrade.backend.service.calculation.BrinsonPerformanceAttributionService;

@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class JournalController {

    private final JournalService journalService;
    private final TradeRepository tradeRepository;
    private final AdvancedTradingAnalyticsService advancedAnalyticsService;
    private final com.vntrade.backend.service.calculation.BrinsonPerformanceAttributionService attributionService;

    @GetMapping("/performance-attribution")
    public ResponseEntity<com.vntrade.backend.dto.PerformanceAttributionDto> getPerformanceAttribution() {
        return ResponseEntity.ok(attributionService.calculatePerformanceAttribution());
    }

    @GetMapping("/analytics")
    public ResponseEntity<TradeAnalytics> getTradeAnalytics() {
        return ResponseEntity.ok(journalService.computeAnalytics());
    }

    @GetMapping("/entries")
    public ResponseEntity<List<Trade>> getClosedJournalEntries() {
        return ResponseEntity.ok(tradeRepository.findByStatusOrderByTradeDateDesc("closed"));
    }

    @GetMapping("/ratios")
    public ResponseEntity<TradingRatiosDto> getInstitutionalRatios() {
        return ResponseEntity.ok(advancedAnalyticsService.computeInstitutionalRatios());
    }

    @GetMapping("/pnl-ledger")
    public ResponseEntity<List<PnLLedgerItemDto>> getPnLLedger() {
        return ResponseEntity.ok(advancedAnalyticsService.getFullPnLLedger());
    }
}
