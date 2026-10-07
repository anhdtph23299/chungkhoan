package com.vntrade.backend.controller;

import com.vntrade.backend.dto.BacktestRequest;
import com.vntrade.backend.dto.BacktestResult;
import com.vntrade.backend.service.BacktestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class BacktestController {

    private final BacktestService backtestService;
    private final com.vntrade.backend.service.StrategyOptimizerService strategyOptimizerService;
    private final com.vntrade.backend.service.InstitutionalBacktestService institutionalBacktestService;
    private final com.vntrade.backend.service.MonteCarloBacktestStressService monteCarloBacktestStressService;
    private final com.vntrade.backend.service.DeflatedSharpeAuditService deflatedSharpeAuditService;
    private final com.vntrade.backend.service.WalkForwardOptimizationService walkForwardOptimizationService;

    @GetMapping("/walk-forward-optimization/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.WalkForwardOptimizationDto> getWalkForwardOptimization(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "VCP_INSTITUTIONAL_BREAKOUT") String strategy,
            @RequestParam(required = false, defaultValue = "180") int candles,
            @RequestParam(required = false, defaultValue = "4") int windows) {
        return ResponseEntity.ok(walkForwardOptimizationService.runWalkForwardAnalysis(symbol, strategy, candles, windows));
    }

    @GetMapping("/deflated-sharpe/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.DeflatedSharpeAuditDto> getDeflatedSharpeAudit(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "VCP_INSTITUTIONAL_BREAKOUT") String strategy,
            @RequestParam(required = false, defaultValue = "180") int candles,
            @RequestParam(required = false, defaultValue = "50") int trials) {
        return ResponseEntity.ok(deflatedSharpeAuditService.auditDeflatedSharpe(symbol, strategy, candles, trials));
    }

    @GetMapping("/institutional/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.InstitutionalBacktestResultDto> runInstitutionalBacktest(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "VCP_INSTITUTIONAL_BREAKOUT") String strategy,
            @RequestParam(required = false, defaultValue = "180") int candles,
            @RequestParam(required = false, defaultValue = "100000000") java.math.BigDecimal capital,
            @RequestParam(required = false, defaultValue = "7.0") double stopLoss,
            @RequestParam(required = false, defaultValue = "15.0") double takeProfit) {
        return ResponseEntity.ok(institutionalBacktestService.runInstitutionalBacktest(
            symbol, strategy, candles, capital, stopLoss, takeProfit));
    }

    @GetMapping("/monte-carlo-stress/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.MonteCarloBacktestStressDto> runMonteCarloStress(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "VCP_INSTITUTIONAL_BREAKOUT") String strategy,
            @RequestParam(required = false, defaultValue = "180") int candles,
            @RequestParam(required = false, defaultValue = "100000000") java.math.BigDecimal capital) {
        return ResponseEntity.ok(monteCarloBacktestStressService.runMonteCarloStressTest(
            symbol, strategy, candles, capital));
    }

    @GetMapping("/vn30-matrix")
    public ResponseEntity<com.vntrade.backend.dto.Vn30BacktestMatrixDto> getVn30Matrix(
            @RequestParam(required = false, defaultValue = "VCP_INSTITUTIONAL_BREAKOUT") String strategy) {
        return ResponseEntity.ok(institutionalBacktestService.runVn30InstitutionalMatrix(strategy));
    }

    @PostMapping("/run")
    public ResponseEntity<BacktestResult> runBacktest(@RequestBody BacktestRequest request) {
        return ResponseEntity.ok(backtestService.runBacktest(request));
    }

    @GetMapping("/quick/{symbol}")
    public ResponseEntity<BacktestResult> quickBacktest(@PathVariable String symbol) {
        BacktestRequest request = BacktestRequest.builder()
            .symbol(symbol)
            .strategy("VN30_ENSEMBLE")
            .candlesCount(150)
            .stopLossPercent(7.0)
            .takeProfitPercent(15.0)
            .build();
        return ResponseEntity.ok(backtestService.runBacktest(request));
    }

    @GetMapping("/compare/{symbol}")
    public ResponseEntity<java.util.List<BacktestResult>> compareStrategies(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "150") Integer candles,
            @RequestParam(required = false, defaultValue = "100000000") java.math.BigDecimal capital) {
        return ResponseEntity.ok(backtestService.runMultiStrategyComparison(symbol, candles, capital));
    }

    @GetMapping("/optimize/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.OptimizationResult> optimizeStrategy(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "150") Integer candles) {
        return ResponseEntity.ok(strategyOptimizerService.optimizeParameters(symbol, candles));
    }
}
