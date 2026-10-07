package com.vntrade.backend.service;

import com.vntrade.backend.dto.TradeAnalytics;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class JournalServiceTest {

    private TradeRepository tradeRepository;
    private JournalService journalService;

    @BeforeEach
    void setUp() {
        tradeRepository = Mockito.mock(TradeRepository.class);
        journalService = new JournalService(tradeRepository);
    }

    @Test
    @DisplayName("Kiểm tra tính toán Win Rate, Profit Factor và Kỳ Vọng Toán Học")
    void testComputeAnalytics_ProfitableSystem() {
        // Mock 4 lệnh: 3 thắng, 1 thua
        List<Trade> trades = List.of(
            Trade.builder()
                .symbol("FPT")
                .status("closed")
                .tradeDate(LocalDate.now().minusDays(30))
                .pnl(BigDecimal.valueOf(6_000_000))
                .pnlPercent(BigDecimal.valueOf(15.0))
                .strategy("Breakout")
                .build(),
            Trade.builder()
                .symbol("HPG")
                .status("closed")
                .tradeDate(LocalDate.now().minusDays(20))
                .pnl(BigDecimal.valueOf(4_000_000))
                .pnlPercent(BigDecimal.valueOf(12.0))
                .strategy("Pullback")
                .build(),
            Trade.builder()
                .symbol("SSI")
                .status("closed")
                .tradeDate(LocalDate.now().minusDays(10))
                .pnl(BigDecimal.valueOf(5_000_000))
                .pnlPercent(BigDecimal.valueOf(14.0))
                .strategy("Breakout")
                .build(),
            Trade.builder()
                .symbol("VND")
                .status("closed")
                .tradeDate(LocalDate.now().minusDays(5))
                .pnl(BigDecimal.valueOf(-2_000_000))
                .pnlPercent(BigDecimal.valueOf(-6.5))
                .strategy("Bắt đáy")
                .build()
        );

        when(tradeRepository.findByStatusOrderByTradeDateDesc("closed")).thenReturn(trades);

        TradeAnalytics analytics = journalService.computeAnalytics();

        assertNotNull(analytics);
        assertEquals(4, analytics.getTotalTrades());
        assertEquals(3, analytics.getWinningTrades());
        assertEquals(1, analytics.getLosingTrades());
        // Win rate: 3/4 = 75%
        assertEquals(0, BigDecimal.valueOf(75.0).compareTo(analytics.getWinRate()));
        // Profit factor: (6m + 4m + 5m) / 2m = 15m / 2m = 7.5
        assertEquals(0, BigDecimal.valueOf(7.5).compareTo(analytics.getProfitFactor()));
        // Net profit: 15m - 2m = 13m
        assertEquals(0, BigDecimal.valueOf(13_000_000).compareTo(analytics.getNetProfit()));
        // Expectancy: (0.75 * 5m) - (0.25 * 2m) = 3.75m - 0.5m = 3.25m > 0
        assertTrue(analytics.getMathematicalExpectancy().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(analytics.getAverageMfePercent());
        assertNotNull(analytics.getAverageMaePercent());
        assertNotNull(analytics.getExitEfficiencyPercent());
    }
}
