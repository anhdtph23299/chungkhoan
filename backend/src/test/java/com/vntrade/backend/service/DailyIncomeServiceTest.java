package com.vntrade.backend.service;

import com.vntrade.backend.dto.DailyIncomeDto;
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

class DailyIncomeServiceTest {

    private TradeRepository tradeRepository;
    private StockPriceService stockPriceService;
    private DailyIncomeService dailyIncomeService;

    @BeforeEach
    void setUp() {
        tradeRepository = Mockito.mock(TradeRepository.class);
        stockPriceService = Mockito.mock(StockPriceService.class);
        dailyIncomeService = new DailyIncomeService(tradeRepository, stockPriceService);
    }

    @Test
    @DisplayName("Kiểm tra báo cáo kiếm tiền hàng ngày: Tính đúng lãi ròng, tỷ lệ đạt mục tiêu, trích 30% tiền mặt rút ra")
    void testGetTodayIncomeReport() {
        LocalDate today = LocalDate.now();

        // 1 lệnh chốt lời hôm nay kiếm được 2.100.000 đ
        Trade closedTrade = Trade.builder()
            .id(1L)
            .symbol("FPT")
            .status("closed")
            .closeDate(today)
            .price(BigDecimal.valueOf(132000))
            .closePrice(BigDecimal.valueOf(146000))
            .quantity(200)
            .pnl(BigDecimal.valueOf(2_100_000))
            .pnlPercent(BigDecimal.valueOf(10.6))
            .build();

        when(tradeRepository.findAllByOrderByTradeDateDesc()).thenReturn(List.of(closedTrade));
        when(tradeRepository.findByStatusOrderByTradeDateDesc("open")).thenReturn(List.of());

        DailyIncomeDto report = dailyIncomeService.getTodayIncomeReport();

        assertNotNull(report);
        assertEquals(today, report.getReportDate());
        assertEquals(0, report.getDailyRealizedProfit().compareTo(BigDecimal.valueOf(2_100_000)));

        // Mục tiêu 1.500.000 đ -> Đạt 140%
        assertEquals(0, report.getTargetAchievementPercent().compareTo(BigDecimal.valueOf(140.0)));

        // 70% tái đầu tư = 1.470.000 đ
        assertEquals(0, report.getReinvestmentCapital().compareTo(BigDecimal.valueOf(1_470_000)));

        // 30% rút tiêu dùng hàng ngày = 630.000 đ
        assertEquals(0, report.getWithdrawableIncome().compareTo(BigDecimal.valueOf(630_000)));

        // Tỷ lệ thắng hôm nay = 100%
        assertEquals(0, report.getWinRateToday().compareTo(BigDecimal.valueOf(100.0)));
        assertTrue(report.getDailyStatusMessage().contains("ĐẠT CHỈ TIÊU NGÀY"));
    }

    @Test
    @DisplayName("Kiểm tra lịch sử thu nhập nhiều ngày")
    void testGetIncomeHistory() {
        List<DailyIncomeDto> history = dailyIncomeService.getIncomeHistory(7);
        assertEquals(7, history.size());
        assertTrue(history.get(0).getDailyTarget().compareTo(BigDecimal.ZERO) > 0);
    }
}
