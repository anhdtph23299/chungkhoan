package com.vntrade.backend.controller;

import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.service.AutoTradingBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class BotController {

    private final AutoTradingBotService botService;
    private final com.vntrade.backend.service.MarketSimulationService simulationService;
    private final com.vntrade.backend.service.VN30SignalScreenerService vn30SignalScreenerService;
    private final com.vntrade.backend.service.BotConfigService botConfigService;

    @GetMapping("/config")
    public ResponseEntity<com.vntrade.backend.dto.BotConfigDto> getBotConfig() {
        return ResponseEntity.ok(botConfigService.getConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<com.vntrade.backend.dto.BotConfigDto> updateBotConfig(@RequestBody com.vntrade.backend.dto.BotConfigDto update) {
        return ResponseEntity.ok(botConfigService.updateConfig(update));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getBotStatus() {
        return ResponseEntity.ok(Map.of(
            "running", botService.isRunning(),
            "mode", botService.getMode(),
            "capital", botService.getAccountCapital(),
            "todayRealizedPnl", botService.getTodayRealizedPnl(),
            "todayTradesCount", botService.getTodayTradesCount(),
            "dailyTarget", botService.getDailyProfitTarget(),
            "circuitBreakerLimit", botService.getDailyMaxLossLimit(),
            "recentLogs", botService.getBotLogs(),
            "breakoutQueue", botService.getBreakoutWatchlist()
        ));
    }

    @GetMapping("/breakout-queue")
    public ResponseEntity<List<Map<String, Object>>> getBreakoutQueue() {
        return ResponseEntity.ok(botService.getBreakoutWatchlist());
    }

    @PostMapping({"/setup-paper-trading", "/setup-paper"})
    public ResponseEntity<Map<String, Object>> setupPaperTrading(
            @RequestParam(required = false, defaultValue = "100000000") BigDecimal capital) {
        botService.setupPaperTrading(capital);
        return ResponseEntity.ok(Map.of(
            "status", "READY_FOR_LIVE_TRACE",
            "mode", botService.getMode(),
            "capital", botService.getAccountCapital(),
            "dailyTarget", botService.getDailyProfitTarget(),
            "circuitBreaker", botService.getDailyMaxLossLimit(),
            "running", botService.isRunning(),
            "message", String.format("🚀 Đã thiết lập hoàn tất tài khoản Live Trace %,.0f đ! Bot đã sẵn sàng trực canh phiên giao dịch Ngày 1 sáng mai (09:00).", botService.getAccountCapital())
        ));
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startBot() {
        botService.setRunning(true);
        return ResponseEntity.ok(Map.of(
            "running", botService.isRunning(),
            "message", "Bot đã kích hoạt chế độ tự động săn lệnh!"
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopBot() {
        botService.setRunning(false);
        return ResponseEntity.ok(Map.of(
            "running", botService.isRunning(),
            "message", "Bot đã tạm dừng."
        ));
    }

    @PostMapping("/reset-capital")
    public ResponseEntity<Map<String, Object>> resetCapital(
            @RequestParam(required = false, defaultValue = "100000000") BigDecimal capital) {
        botService.setAccountCapital(capital);
        return ResponseEntity.ok(Map.of(
            "capital", botService.getAccountCapital(),
            "dailyTarget", botService.getDailyProfitTarget(),
            "circuitBreaker", botService.getDailyMaxLossLimit(),
            "message", "Đã cập nhật số vốn thành công!"
        ));
    }

    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleBot(@RequestParam boolean enable) {
        botService.setRunning(enable);
        return ResponseEntity.ok(Map.of(
            "running", botService.isRunning(),
            "message", enable ? "Bot đã kích hoạt chế độ tự động săn lệnh!" : "Bot đã tạm dừng."
        ));
    }

    @PostMapping("/simulate-trade")
    public ResponseEntity<Trade> simulateTrade(@RequestParam(required = false, defaultValue = "FPT") String symbol) {
        Trade trade = botService.simulateDailyProfitTrade(symbol);
        return ResponseEntity.ok(trade);
    }

    @PostMapping("/trigger-cycle")
    public ResponseEntity<Map<String, Object>> triggerCycle() {
        botService.executeBotCycle();
        return ResponseEntity.ok(Map.of(
            "status", "CYCLE_EXECUTED",
            "todayRealizedPnl", botService.getTodayRealizedPnl(),
            "todayTradesCount", botService.getTodayTradesCount(),
            "recentLogs", botService.getBotLogs().stream().limit(5).toList()
        ));
    }

    @PostMapping("/simulate-tick")
    public ResponseEntity<Map<String, Object>> simulateTick() {
        return ResponseEntity.ok(simulationService.simulateMarketTick());
    }

    @PostMapping("/fast-forward-day")
    public ResponseEntity<Map<String, Object>> fastForwardDay() {
        return ResponseEntity.ok(simulationService.fastForwardTradingDay());
    }

    @PostMapping("/harvest/{symbol}")
    public ResponseEntity<Map<String, Object>> harvestProfit(@PathVariable String symbol) {
        return ResponseEntity.ok(simulationService.triggerDirectProfitHarvest(symbol));
    }

    @GetMapping("/signals")
    public ResponseEntity<List<com.vntrade.backend.dto.SignalScreenerDto>> getBotSignals() {
        return ResponseEntity.ok(vn30SignalScreenerService.screenVN30Signals());
    }
}
