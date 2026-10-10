package com.vntrade.backend.service.calculation;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.GarchVolatilityForecastDto;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.marketdata.CandleDataService;
import com.vntrade.backend.service.marketdata.StockPriceService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class GarchVolatilityForecastServiceTest {

    @Mock
    private CandleDataService candleDataService;

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private GarchVolatilityForecastService garchService;

    @BeforeEach
    void setUp() {
        List<Candle> mockCandles = new ArrayList<>();
        BigDecimal base = BigDecimal.valueOf(100000);

        for (int i = 0; i < 60; i++) {
            BigDecimal close = base.add(BigDecimal.valueOf(Math.sin(i * 0.25) * 4000 + (i * 150)));
            mockCandles.add(Candle.builder()
                    .symbol("FPT")
                    .date(LocalDate.of(2026, 1, 1).plusDays(i))
                    .close(close)
                    .open(close.subtract(BigDecimal.valueOf(200)))
                    .high(close.add(BigDecimal.valueOf(500)))
                    .low(close.subtract(BigDecimal.valueOf(500)))
                    .volume(2000000L)
                    .build());
        }

        when(candleDataService.getHistoricalCandles(eq("FPT"), anyInt())).thenReturn(mockCandles);
        when(stockPriceService.getQuote(eq("FPT"))).thenReturn(StockQuote.builder().symbol("FPT").price(BigDecimal.valueOf(110000)).build());
    }

    @Test
    void testForecastVolatility_CalculatesGarchMetrics() {
        GarchVolatilityForecastDto result = garchService.forecastVolatility("FPT");

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertNotNull(result.getHistoricalVolAnnualized());
        assertTrue(result.getHistoricalVolAnnualized().compareTo(BigDecimal.ZERO) > 0);

        assertNotNull(result.getConditionalVolCurrent());
        assertTrue(result.getConditionalVolCurrent().compareTo(BigDecimal.ZERO) > 0);

        assertNotNull(result.getPersistenceAlphaPlusBeta());
        assertTrue(result.getPersistenceAlphaPlusBeta().compareTo(BigDecimal.ONE) < 0, "GARCH persistence must be strictly less than 1.0 for stationarity");

        assertNotNull(result.getForecastTPlus1VolAnnualized());
        assertNotNull(result.getForecastTPlus2VolAnnualized());
        assertNotNull(result.getForecastT25SettlementVolPercent());

        assertNotNull(result.getDynamicT25StopLossPercent());
        assertTrue(result.getDynamicT25StopLossPercent().compareTo(BigDecimal.valueOf(4.0)) >= 0, "Stop loss must respect floor");
        assertTrue(result.getDynamicT25StopLossPercent().compareTo(BigDecimal.valueOf(9.0)) <= 0, "Stop loss must respect ceiling");

        assertNotNull(result.getVolatilityRegime());
        assertNotNull(result.getInstitutionalRiskAction());

        assertNotNull(result.getHistoricalVolSeries());
        assertFalse(result.getHistoricalVolSeries().isEmpty());
    }

    @Test
    void testForecastVolatility_ThrowsWhenInsufficientCandles() {
        when(candleDataService.getHistoricalCandles(eq("XYZ"), anyInt())).thenReturn(new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> garchService.forecastVolatility("XYZ"));
    }
}