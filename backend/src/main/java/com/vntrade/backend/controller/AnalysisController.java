package com.vntrade.backend.controller;

import com.vntrade.backend.dto.StockScanResult;
import com.vntrade.backend.service.StrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AnalysisController {

    private final StrategyService strategyService;
    private final com.vntrade.backend.service.SectorRotationService sectorRotationService;
    private final com.vntrade.backend.service.VN30SignalScreenerService vn30SignalScreenerService;
    private final com.vntrade.backend.service.VcpPatternDetectorService vcpDetectorService;
    private final com.vntrade.backend.service.AtcExecutionService atcExecutionService;
    private final com.vntrade.backend.service.MultiTimeframeConfluenceService confluenceService;
    private final com.vntrade.backend.service.CanslimRatingService canslimRatingService;
    private final com.vntrade.backend.service.MarketRegimeDetectionService marketRegimeService;
    private final com.vntrade.backend.service.IntradayOrderFlowFootprintService footprintService;
    private final com.vntrade.backend.service.MultiFactorAttributionService factorAttributionService;
    private final com.vntrade.backend.service.StatisticalArbitrageService statisticalArbitrageService;
    private final com.vntrade.backend.service.KalmanFilterTrendService kalmanFilterTrendService;
    private final com.vntrade.backend.service.RegimeSwitchingSignalService regimeSwitchingSignalService;
    private final com.vntrade.backend.service.AdaptivePositionSizingService adaptivePositionSizingService;
    private final com.vntrade.backend.service.RelativeRotationGraphService relativeRotationGraphService;

    @GetMapping("/kalman-filter/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.KalmanFilterTrendDto> getKalmanFilterAnalysis(@PathVariable String symbol) {
        return ResponseEntity.ok(kalmanFilterTrendService.analyzeKalmanTrend(symbol));
    }

    /**
     * HMM Regime Switching Signal — phân tích chế độ thị trường hiện tại
     * với xác suất Markov chuyển trạng thái Bull/Bear/Sideways
     */
    @GetMapping("/regime-switching")
    public ResponseEntity<com.vntrade.backend.dto.RegimeSwitchingDto> getRegimeSwitchingSignal() {
        return ResponseEntity.ok(regimeSwitchingSignalService.analyzeMarketRegime());
    }

    /**
     * Adaptive Position Sizing — kích thước vị thế tối ưu kết hợp
     * ATR Volatility Targeting + Kelly Criterion + Regime Multiplier + Vol Environment
     */
    @GetMapping("/adaptive-sizing/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.AdaptivePositionSizingDto> getAdaptivePositionSizing(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "200000000") java.math.BigDecimal capital) {
        return ResponseEntity.ok(adaptivePositionSizingService.calculateAdaptiveSize(symbol, capital));
    }

    @GetMapping("/statistical-arbitrage/pairs")
    public ResponseEntity<List<com.vntrade.backend.dto.StatisticalArbitragePairDto>> getStatisticalArbitragePairs() {
        return ResponseEntity.ok(statisticalArbitrageService.analyzeAllArbitragePairs());
    }

    @GetMapping("/statistical-arbitrage/pair")
    public ResponseEntity<com.vntrade.backend.dto.StatisticalArbitragePairDto> getStatisticalArbitrageCustomPair(
            @RequestParam String stockA,
            @RequestParam String stockB,
            @RequestParam(required = false, defaultValue = "Tùy chọn") String sector) {
        return ResponseEntity.ok(statisticalArbitrageService.analyzePair(stockA, stockB, sector));
    }

    @GetMapping("/factor-attribution/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.MultiFactorAttributionDto> getFactorAttribution(@PathVariable String symbol) {
        return ResponseEntity.ok(factorAttributionService.calculateFactorAttribution(symbol));
    }

    @GetMapping("/footprint/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.FootprintDeltaDto> getFootprintOrderFlow(@PathVariable String symbol) {
        return ResponseEntity.ok(footprintService.analyzeFootprintOrderFlow(symbol));
    }

    @GetMapping("/market-regime")
    public ResponseEntity<com.vntrade.backend.dto.MarketRegimeDto> getMarketRegime() {
        return ResponseEntity.ok(marketRegimeService.detectMarketRegime());
    }

    @GetMapping("/canslim")
    public ResponseEntity<List<com.vntrade.backend.dto.CanslimRatingDto>> getCanslimLeaders() {
        return ResponseEntity.ok(canslimRatingService.getTopInstitutionalWatchlist());
    }

    @GetMapping("/canslim/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.CanslimRatingDto> getCanslimForSymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(canslimRatingService.rateStock(symbol));
    }

    @GetMapping("/confluence")
    public ResponseEntity<List<com.vntrade.backend.dto.MultiTimeframeConfluenceDto>> getConfluenceScan() {
        return ResponseEntity.ok(confluenceService.scanConfluenceLeaders());
    }

    @GetMapping("/confluence/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.MultiTimeframeConfluenceDto> getConfluenceForSymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(confluenceService.analyzeMultiTimeframe(symbol));
    }

    @GetMapping("/scan")
    public ResponseEntity<List<StockScanResult>> scanStocks(
        @RequestParam(required = false, defaultValue = "ALL") String strategy
    ) {
        return ResponseEntity.ok(strategyService.scanByStrategy(strategy));
    }

    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<StockScanResult> evaluateSymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(strategyService.evaluateSymbol(symbol));
    }

    @GetMapping("/sector-rotation")
    public ResponseEntity<List<com.vntrade.backend.dto.SectorRotationDto>> getSectorRotation() {
        return ResponseEntity.ok(sectorRotationService.analyzeSectorRotation());
    }

    @GetMapping("/screener")
    public ResponseEntity<List<com.vntrade.backend.dto.SignalScreenerDto>> getSignalScreener() {
        return ResponseEntity.ok(vn30SignalScreenerService.screenVN30Signals());
    }

    @GetMapping("/vcp")
    public ResponseEntity<List<com.vntrade.backend.dto.VcpPatternDto>> scanVcp() {
        return ResponseEntity.ok(vcpDetectorService.scanVcpAcrossWatchlist());
    }

    @GetMapping("/vcp/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.VcpPatternDto> evaluateVcp(@PathVariable String symbol) {
        return ResponseEntity.ok(vcpDetectorService.detectVcpPattern(symbol));
    }

    @GetMapping("/atc")
    public ResponseEntity<List<com.vntrade.backend.dto.AtcOrderFlowDto>> getAtcAnalysis() {
        return ResponseEntity.ok(atcExecutionService.evaluateTopAtcStocks());
    }

    @GetMapping("/atc/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.AtcOrderFlowDto> getAtcForSymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(atcExecutionService.evaluateAtcWindow(symbol));
    }

    /**
     * Relative Rotation Graph (RRG) — Đồ thị xoay tua tương đối so với VN-Index:
     * Phân bổ 4 góc phần tư (Leading, Weakening, Lagging, Improving),
     * Véc-tơ vận tốc góc (Heading Angle 0-360 độ) và Quỹ đạo 5 phiên.
     */
    @GetMapping("/rrg/vn30")
    public ResponseEntity<com.vntrade.backend.dto.RelativeRotationGraphDto> getVn30Rrg() {
        return ResponseEntity.ok(relativeRotationGraphService.calculateVn30Rrg());
    }

    @GetMapping("/rrg/sectors")
    public ResponseEntity<com.vntrade.backend.dto.RelativeRotationGraphDto> getSectorsRrg() {
        return ResponseEntity.ok(relativeRotationGraphService.calculateSectorsRrg());
    }

    @GetMapping("/rrg/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.RrgItemDto> getSymbolRrg(@PathVariable String symbol) {
        return ResponseEntity.ok(relativeRotationGraphService.calculateSingleStockRrg(symbol));
    }
}

