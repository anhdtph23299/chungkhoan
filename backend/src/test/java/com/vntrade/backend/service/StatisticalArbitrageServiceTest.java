package com.vntrade.backend.service;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.StatisticalArbitragePairDto;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class StatisticalArbitrageServiceTest {

    @Mock
    private CandleDataService candleDataService;

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private StatisticalArbitrageService statisticalArbitrageService;

    @BeforeEach
    void setUp() {
        List<Candle> candlesA = new ArrayList<>();
        List<Candle> candlesB = new ArrayList<>();

        // Giả lập 60 phiên dữ liệu đồng pha có tính đồng liên kết (TCB và MBB)
        for (int i = 0; i < 60; i++) {
            double priceA = 24000.0 + Math.sin(i * 0.15) * 800.0 + (i * 20.0);
            double priceB = 22000.0 + Math.sin(i * 0.15) * 750.0 + (i * 18.0);

            candlesA.add(Candle.builder()
                    .symbol("TCB")
                    .date(LocalDate.of(2026, 1, 1).plusDays(i))
                    .close(BigDecimal.valueOf(priceA))
                    .build());

            candlesB.add(Candle.builder()
                    .symbol("MBB")
                    .date(LocalDate.of(2026, 1, 1).plusDays(i))
                    .close(BigDecimal.valueOf(priceB))
                    .build());
        }

        when(candleDataService.getHistoricalCandles(eq("TCB"), anyInt())).thenReturn(candlesA);
        when(candleDataService.getHistoricalCandles(eq("MBB"), anyInt())).thenReturn(candlesB);
        when(candleDataService.getHistoricalCandles(argThat(s -> !"TCB".equals(s) && !"MBB".equals(s)), anyInt())).thenReturn(candlesA);

        when(stockPriceService.getQuote(eq("TCB"))).thenReturn(StockQuote.builder().symbol("TCB").price(BigDecimal.valueOf(25000)).build());
        when(stockPriceService.getQuote(eq("MBB"))).thenReturn(StockQuote.builder().symbol("MBB").price(BigDecimal.valueOf(23000)).build());
    }

    @Test
    void testAnalyzePair_CalculatesMetricsCorrectly() {
        StatisticalArbitragePairDto result = statisticalArbitrageService.analyzePair("TCB", "MBB", "Banking");

        assertNotNull(result);
        assertEquals("TCB/MBB", result.getPairName());
        assertEquals("TCB", result.getStockA());
        assertEquals("MBB", result.getStockB());
        assertNotNull(result.getHedgeRatioBeta());
        assertTrue(result.getHedgeRatioBeta().compareTo(BigDecimal.ZERO) > 0, "Hedge ratio beta must be positive for cointegrated banking pair");
        assertNotNull(result.getCointegrationAdfPValue());
        assertNotNull(result.getSpreadZScore());
        assertNotNull(result.getHalfLifeDays());
        assertNotNull(result.getArbitrageSignal());
        assertNotNull(result.getExpectedNetEdgePercent());
        assertNotNull(result.getInstitutionalPairVerdict());
    }

    @Test
    void testAnalyzeAllArbitragePairs_ReturnsFullRoster() {
        List<StatisticalArbitragePairDto> pairs = statisticalArbitrageService.analyzeAllArbitragePairs();

        assertNotNull(pairs);
        assertFalse(pairs.isEmpty());
        assertTrue(pairs.size() >= 5, "Must scan all standard HOSE institutional pairs");
    }
}
