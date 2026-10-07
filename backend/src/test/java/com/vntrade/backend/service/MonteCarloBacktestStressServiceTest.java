package com.vntrade.backend.service;

import com.vntrade.backend.dto.BacktestTrade;
import com.vntrade.backend.dto.InstitutionalBacktestResultDto;
import com.vntrade.backend.dto.MonteCarloBacktestStressDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class MonteCarloBacktestStressServiceTest {

    private InstitutionalBacktestService institutionalBacktestService;
    private MonteCarloBacktestStressService monteCarloBacktestStressService;

    @BeforeEach
    void setUp() {
        institutionalBacktestService = Mockito.mock(InstitutionalBacktestService.class);
        monteCarloBacktestStressService = new MonteCarloBacktestStressService(institutionalBacktestService);
    }

    @Test
    @DisplayName("Kiểm tra mô phỏng Monte Carlo Bootstrap 1,000 paths trên kết quả backtest định chế")
    void testRunMonteCarloStressTest() {
        List<BacktestTrade> trades = new ArrayList<>();
        trades.add(BacktestTrade.builder()
            .entryDate(LocalDate.now().minusDays(50))
            .exitDate(LocalDate.now().minusDays(40))
            .entryPrice(BigDecimal.valueOf(120000))
            .exitPrice(BigDecimal.valueOf(132000))
            .quantity(100)
            .pnl(BigDecimal.valueOf(1200000))
            .pnlPercent(BigDecimal.valueOf(10.0))
            .build());
        trades.add(BacktestTrade.builder()
            .entryDate(LocalDate.now().minusDays(30))
            .exitDate(LocalDate.now().minusDays(20))
            .entryPrice(BigDecimal.valueOf(130000))
            .exitPrice(BigDecimal.valueOf(140000))
            .quantity(100)
            .pnl(BigDecimal.valueOf(1000000))
            .pnlPercent(BigDecimal.valueOf(7.69))
            .build());

        InstitutionalBacktestResultDto btMock = InstitutionalBacktestResultDto.builder()
            .symbol("FPT")
            .strategyName("VCP_INSTITUTIONAL_BREAKOUT")
            .winRatePercent(BigDecimal.valueOf(100.0))
            .totalReturnPercent(BigDecimal.valueOf(1.15))
            .tradesHistory(trades)
            .build();

        when(institutionalBacktestService.runInstitutionalBacktest(
            anyString(), anyString(), anyInt(), any(BigDecimal.class), anyDouble(), anyDouble()
        )).thenReturn(btMock);

        MonteCarloBacktestStressDto result = monteCarloBacktestStressService.runMonteCarloStressTest(
            "FPT", "VCP_INSTITUTIONAL_BREAKOUT", 180, BigDecimal.valueOf(100_000_000)
        );

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertEquals("VCP_INSTITUTIONAL_BREAKOUT", result.getStrategyName());
        assertEquals(1000, result.getSimulationsCount());
        assertEquals(50, result.getTradesPerPath());
        assertNotNull(result.getMedianTerminalNav());
        assertTrue(result.getMedianTerminalNav().compareTo(BigDecimal.valueOf(100_000_000)) > 0);
        assertNotNull(result.getPercentile5thNav());
        assertNotNull(result.getPercentile95thNav());
        assertTrue(result.getPercentile95thNav().compareTo(result.getPercentile5thNav()) >= 0);
        assertNotNull(result.getWorstCaseDrawdown99thPercent());
        assertNotNull(result.getRequiredCapitalReserve());
        assertNotNull(result.getStressTestTier());
        assertNotNull(result.getQuantAuditVerdict());
        assertTrue(result.getQuantAuditVerdict().contains("STRESS-TEST MONTE CARLO"));
    }
}
