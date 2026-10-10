package com.vntrade.backend.controller;

import com.vntrade.backend.dto.*;
import com.vntrade.backend.service.VN30FuturesBacktestService;
import com.vntrade.backend.service.VN30FuturesStrategyEngine;
import com.vntrade.backend.service.VN30FuturesTradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/futures")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class FuturesController {

    private final VN30FuturesStrategyEngine strategyEngine;
    private final VN30FuturesTradingService tradingService;
    private final VN30FuturesBacktestService backtestService;

    @GetMapping("/quote")
    public ResponseEntity<FuturesQuoteDto> getQuote() {
        return ResponseEntity.ok(strategyEngine.getCurrentQuote());
    }

    @GetMapping("/signal")
    public ResponseEntity<FuturesSignalDto> getSignal() {
        return ResponseEntity.ok(strategyEngine.generateSignal());
    }

    @GetMapping("/positions")
    public ResponseEntity<List<FuturesPositionDto>> getOpenPositions() {
        return ResponseEntity.ok(tradingService.getOpenPositions());
    }

    @GetMapping("/history")
    public ResponseEntity<List<FuturesPositionDto>> getPositionHistory() {
        return ResponseEntity.ok(tradingService.getPositionHistory());
    }

    @PostMapping("/order")
    public ResponseEntity<FuturesPositionDto> openPosition(
            @RequestParam String side,
            @RequestParam(required = false, defaultValue = "1") int contracts,
            @RequestParam(required = false) BigDecimal entryPrice,
            @RequestParam(required = false) BigDecimal stopLoss,
            @RequestParam(required = false) BigDecimal takeProfit,
            @RequestParam(required = false, defaultValue = "Đặt lệnh thủ công") String reason) {
        return ResponseEntity.ok(tradingService.openPosition(side, contracts, entryPrice, stopLoss, takeProfit, reason));
    }

    @PostMapping("/close/{id}")
    public ResponseEntity<FuturesPositionDto> closePosition(
            @PathVariable String id,
            @RequestParam(required = false) BigDecimal closePrice,
            @RequestParam(required = false, defaultValue = "Chốt thủ công") String reason) {
        FuturesPositionDto closed = tradingService.closePosition(id, closePrice, reason);
        if (closed == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(closed);
    }

    @PostMapping("/close-all")
    public ResponseEntity<Map<String, String>> closeAllPositions(
            @RequestParam(required = false, defaultValue = "Tất toán toàn bộ thủ công") String reason) {
        tradingService.closeAllPositions(reason);
        return ResponseEntity.ok(Map.of("message", "Đã đóng toàn bộ vị thế phái sinh thành công!"));
    }

    @GetMapping("/bot-status")
    public ResponseEntity<Map<String, Object>> getBotStatus() {
        return ResponseEntity.ok(Map.of(
            "config", tradingService.getBotConfig(),
            "logs", tradingService.getBotLogs(),
            "openPositionsCount", tradingService.getOpenPositions().size()
        ));
    }

    @PostMapping("/bot-toggle")
    public ResponseEntity<Map<String, Object>> toggleBot(@RequestParam boolean enable) {
        tradingService.setAutoTrading(enable);
        return ResponseEntity.ok(Map.of(
            "autoTrading", enable,
            "message", enable ? "Bot Phái Sinh đã BẬT săn lệnh tự động!" : "Bot Phái Sinh đã TẠM DỪNG!"
        ));
    }

    @PostMapping("/bot-config")
    public ResponseEntity<FuturesBotConfigDto> updateBotConfig(@RequestBody FuturesBotConfigDto update) {
        tradingService.updateBotConfig(update);
        return ResponseEntity.ok(tradingService.getBotConfig());
    }

    @GetMapping("/backtest")
    public ResponseEntity<FuturesBacktestResultDto> runBacktest(
            @RequestParam(required = false, defaultValue = "10") int days,
            @RequestParam(required = false, defaultValue = "2.5") double stopLoss,
            @RequestParam(required = false, defaultValue = "5.0") double takeProfit,
            @RequestParam(required = false, defaultValue = "1.5") double trailingStop) {
        return ResponseEntity.ok(backtestService.runBacktest(days, stopLoss, takeProfit, trailingStop));
    }
}
