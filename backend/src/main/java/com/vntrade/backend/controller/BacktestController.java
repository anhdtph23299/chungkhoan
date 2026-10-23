package com.vntrade.backend.controller;

import com.vntrade.backend.dto.BacktestRequest;
import com.vntrade.backend.dto.BacktestResult;
import com.vntrade.backend.service.backtest.BacktestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.vntrade.backend.service.decision.StrategyOptimizerService;
import com.vntrade.backend.service.calculation.MonteCarloBacktestStressService;
import com.vntrade.backend.service.backtest.InstitutionalBacktestService;
import com.vntrade.backend.service.calculation.DeflatedSharpeAuditService;
import com.vntrade.backend.service.backtest.WalkForwardOptimizationService;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class BacktestController {

    private final BacktestService backtestService;
    private final com.vntrade.backend.service.decision.StrategyOptimizerService strategyOptimizerService;
    private final com.vntrade.backend.service.backtest.InstitutionalBacktestService institutionalBacktestService;
    private final com.vntrade.backend.service.calculation.MonteCarloBacktestStressService monteCarloBacktestStressService;
    private final com.vntrade.backend.service.calculation.DeflatedSharpeAuditService deflatedSharpeAuditService;
    private final com.vntrade.backend.service.backtest.WalkForwardOptimizationService walkForwardOptimizationService;

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
            .strategy("VET_VSA_VCP")
            .candlesCount(500)
            .stopLossPercent(6.0)
            .takeProfitPercent(18.0)
            .build();
        return ResponseEntity.ok(backtestService.runBacktest(request));
    }

    @GetMapping("/compare/{symbol}")
    public ResponseEntity<java.util.List<BacktestResult>> compareStrategies(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "500") Integer candles,
            @RequestParam(required = false, defaultValue = "100000000") java.math.BigDecimal capital) {
        return ResponseEntity.ok(backtestService.runMultiStrategyComparison(symbol, candles, capital));
    }

    @GetMapping("/optimize/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.OptimizationResult> optimizeStrategy(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "500") Integer candles) {
        return ResponseEntity.ok(strategyOptimizerService.optimizeParameters(symbol, candles));
    }

    /**
     * Chạy VET_VSA_VCP (Chiến thuật Lão làng VSA + Minervini VCP + Chốt 50% tiền mặt)
     * trên 10 cổ phiếu leader hàng đầu thị trường VN
     */
    @GetMapping("/veteran-focus")
    public ResponseEntity<java.util.List<BacktestResult>> veteranFocusLeaders(
            @RequestParam(required = false, defaultValue = "500") Integer candles,
            @RequestParam(required = false, defaultValue = "6.0") Double sl,
            @RequestParam(required = false, defaultValue = "18.0") Double tp) {
        java.util.List<String> leaders = java.util.List.of(
            "TCB", "MWG", "FPT", "HPG", "VCB", "BID", "SSI", "ACB", "VHM", "DCM"
        );
        java.util.List<BacktestResult> results = new java.util.ArrayList<>();
        for (String sym : leaders) {
            BacktestRequest req = BacktestRequest.builder()
                .symbol(sym)
                .strategy("VET_VSA_VCP")
                .candlesCount(candles)
                .initialCapital(java.math.BigDecimal.valueOf(100_000_000))
                .stopLossPercent(sl)
                .takeProfitPercent(tp)
                .build();
            results.add(backtestService.runBacktest(req));
        }
        results.sort((a, b) -> b.getNetProfit().compareTo(a.getNetProfit()));
        return ResponseEntity.ok(results);
    }

    /**
     * Chạy RSI_OVERSOLD 500 nến trên tất cả cổ phiếu leader cùng lúc
     * Trả về bảng xếp hạng theo lợi nhuận ròng (tốt nhất lên đầu)
     */
    @GetMapping("/rsi-focus")
    public ResponseEntity<java.util.List<BacktestResult>> rsiFocusLeaders(
            @RequestParam(required = false, defaultValue = "500") Integer candles,
            @RequestParam(required = false, defaultValue = "6.0") Double sl,
            @RequestParam(required = false, defaultValue = "18.0") Double tp) {
        java.util.List<String> leaders = java.util.List.of(
            "TCB", "DCM", "MWG", "FPT", "HPG", "VCB", "BID", "SSI", "ACB", "VHM"
        );
        java.util.List<BacktestResult> results = new java.util.ArrayList<>();
        for (String sym : leaders) {
            BacktestRequest req = BacktestRequest.builder()
                .symbol(sym)
                .strategy("RSI_OVERSOLD")
                .candlesCount(candles)
                .initialCapital(java.math.BigDecimal.valueOf(100_000_000))
                .stopLossPercent(sl)
                .takeProfitPercent(tp)
                .build();
            results.add(backtestService.runBacktest(req));
        }
        // Sắp xếp: lợi nhuận cao nhất lên đầu
        results.sort((a, b) -> b.getNetProfit().compareTo(a.getNetProfit()));
        return ResponseEntity.ok(results);
    }
}