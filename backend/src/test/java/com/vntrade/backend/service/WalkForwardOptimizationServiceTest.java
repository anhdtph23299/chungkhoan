package com.vntrade.backend.service;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.WalkForwardOptimizationDto;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WalkForwardOptimizationServiceTest {

    @Mock
    private CandleDataService candleDataService;

    @Mock
    private TechnicalIndicatorService technicalIndicatorService;

    @InjectMocks
    private WalkForwardOptimizationService walkForwardService;

    @BeforeEach
    void setUp() {
        List<Candle> mockCandles = new ArrayList<>();
        List<BigDecimal> mockSma = new ArrayList<>();
        BigDecimal base = BigDecimal.valueOf(100000);

        for (int i = 0; i < 150; i++) {
            BigDecimal close = base.add(BigDecimal.valueOf(Math.sin(i * 0.2) * 5000 + (i * 100)));
            mockCandles.add(Candle.builder()
                .symbol("FPT")
                .date(LocalDate.of(2026, 1, 1).plusDays(i))
                .open(close.subtract(BigDecimal.valueOf(500)))
                .high(close.add(BigDecimal.valueOf(1000)))
                .low(close.subtract(BigDecimal.valueOf(1000)))
                .close(close)
                .volume(2000000L)
                .build());
            mockSma.add(close.subtract(BigDecimal.valueOf(200)));
        }

        when(candleDataService.getHistoricalCandles(anyString(), anyInt())).thenReturn(mockCandles);
        when(technicalIndicatorService.calculateSMA(any(), anyInt())).thenReturn(mockSma);
    }

    @Test
    void testRunWalkForwardAnalysis() {
        WalkForwardOptimizationDto result = walkForwardService.runWalkForwardAnalysis(
            "FPT", "VCP_INSTITUTIONAL_BREAKOUT", 150, 4
        );

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertEquals("VCP_INSTITUTIONAL_BREAKOUT", result.getStrategyName());
        assertEquals(150, result.getTotalHistoricalCandles());
        assertEquals(4, result.getTotalWalkForwardWindows());
        assertTrue(result.isT25SettlementEnforced(), "Bắt buộc tuân thủ điều kiện thanh toán T+2.5");
        assertEquals(0, result.getTotalRoundtripTaxAndFeesPercent().compareTo(BigDecimal.valueOf(0.40)), "Thuế phí 0.40%");
        assertNotNull(result.getRobustnessGrade());
        assertNotNull(result.getInstitutionalAuditVerdict());
        assertFalse(result.getWindows().isEmpty());
        assertEquals(4, result.getWindows().size(), "Phải chia thành 4 cửa sổ kiểm định độc lập");

        for (var win : result.getWindows()) {
            assertNotNull(win.getInSampleReturnPercent());
            assertNotNull(win.getOutOfSampleReturnPercent());
            assertNotNull(win.getWindowWfePercent());
            assertNotNull(win.getWindowVerdict());
        }
    }
}
