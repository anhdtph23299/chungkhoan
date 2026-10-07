package com.vntrade.backend.service;

import com.vntrade.backend.dto.MarketRegimeDto;
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
public class MarketRegimeDetectionServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private MarketRegimeDetectionService regimeService;

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
    void testDetectMarketRegime_ConfirmedUptrend() {
        MarketRegimeDto regime = regimeService.detectMarketRegime();

        assertNotNull(regime);
        assertEquals("CONFIRMED_UPTREND", regime.getCurrentRegime());
        assertNotNull(regime.getRegimeDisplayName());
        assertEquals(100.0, regime.getRecommendedMaxExposure().doubleValue(), "Trong Uptrend cho phép tối đa 100% NAV");
        assertTrue(regime.isFollowThroughDayConfirmed());
        assertFalse(regime.getRegimeCharacteristics().isEmpty());
        assertNotNull(regime.getQuantOfficerVerdict());
    }
}
