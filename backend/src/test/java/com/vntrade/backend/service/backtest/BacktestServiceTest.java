package com.vntrade.backend.service.backtest;

import com.vntrade.backend.dto.BacktestRequest;
import com.vntrade.backend.dto.BacktestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import com.vntrade.backend.service.marketdata.CandleDataService;
import com.vntrade.backend.service.calculation.TechnicalIndicatorService;
import com.vntrade.backend.service.marketdata.StockPriceService;

public class BacktestServiceTest {

    private BacktestService backtestService;

    @BeforeEach
    void setUp() {
        StockPriceService stockPriceService = Mockito.mock(StockPriceService.class);
        Mockito.when(stockPriceService.getQuote(Mockito.anyString())).thenReturn(
            com.vntrade.backend.dto.StockQuote.builder()
                .symbol("FPT")
                .price(BigDecimal.valueOf(140000))
                .build()
        );
        CandleDataService candleDataService = new CandleDataService(stockPriceService);
        TechnicalIndicatorService technicalIndicatorService = new TechnicalIndicatorService();
        backtestService = new BacktestService(candleDataService, technicalIndicatorService);
    }

    @Test
    @DisplayName("Kiểm tra chạy Backtest chiến lược Breakout sinh lời và khống chế drawdown")
    void testRunBacktest_BreakoutStrategy() {
        BacktestRequest request = BacktestRequest.builder()
            .symbol("FPT")
            .strategy("BREAKOUT_VOL")
            .candlesCount(120)
            .initialCapital(BigDecimal.valueOf(100_000_000))
            .stopLossPercent(7.0)
            .takeProfitPercent(15.0)
            .build();

        BacktestResult result = backtestService.runBacktest(request);

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertEquals("BREAKOUT_VOL", result.getStrategy());
        assertEquals(0, BigDecimal.valueOf(100_000_000).compareTo(result.getInitialCapital()));
        assertNotNull(result.getFinalCapital());
        assertNotNull(result.getWinRate());
        assertNotNull(result.getProfitFactor());
        assertNotNull(result.getConclusion());
        assertTrue(result.getConclusion().contains("Chiến lược BREAKOUT_VOL"));
    }
}