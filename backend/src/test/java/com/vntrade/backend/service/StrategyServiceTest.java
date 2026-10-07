package com.vntrade.backend.service;

import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.dto.StockScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class StrategyServiceTest {

    private StockPriceService stockPriceService;
    private CandleDataService candleDataService;
    private QuantitativeStrategyEngine quantitativeStrategyEngine;
    private StrategyService strategyService;

    @BeforeEach
    void setUp() {
        stockPriceService = Mockito.mock(StockPriceService.class);
        candleDataService = Mockito.mock(CandleDataService.class);
        TechnicalIndicatorService indicatorService = new TechnicalIndicatorService();
        quantitativeStrategyEngine = new QuantitativeStrategyEngine(indicatorService);
        strategyService = new StrategyService(stockPriceService, candleDataService, quantitativeStrategyEngine);
    }

    @Test
    @DisplayName("Kiểm tra quét cổ phiếu FPT nhận diện tín hiệu Breakout kèm Volume")
    void testEvaluateSymbol_FptBreakout() {
        StockQuote mockQuote = StockQuote.builder()
            .symbol("FPT")
            .price(BigDecimal.valueOf(141000))
            .change(BigDecimal.valueOf(3500))
            .changePercent(BigDecimal.valueOf(2.55))
            .volume(5_000_000L)
            .exchange("HOSE")
            .build();

        List<com.vntrade.backend.dto.Candle> candles = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            double base = 130000 + (i < 50 ? (i % 2 == 0 ? 400 : -200) : (i - 50) * 1100);
            candles.add(com.vntrade.backend.dto.Candle.builder()
                .date(java.time.LocalDate.now().minusDays(60 - i))
                .open(BigDecimal.valueOf(base - 100))
                .high(BigDecimal.valueOf(base + 400))
                .low(BigDecimal.valueOf(base - 300))
                .close(BigDecimal.valueOf(base))
                .volume(i == 59 ? 5_000_000L : 2_000_000L)
                .build());
        }

        when(stockPriceService.getQuote("FPT")).thenReturn(mockQuote);
        when(candleDataService.getHistoricalCandles("FPT", 80)).thenReturn(candles);

        StockScanResult result = strategyService.evaluateSymbol("FPT");

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertEquals("BREAKOUT_VOL", result.getSignalType());
        assertEquals("STRONG_BUY", result.getAction());
        assertTrue(result.getConfidenceScore() >= 80);
        assertTrue(result.getStopLoss().compareTo(result.getPrice()) < 0);
        assertTrue(result.getTargetPrice().compareTo(result.getPrice()) > 0);
    }

    @Test
    @DisplayName("Kiểm tra quét toàn bộ 12 mã trọng điểm thị trường VN")
    void testScanAllStocks() {
        StockQuote defaultQuote = StockQuote.builder()
            .price(BigDecimal.valueOf(30000))
            .change(BigDecimal.valueOf(500))
            .changePercent(BigDecimal.valueOf(1.67))
            .build();

        when(stockPriceService.getQuote(anyString())).thenReturn(defaultQuote);

        List<StockScanResult> results = strategyService.scanAllStocks();

        assertNotNull(results);
        assertEquals(12, results.size());
    }
}
