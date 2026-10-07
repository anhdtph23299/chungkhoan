package com.vntrade.backend.service;

import com.vntrade.backend.dto.BacktestRequest;
import com.vntrade.backend.dto.BacktestResult;
import com.vntrade.backend.dto.OptimizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class StrategyOptimizerServiceTest {

    private BacktestService backtestService;
    private StrategyOptimizerService optimizerService;

    @BeforeEach
    void setUp() {
        backtestService = Mockito.mock(BacktestService.class);
        optimizerService = new StrategyOptimizerService(backtestService);
    }

    @Test
    @DisplayName("Kiểm tra tối ưu hóa tham số tìm ra cấu hình lợi nhuận cao nhất")
    void testOptimizeParameters() {
        BacktestResult sampleResult = BacktestResult.builder()
            .symbol("FPT")
            .strategy("VN30_ENSEMBLE")
            .initialCapital(BigDecimal.valueOf(100_000_000))
            .finalCapital(BigDecimal.valueOf(108_500_000))
            .netProfit(BigDecimal.valueOf(8_500_000))
            .totalReturnPercent(BigDecimal.valueOf(8.5))
            .totalTrades(4)
            .winningTrades(3)
            .losingTrades(1)
            .winRate(BigDecimal.valueOf(75.0))
            .profitFactor(BigDecimal.valueOf(4.2))
            .maxDrawdownPercent(BigDecimal.valueOf(2.5))
            .sharpeRatio(BigDecimal.valueOf(2.1))
            .tradesHistory(List.of())
            .build();

        when(backtestService.runBacktest(any(BacktestRequest.class))).thenReturn(sampleResult);

        OptimizationResult opt = optimizerService.optimizeParameters("FPT", 120);

        assertNotNull(opt);
        assertEquals("FPT", opt.getSymbol());
        assertEquals("VN30_ENSEMBLE", opt.getOptimalStrategy());
        assertEquals(0, BigDecimal.valueOf(8_500_000).compareTo(opt.getMaxNetProfit()));
        assertEquals(0, BigDecimal.valueOf(75.0).compareTo(opt.getOptimalWinRate()));
        assertNotNull(opt.getRecommendation());
        assertFalse(opt.getTopPerformingConfigurations().isEmpty());
    }
}
