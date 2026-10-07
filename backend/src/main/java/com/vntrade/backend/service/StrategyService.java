package com.vntrade.backend.service;

import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.dto.StockScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyService {

    private final StockPriceService stockPriceService;
    private final CandleDataService candleDataService;
    private final QuantitativeStrategyEngine quantitativeStrategyEngine;

    // Danh sách 12 mã tâm điểm VN30 & đầu ngành thanh khoản cao
    private static final List<String> FOCUS_SYMBOLS = List.of(
        "FPT", "HPG", "SSI", "TCB", "MWG", "VHM", "VCB", "MBB", "DGC", "STB", "VIX", "KBC"
    );

    /**
     * Quét toàn bộ cổ phiếu theo các mô hình định lượng VSA, Canslim và MA
     */
    public List<StockScanResult> scanAllStocks() {
        List<StockScanResult> results = new ArrayList<>();
        for (String symbol : FOCUS_SYMBOLS) {
            try {
                StockScanResult scan = evaluateSymbol(symbol);
                results.add(scan);
            } catch (Exception e) {
                log.error("Error evaluating symbol {}: {}", symbol, e.getMessage());
            }
        }
        return results;
    }

    public List<StockScanResult> scanByStrategy(String strategyType) {
        return scanAllStocks().stream()
            .filter(s -> strategyType == null || strategyType.equalsIgnoreCase("ALL") || s.getSignalType().equalsIgnoreCase(strategyType))
            .toList();
    }

    public StockScanResult evaluateSymbol(String symbol) {
        String sym = symbol.toUpperCase().trim();
        StockQuote quote = stockPriceService.getQuote(sym);
        try {
            var candles = candleDataService.getHistoricalCandles(sym, 80);
            return quantitativeStrategyEngine.evaluateRealTimeData(sym, quote, candles);
        } catch (Exception e) {
            log.warn("Falling back to basic evaluation for {}: {}", sym, e.getMessage());
            return quantitativeStrategyEngine.evaluateRealTimeData(sym, quote, List.of());
        }
    }
}
