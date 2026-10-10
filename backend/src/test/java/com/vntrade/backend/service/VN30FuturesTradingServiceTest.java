package com.vntrade.backend.service;

import com.vntrade.backend.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VN30FuturesTradingServiceTest {

    private VN30FuturesCandleService candleService;
    private VN30FuturesStrategyEngine strategyEngine;
    private VN30FuturesTradingService tradingService;
    private VN30FuturesBacktestService backtestService;

    @BeforeEach
    void setUp() {
        candleService = new VN30FuturesCandleService();
        strategyEngine = new VN30FuturesStrategyEngine(candleService);
        tradingService = new VN30FuturesTradingService(strategyEngine);
        backtestService = new VN30FuturesBacktestService(candleService);
    }

    @Test
    void testTickSizeRounding() {
        BigDecimal p1 = BigDecimal.valueOf(1877.24);
        BigDecimal rounded1 = VN30FuturesStrategyEngine.roundToFuturesTick(p1);
        assertEquals(new BigDecimal("1877.2"), rounded1);

        BigDecimal p2 = BigDecimal.valueOf(1877.26);
        BigDecimal rounded2 = VN30FuturesStrategyEngine.roundToFuturesTick(p2);
        assertEquals(new BigDecimal("1877.3"), rounded2);
    }

    @Test
    void testOpenAndCloseLongPosition() {
        // Mở vị thế LONG 2 HĐ @ 1870.0
        FuturesPositionDto pos = tradingService.openPosition("LONG", 2, BigDecimal.valueOf(1870.0),
            BigDecimal.valueOf(1867.5), BigDecimal.valueOf(1875.0), "Test Long");

        assertNotNull(pos);
        assertEquals("LONG", pos.getSide());
        assertEquals(2, pos.getContracts());
        assertEquals(new BigDecimal("1870.0"), pos.getEntryPrice());
        assertEquals(new BigDecimal("1867.5"), pos.getStopLossPrice());
        assertEquals(new BigDecimal("1875.0"), pos.getTakeProfitPrice());

        // Đóng vị thế @ 1874.5 (+4.5 điểm)
        FuturesPositionDto closed = tradingService.closePosition(pos.getId(), BigDecimal.valueOf(1874.5), "TP Test");
        assertNotNull(closed);
        assertEquals("CLOSED", closed.getStatus());
        assertEquals(new BigDecimal("4.5"), closed.getPnlPoints());

        // Lãi gộp = 4.5 * 100.000 * 2 = 900.000 đ
        assertEquals(new BigDecimal("900000"), closed.getGrossPnlVnd());
        // Phí 2 HĐ = 9.400 * 2 = 18.800 đ
        assertEquals(new BigDecimal("18800"), closed.getFeesVnd());
        // Lãi ròng = 900.000 - 18.800 = 881.200 đ
        assertEquals(new BigDecimal("881200"), closed.getNetPnlVnd());
    }

    @Test
    void testOpenAndCloseShortPosition() {
        // Mở vị thế SHORT 2 HĐ @ 1880.0
        FuturesPositionDto pos = tradingService.openPosition("SHORT", 2, BigDecimal.valueOf(1880.0),
            BigDecimal.valueOf(1882.5), BigDecimal.valueOf(1875.0), "Test Short");

        assertNotNull(pos);
        assertEquals("SHORT", pos.getSide());

        // Đóng vị thế khi giá giảm về 1876.0 (+4.0 điểm lời Short)
        FuturesPositionDto closed = tradingService.closePosition(pos.getId(), BigDecimal.valueOf(1876.0), "Short TP");
        assertNotNull(closed);
        assertEquals(new BigDecimal("4.0"), closed.getPnlPoints());

        // Lãi gộp = 4.0 * 100.000 * 2 = 800.000 đ
        assertEquals(new BigDecimal("800000"), closed.getGrossPnlVnd());
        assertEquals(new BigDecimal("781200"), closed.getNetPnlVnd());
    }

    @Test
    void testQuoteAndBasis() {
        FuturesQuoteDto quote = strategyEngine.getCurrentQuote();
        assertNotNull(quote);
        assertEquals("VN30F1M", quote.getSymbol());
        assertNotNull(quote.getCurrentPrice());
        assertNotNull(quote.getVn30IndexPrice());
        assertNotNull(quote.getBasis());
        assertNotNull(quote.getBasisStatus());
    }

    @Test
    void testSignalGeneration() {
        FuturesSignalDto signal = strategyEngine.generateSignal();
        assertNotNull(signal);
        assertEquals("VN30F1M", signal.getSymbol());
        assertNotNull(signal.getAction());
        assertTrue(List.of("LONG", "SHORT", "STANDBY").contains(signal.getAction()));
        assertNotNull(signal.getConfidenceScore());
    }

    @Test
    void testBacktestExecution() {
        FuturesBacktestResultDto res = backtestService.runBacktest(5, 2.5, 5.0, 1.5);
        assertNotNull(res);
        assertEquals("VN30F1M", res.getSymbol());
        assertNotNull(res.getTotalTrades());
        assertNotNull(res.getWinRatePercent());
        assertNotNull(res.getTotalNetPnlVnd());
    }
}
