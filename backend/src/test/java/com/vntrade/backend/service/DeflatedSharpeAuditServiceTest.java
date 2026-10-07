package com.vntrade.backend.service;

import com.vntrade.backend.dto.BacktestTrade;
import com.vntrade.backend.dto.DeflatedSharpeAuditDto;
import com.vntrade.backend.dto.InstitutionalBacktestResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DeflatedSharpeAuditServiceTest {

    @Mock
    private InstitutionalBacktestService institutionalBacktestService;

    @InjectMocks
    private DeflatedSharpeAuditService deflatedSharpeAuditService;

    @BeforeEach
    void setUp() {
        InstitutionalBacktestResultDto mockResult = InstitutionalBacktestResultDto.builder()
            .symbol("FPT")
            .strategyName("VCP_INSTITUTIONAL_BREAKOUT")
            .sharpeRatio(BigDecimal.valueOf(1.85))
            .totalReturnPercent(BigDecimal.valueOf(28.5))
            .winRatePercent(BigDecimal.valueOf(65.0))
            .tradesHistory(List.of(
                BacktestTrade.builder().pnlPercent(BigDecimal.valueOf(6.5)).build(),
                BacktestTrade.builder().pnlPercent(BigDecimal.valueOf(-2.1)).build(),
                BacktestTrade.builder().pnlPercent(BigDecimal.valueOf(8.2)).build(),
                BacktestTrade.builder().pnlPercent(BigDecimal.valueOf(-1.8)).build(),
                BacktestTrade.builder().pnlPercent(BigDecimal.valueOf(5.4)).build(),
                BacktestTrade.builder().pnlPercent(BigDecimal.valueOf(11.0)).build(),
                BacktestTrade.builder().pnlPercent(BigDecimal.valueOf(-3.0)).build()
            ))
            .build();

        when(institutionalBacktestService.runInstitutionalBacktest(
            anyString(), anyString(), anyInt(), any(BigDecimal.class), anyDouble(), anyDouble()
        )).thenReturn(mockResult);
    }

    @Test
    void testAuditDeflatedSharpe_StrongEdge() {
        DeflatedSharpeAuditDto audit = deflatedSharpeAuditService.auditDeflatedSharpe(
            "FPT", "VCP_INSTITUTIONAL_BREAKOUT", 180, 50
        );

        assertNotNull(audit);
        assertEquals("FPT", audit.getSymbol());
        assertEquals(50, audit.getIndependentTrialsCount());
        assertNotNull(audit.getDeflatedSharpeRatio());
        assertTrue(audit.getDeflatedSharpeRatio().doubleValue() > 0.0);
        assertTrue(audit.getProbabilityOfBacktestOverfittingPercent().doubleValue() >= 0.0);
        assertTrue(audit.getMinimumTrackRecordLengthDays() > 0);
        assertNotNull(audit.getStatisticalConfidenceGrade());
        assertNotNull(audit.getInstitutionalAuditSummary());
        assertTrue(audit.getInstitutionalAuditSummary().contains("KIỂM TOÁN") || audit.getInstitutionalAuditSummary().contains("ĐỘ TIN CẬY"));
    }

    @Test
    void testNormalCdfAndErf() {
        // CDF(0) = 0.5
        assertEquals(0.5, deflatedSharpeAuditService.normalCdf(0.0), 1e-4);
        // CDF(1.96) ~ 0.975
        assertEquals(0.975, deflatedSharpeAuditService.normalCdf(1.95996), 1e-3);
        // InverseNormalCdf(0.5) = 0.0
        assertEquals(0.0, deflatedSharpeAuditService.inverseNormalCdf(0.5), 1e-3);
        // InverseNormalCdf(0.975) ~ 1.96
        assertEquals(1.96, deflatedSharpeAuditService.inverseNormalCdf(0.975), 1e-2);
    }
}
