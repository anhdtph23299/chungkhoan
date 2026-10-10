package com.vntrade.backend.controller;

import com.vntrade.backend.dto.DailyIncomeDto;
import com.vntrade.backend.dto.SmartMoneyFlowDto;
import com.vntrade.backend.dto.WealthProjectionDto;
import com.vntrade.backend.service.execution.DailyIncomeService;
import com.vntrade.backend.service.execution.MarketSimulationService;
import com.vntrade.backend.service.calculation.MonteCarloProjectionService;
import com.vntrade.backend.service.decision.SmartMoneyFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/income")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class DailyIncomeController {

    private final DailyIncomeService dailyIncomeService;
    private final MarketSimulationService simulationService;
    private final MonteCarloProjectionService monteCarloService;
    private final SmartMoneyFlowService smartMoneyService;

    @GetMapping("/today")
    public ResponseEntity<DailyIncomeDto> getTodayIncome() {
        return ResponseEntity.ok(dailyIncomeService.getTodayIncomeReport());
    }

    @GetMapping("/history")
    public ResponseEntity<List<DailyIncomeDto>> getIncomeHistory(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(dailyIncomeService.getIncomeHistory(days));
    }

    @GetMapping("/wealth-projection")
    public ResponseEntity<WealthProjectionDto> getWealthProjection() {
        return ResponseEntity.ok(monteCarloService.calculate90DayWealthProjection());
    }

    @GetMapping("/smart-money")
    public ResponseEntity<List<SmartMoneyFlowDto>> getSmartMoneyFlow() {
        return ResponseEntity.ok(smartMoneyService.getTopAccumulationSymbols());
    }

    @GetMapping("/smart-money/{symbol}")
    public ResponseEntity<SmartMoneyFlowDto> getSmartMoneyForSymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(smartMoneyService.analyzeSmartMoney(symbol));
    }

    @GetMapping("/money-matrix")
    public ResponseEntity<Map<String, Object>> getMoneyMakingMatrix() {
        Map<String, Object> matrix = new HashMap<>();
        DailyIncomeDto today = dailyIncomeService.getTodayIncomeReport();
        WealthProjectionDto projection = monteCarloService.calculate90DayWealthProjection();
        List<SmartMoneyFlowDto> smartMoney = smartMoneyService.getTopAccumulationSymbols();

        matrix.put("timestamp", java.time.LocalDateTime.now().toString());
        matrix.put("status", "ACTIVE_PROFIT_GENERATION");
        matrix.put("todayRealizedProfit", today.getDailyRealizedProfit());
        matrix.put("todayWithdrawableCash", today.getWithdrawableIncome());
        matrix.put("todayReinvestment", today.getReinvestmentCapital());
        matrix.put("winRate", today.getWinRateToday());
        matrix.put("profitFactor", projection.getProfitFactor());
        matrix.put("monthlyRoiPercent", projection.getMonthlyRoiPercent());
        matrix.put("estimatedDaysToDoubleNav", projection.getEstimatedDaysToDoubleNav());
        matrix.put("projectedNav90Days", projection.getProjectedNav90Days());
        matrix.put("projectedWithdrawableCash90Days", projection.getProjectedWithdrawableCash90Days());
        matrix.put("topSmartMoneyPicks", smartMoney.stream().limit(3).toList());
        matrix.put("executiveSummary", projection.getFinancialIndependenceVerdict());

        return ResponseEntity.ok(matrix);
    }

    @PostMapping("/harvest/{symbol}")
    public ResponseEntity<Map<String, Object>> harvestProfit(@PathVariable String symbol) {
        return ResponseEntity.ok(simulationService.triggerDirectProfitHarvest(symbol));
    }

    @PostMapping("/simulate-tick")
    public ResponseEntity<Map<String, Object>> simulateTick() {
        return ResponseEntity.ok(simulationService.simulateMarketTick());
    }

    @PostMapping("/fast-forward-day")
    public ResponseEntity<Map<String, Object>> fastForwardDay() {
        return ResponseEntity.ok(simulationService.fastForwardTradingDay());
    }
}

