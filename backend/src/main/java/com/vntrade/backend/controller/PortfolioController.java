package com.vntrade.backend.controller;

import com.vntrade.backend.dto.PortfolioSummary;
import com.vntrade.backend.entity.PortfolioSnapshot;
import com.vntrade.backend.service.PortfolioHistoryService;
import com.vntrade.backend.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class PortfolioController {

    private final PortfolioHistoryService portfolioHistoryService;
    private final TradeService tradeService;
    private final com.vntrade.backend.service.MarkowitzOptimizationService markowitzService;

    @GetMapping("/markowitz-frontier")
    public ResponseEntity<com.vntrade.backend.dto.MarkowitzEfficientFrontierDto> getMarkowitzEfficientFrontier(
            @RequestParam(required = false) java.math.BigDecimal capital) {
        return ResponseEntity.ok(markowitzService.calculateEfficientFrontier(capital));
    }

    @GetMapping("/equity-curve")
    public ResponseEntity<List<PortfolioSnapshot>> getEquityCurve() {
        return ResponseEntity.ok(portfolioHistoryService.getEquityCurve());
    }

    @GetMapping("/performance-comparison")
    public ResponseEntity<com.vntrade.backend.dto.PerformanceComparisonDto> getPerformanceComparison() {
        return ResponseEntity.ok(portfolioHistoryService.getPerformanceComparison());
    }

    @PostMapping("/snapshot")
    public ResponseEntity<PortfolioSnapshot> recordSnapshot() {
        return ResponseEntity.ok(portfolioHistoryService.recordCurrentSnapshot());
    }

    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummary> getPortfolioSummary() {
        return ResponseEntity.ok(tradeService.getPortfolioSummary());
    }

    @PostMapping("/reset-snapshots")
    public ResponseEntity<List<PortfolioSnapshot>> resetSnapshots() {
        return ResponseEntity.ok(portfolioHistoryService.resetAndReseedSnapshots());
    }
}
