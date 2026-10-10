package com.vntrade.backend.service.calculation;

import com.vntrade.backend.dto.PnLLedgerItemDto;
import com.vntrade.backend.dto.TradingRatiosDto;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AdvancedTradingAnalyticsServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private AdvancedTradingAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        Trade t1 = Trade.builder()
            .id(1L)
            .symbol("FPT")
            .exchange("HOSE")
            .status("closed")
            .price(BigDecimal.valueOf(130000))
            .closePrice(BigDecimal.valueOf(145000))
            .quantity(500)
            .pnl(BigDecimal.valueOf(7500000))
            .pnlPercent(BigDecimal.valueOf(11.54))
            .tradeDate(LocalDate.now().minusDays(5))
            .closeDate(LocalDate.now())
            .fee(BigDecimal.valueOf(100000))
            .strategy("Breakout")
            .build();

        Trade t2 = Trade.builder()
            .id(2L)
            .symbol("HPG")
            .exchange("HOSE")
            .status("closed")
            .price(BigDecimal.valueOf(28000))
            .closePrice(BigDecimal.valueOf(31000))
            .quantity(1000)
            .pnl(BigDecimal.valueOf(3000000))
            .pnlPercent(BigDecimal.valueOf(10.71))
            .tradeDate(LocalDate.now().minusDays(3))
            .closeDate(LocalDate.now())
            .fee(BigDecimal.valueOf(45000))
            .strategy("Pullback MA20")
            .build();

        when(tradeRepository.findByStatusOrderByTradeDateDesc("closed")).thenReturn(List.of(t1, t2));
        when(tradeRepository.findAllByOrderByTradeDateDesc()).thenReturn(List.of(t1, t2));
    }

    @Test
    void testComputeInstitutionalRatios() {
        TradingRatiosDto ratios = analyticsService.computeInstitutionalRatios();

        assertNotNull(ratios);
        assertEquals(2, ratios.getTotalTradesAnalyzed());
        assertEquals(BigDecimal.valueOf(100.0).setScale(2), ratios.getWinRate());
        assertTrue(ratios.getSharpeRatio().doubleValue() > 1.5, "Sharpe ratio phải cao khi win rate 100%");
        assertTrue(ratios.getSortinoRatio().doubleValue() > 2.0, "Sortino ratio phải cao khi không có lỗ");
        assertNotNull(ratios.getHedgeFundRating());
    }

    @Test
    void testGetFullPnLLedger() {
        List<PnLLedgerItemDto> ledger = analyticsService.getFullPnLLedger();

        assertNotNull(ledger);
        assertEquals(2, ledger.size());
        assertEquals("FPT", ledger.get(0).getSymbol());
        assertTrue(ledger.get(0).getNetProfit().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(ledger.get(0).getFeeDeducted());
        assertNotNull(ledger.get(0).getTaxDeducted());
    }
}
