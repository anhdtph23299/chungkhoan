package com.vntrade.backend.service.risk;

import com.vntrade.backend.dto.ForeignFlowRiskDto;
import com.vntrade.backend.dto.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.marketdata.StockPriceService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ForeignFlowRiskServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private ForeignFlowRiskService foreignFlowRiskService;

    @BeforeEach
    void setUp() {
        StockQuote quote = StockQuote.builder()
            .symbol("FPT")
            .price(BigDecimal.valueOf(140000))
            .volume(4000000L)
            .changePercent(BigDecimal.valueOf(1.5))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);
    }

    @Test
    void testEvaluateForeignFlowRisk_FPT() {
        ForeignFlowRiskDto report = foreignFlowRiskService.evaluateForeignFlowRisk("FPT");

        assertNotNull(report);
        assertEquals("FPT", report.getSymbol());
        assertTrue(report.getForeignNetValueVnd().compareTo(BigDecimal.ZERO) > 0, "Giá trị ròng khối ngoại FPT phải dương");
        assertEquals(0, BigDecimal.valueOf(49.0).compareTo(report.getForeignOwnershipPercent()), "FPT kín room ngoại 49%");
        assertEquals("LOW", report.getSlippageRiskIndex());
        assertEquals("STRONG_FOREIGN_NET_ACCUMULATION", report.getFiiFlowVerdict());
        assertTrue(report.getLiquidityHealthScore() >= 90);
        assertFalse(report.getEarlyWarningAlerts().isEmpty());
        assertNotNull(report.getInstitutionalRecommendation());
    }
}