package com.vntrade.backend.service;

import com.vntrade.backend.dto.MultiFactorAttributionDto;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MultiFactorAttributionServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private MultiFactorAttributionService factorAttributionService;

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
    void testCalculateFactorAttribution_FPT() {
        MultiFactorAttributionDto result = factorAttributionService.calculateFactorAttribution("FPT");

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertTrue(result.getJensensAlphaPercent().compareTo(BigDecimal.ZERO) > 0, "Alpha của FPT phải dương (+12.4%)");
        assertTrue(result.getQualityFactorRmw().compareTo(BigDecimal.ZERO) > 0, "Nhân tố chất lượng RMW phải rất cao");
        assertTrue(result.getMomentumFactorWml().compareTo(BigDecimal.ZERO) > 0, "Nhân tố đà tăng WML phải dương");
        assertEquals("QUALITY_AND_MOMENTUM_RUNNER", result.getPrimaryDriverFactor());
        assertTrue(result.getRSquaredPercent().doubleValue() >= 80.0, "Độ chuẩn xác mô hình R^2 phải >= 80%");
        assertFalse(result.getFactorStrengths().isEmpty());
        assertNotNull(result.getInstitutionalFactorVerdict());
    }
}
