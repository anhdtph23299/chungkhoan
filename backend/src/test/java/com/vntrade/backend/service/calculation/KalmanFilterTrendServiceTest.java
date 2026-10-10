package com.vntrade.backend.service.calculation;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.KalmanFilterTrendDto;
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
public class KalmanFilterTrendServiceTest {

    @Mock
    private CandleDataService candleDataService;

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private KalmanFilterTrendService kalmanFilterTrendService;

    @BeforeEach
    void setUp() {
        List<Candle> candles = new ArrayList<>();
        BigDecimal base = BigDecimal.valueOf(130000);

        for (int i = 0; i < 60; i++) {
            BigDecimal close = base.add(BigDecimal.valueOf(i * 250 + Math.sin(i * 0.3) * 800));
            candles.add(Candle.builder()
                    .symbol("FPT")
                    .date(LocalDate.of(2026, 1, 1).plusDays(i))
                    .open(close.subtract(BigDecimal.valueOf(300)))
                    .high(close.add(BigDecimal.valueOf(600)))
                    .low(close.subtract(BigDecimal.valueOf(500)))
                    .close(close)
                    .volume(2500000L)
                    .build());
        }

        when(candleDataService.getHistoricalCandles(eq("FPT"), anyInt())).thenReturn(candles);
        when(stockPriceService.getQuote(eq("FPT"))).thenReturn(StockQuote.builder().symbol("FPT").price(BigDecimal.valueOf(145000)).build());
    }

    @Test
    void testAnalyzeKalmanTrend_ComputesZeroLagEstimates() {
        KalmanFilterTrendDto result = kalmanFilterTrendService.analyzeKalmanTrend("FPT");

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertNotNull(result.getMarketPrice());
        assertNotNull(result.getKalmanFilteredPrice());
        assertNotNull(result.getPriceVelocity());
        assertNotNull(result.getVelocityPercent());
        assertNotNull(result.getKalmanGain());
        assertTrue(result.getKalmanGain().compareTo(BigDecimal.ZERO) >= 0 && result.getKalmanGain().compareTo(BigDecimal.ONE) <= 0);
        assertNotNull(result.getTrendRegime());
        assertNotNull(result.getZeroLagSignal());
        assertNotNull(result.getSma20LagDifference());
        assertNotNull(result.getT25ExecutionVerdict());
        assertNotNull(result.getHistoricalPoints());
        assertFalse(result.getHistoricalPoints().isEmpty());

        // Verify structure of historical data point
        KalmanFilterTrendDto.KalmanDataPoint point = result.getHistoricalPoints().get(0);
        assertNotNull(point.getDate());
        assertNotNull(point.getActualClose());
        assertNotNull(point.getKalmanEstimate());
        assertNotNull(point.getVelocity());
        assertNotNull(point.getSma20Reference());
    }

    @Test
    void testAnalyzeKalmanTrend_ThrowsWhenInsufficientData() {
        when(candleDataService.getHistoricalCandles(eq("XYZ"), anyInt())).thenReturn(new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> kalmanFilterTrendService.analyzeKalmanTrend("XYZ"));
    }
}