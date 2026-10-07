package com.vntrade.backend.controller;

import com.vntrade.backend.dto.PositionSizingRequest;
import com.vntrade.backend.dto.PositionSizingResult;
import com.vntrade.backend.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class RiskController {

    private final RiskService riskService;
    private final com.vntrade.backend.service.RealMoneyAuditService realMoneyAuditService;
    private final com.vntrade.backend.service.VietnamVeteranRulesService veteranRulesService;
    private final com.vntrade.backend.service.MarketCrashProtectionService crashProtectionService;
    private final com.vntrade.backend.service.KellyCriterionService kellyCriterionService;
    private final com.vntrade.backend.service.RiskStressTestService riskStressTestService;
    private final com.vntrade.backend.service.PortfolioVarRiskService portfolioVarRiskService;
    private final com.vntrade.backend.service.ForeignFlowRiskService foreignFlowRiskService;
    private final com.vntrade.backend.service.TargetVolatilityScalingService targetVolatilityScalingService;
    private final com.vntrade.backend.service.BlackLittermanAllocationService blackLittermanAllocationService;
    private final com.vntrade.backend.service.GarchVolatilityForecastService garchVolatilityForecastService;
    private final com.vntrade.backend.service.BlackSwanStressScenarioService blackSwanStressScenarioService;
    private final com.vntrade.backend.service.LiquidityAdjustedReturnService liquidityAdjustedReturnService;

    @GetMapping("/black-swan-stress")
    public ResponseEntity<com.vntrade.backend.dto.BlackSwanStressScenarioDto> getBlackSwanStressScenarios(
            @RequestParam(required = false) java.math.BigDecimal capital) {
        return ResponseEntity.ok(blackSwanStressScenarioService.simulateBlackSwanScenarios(capital));
    }

    /**
     * Liquidity-Adjusted Return — tính lợi nhuận thực tế sau chi phí
     * market impact (Square-Root Law) + bid-ask + phí GD + T+2.5 opportunity cost
     */
    @GetMapping("/liquidity-adjusted/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.LiquidityAdjustedReturnDto> getLiquidityAdjustedReturn(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "1000") int shares,
            @RequestParam(required = false) java.math.BigDecimal entryPrice,
            @RequestParam(required = false) java.math.BigDecimal exitPrice,
            @RequestParam(required = false, defaultValue = "200000000") java.math.BigDecimal capital) {
        return ResponseEntity.ok(liquidityAdjustedReturnService.calculateLiquidityAdjustedReturn(
            symbol, shares, entryPrice, exitPrice, capital));
    }

    @GetMapping("/garch-forecast/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.GarchVolatilityForecastDto> getGarchVolatilityForecast(@PathVariable String symbol) {
        return ResponseEntity.ok(garchVolatilityForecastService.forecastVolatility(symbol));
    }

    @GetMapping("/black-litterman")
    public ResponseEntity<com.vntrade.backend.dto.BlackLittermanAllocationDto> getBlackLittermanAllocation(
        @RequestParam(required = false) java.math.BigDecimal capital
    ) {
        return ResponseEntity.ok(blackLittermanAllocationService.calculateBlackLittermanAllocation(capital));
    }

    @GetMapping("/target-volatility-scaling")
    public ResponseEntity<com.vntrade.backend.dto.TargetVolatilityScalingDto> getTargetVolatilityScaling(
        @RequestParam(required = false) java.math.BigDecimal capital,
        @RequestParam(required = false) Double targetVol
    ) {
        return ResponseEntity.ok(targetVolatilityScalingService.calculateTargetVolatilityScaling(capital, targetVol));
    }

    @GetMapping("/foreign-flow/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.ForeignFlowRiskDto> getForeignFlowRisk(@PathVariable String symbol) {
        return ResponseEntity.ok(foreignFlowRiskService.evaluateForeignFlowRisk(symbol));
    }

    @GetMapping("/var-analysis")
    public ResponseEntity<com.vntrade.backend.dto.PortfolioVarRiskDto> getPortfolioVarAnalysis(
        @RequestParam(required = false) java.math.BigDecimal capital
    ) {
        return ResponseEntity.ok(portfolioVarRiskService.calculatePortfolioVarRisk(capital));
    }

    @GetMapping("/kelly/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.KellySizingDto> getKellySizing(
        @PathVariable String symbol,
        @RequestParam(required = false) java.math.BigDecimal capital
    ) {
        return ResponseEntity.ok(kellyCriterionService.calculateKellySizing(symbol, capital));
    }

    @GetMapping("/stress-test")
    public ResponseEntity<com.vntrade.backend.dto.StressTestReportDto> getStressTest(
        @RequestParam(required = false) java.math.BigDecimal capital
    ) {
        return ResponseEntity.ok(riskStressTestService.conductHistoricalStressTests(capital));
    }

    @GetMapping("/circuit-breaker")
    public ResponseEntity<com.vntrade.backend.dto.MarketCrashProtectionDto> getCircuitBreakerStatus() {
        return ResponseEntity.ok(crashProtectionService.evaluateMarketCircuitBreaker());
    }

    @GetMapping("/veteran-discipline-audit")
    public ResponseEntity<com.vntrade.backend.dto.VeteranDisciplineAuditDto> getVeteranDisciplineAudit() {
        return ResponseEntity.ok(veteranRulesService.auditVeteranDiscipline());
    }

    @GetMapping("/real-money-audit")
    public ResponseEntity<com.vntrade.backend.dto.RealMoneyAuditDto> getRealMoneyAudit() {
        return ResponseEntity.ok(realMoneyAuditService.conductAudit());
    }

    @PostMapping("/position-size")
    public ResponseEntity<PositionSizingResult> calculatePositionSize(@RequestBody PositionSizingRequest request) {
        return ResponseEntity.ok(riskService.calculatePositionSize(request));
    }

    @GetMapping("/portfolio-health")
    public ResponseEntity<com.vntrade.backend.dto.PortfolioHealthReport> getPortfolioHealth() {
        return ResponseEntity.ok(riskService.evaluatePortfolioHealth());
    }

    @GetMapping("/drawdown-matrix")
    public ResponseEntity<List<Map<String, Object>>> getDrawdownMatrix() {
        List<Map<String, Object>> matrix = List.of(
            Map.of("loss", 5, "gainNeeded", 5.3, "safe", true, "level", "SAFE", "note", "Mất 5% chỉ cần gỡ lại 5.3% - Vùng rất an toàn"),
            Map.of("loss", 7, "gainNeeded", 7.5, "safe", true, "level", "SAFE", "note", "Ngưỡng cắt lỗ kỷ luật chuẩn của Mark Minervini và William O'Neil"),
            Map.of("loss", 10, "gainNeeded", 11.1, "safe", false, "level", "WARNING", "note", "Bắt đầu rơi vào vùng cảnh báo vàng"),
            Map.of("loss", 20, "gainNeeded", 25.0, "safe", false, "level", "DANGER", "note", "Cần lãi 25% mới hòa vốn - Cực kỳ áp lực tâm lý"),
            Map.of("loss", 30, "gainNeeded", 42.9, "safe", false, "level", "DANGER", "note", "Mất kiểm soát, dễ rơi vào trạng thái gồng lỗ tuyệt vọng"),
            Map.of("loss", 50, "gainNeeded", 100.0, "safe", false, "level", "CRITICAL", "note", "Tài khoản chia đôi, cần lãi gấp đôi (100%) mới về bờ")
        );
        return ResponseEntity.ok(matrix);
    }

    @GetMapping("/rules")
    public ResponseEntity<List<Map<String, String>>> getTradingDisciplineRules() {
        List<Map<String, String>> rules = List.of(
            Map.of("id", "RULE_1", "title", "Cắt Lỗ Dứt Khoát Tại 7% - 8%", "content", "Không bao giờ được phép nuôi hy vọng hay trung bình giá xuống cổ phiếu rớt."),
            Map.of("id", "RULE_2", "title", "Tỷ Lệ Risk/Reward Tối Thiểu 1:2", "content", "Chỉ vào lệnh khi lợi nhuận tiềm năng lớn gấp đôi rủi ro. Xác suất thắng 40% vẫn lãi ròng."),
            Map.of("id", "RULE_3", "title", "Quy Tắc 2% Rủi Ro NAV", "content", "Không bao giờ để 1 deal làm hao hụt quá 1% - 2% tổng tài sản tài khoản."),
            Map.of("id", "RULE_4", "title", "Quan Sát Sau 14h15", "content", "80% biến động thật của chứng khoán Việt Nam diễn ra trong 15 phút cuối phiên và ATC."),
            Map.of("id", "RULE_5", "title", "Ghi Chép Nhật Ký 100% Các Lệnh", "content", "Xem xét sai lầm sau mỗi lệnh thua để không lặp lại trước khi nâng vốn thật.")
        );
        return ResponseEntity.ok(rules);
    }
}
