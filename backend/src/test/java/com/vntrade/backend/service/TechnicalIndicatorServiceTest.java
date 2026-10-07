package com.vntrade.backend.service;

import com.vntrade.backend.dto.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TechnicalIndicatorServiceTest {

    private TechnicalIndicatorService indicatorService;

    @BeforeEach
    void setUp() {
        indicatorService = new TechnicalIndicatorService();
    }

    @Test
    @DisplayName("Kiểm tra tính toán SMA 3 kỳ chính xác")
    void testCalculateSMA() {
        List<Candle> candles = new ArrayList<>();
        double[] closes = {10.0, 20.0, 30.0, 40.0, 50.0};
        for (int i = 0; i < closes.length; i++) {
            candles.add(Candle.builder()
                .date(LocalDate.now().plusDays(i))
                .close(BigDecimal.valueOf(closes[i]))
                .build()
            );
        }

        List<BigDecimal> sma3 = indicatorService.calculateSMA(candles, 3);
        assertEquals(5, sma3.size());
        assertNull(sma3.get(0));
        assertNull(sma3.get(1));
        // (10 + 20 + 30) / 3 = 20.0
        assertEquals(0, BigDecimal.valueOf(20.0).compareTo(sma3.get(2)));
        // (20 + 30 + 40) / 3 = 30.0
        assertEquals(0, BigDecimal.valueOf(30.0).compareTo(sma3.get(3)));
        // (30 + 40 + 50) / 3 = 40.0
        assertEquals(0, BigDecimal.valueOf(40.0).compareTo(sma3.get(4)));
    }

    @Test
    @DisplayName("Kiểm tra tính toán RSI 14 trong biên độ 0 - 100")
    void testCalculateRSI() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            candles.add(Candle.builder()
                .date(LocalDate.now().plusDays(i))
                .close(BigDecimal.valueOf(20.0 + (i % 5)))
                .build()
            );
        }

        List<BigDecimal> rsi = indicatorService.calculateRSI(candles, 14);
        assertNotNull(rsi);
        for (BigDecimal val : rsi) {
            if (val != null) {
                assertTrue(val.doubleValue() >= 0 && val.doubleValue() <= 100);
            }
        }
    }

    @Test
    @DisplayName("Kiểm tra tính toán MACD (12, 26, 9) và Histogram")
    void testCalculateMACD() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            candles.add(Candle.builder()
                .date(LocalDate.now().plusDays(i))
                .close(BigDecimal.valueOf(50.0 + (i * 0.5)))
                .build()
            );
        }

        var macd = indicatorService.calculateMACD(candles, 12, 26, 9);
        assertEquals(40, macd.size());
        var lastPoint = macd.get(39);
        assertNotNull(lastPoint);
        assertNotNull(lastPoint.getMacd());
        assertNotNull(lastPoint.getSignal());
        assertNotNull(lastPoint.getHistogram());
    }

    @Test
    @DisplayName("Kiểm tra tính toán Bollinger Bands (20, 2) Upper >= Middle >= Lower")
    void testCalculateBollingerBands() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            candles.add(Candle.builder()
                .date(LocalDate.now().plusDays(i))
                .close(BigDecimal.valueOf(100.0 + (i % 4)))
                .build()
            );
        }

        var bb = indicatorService.calculateBollingerBands(candles, 20, 2.0);
        assertEquals(30, bb.size());
        var lastBb = bb.get(29);
        assertNotNull(lastBb);
        assertTrue(lastBb.getUpper().compareTo(lastBb.getMiddle()) >= 0);
        assertTrue(lastBb.getMiddle().compareTo(lastBb.getLower()) >= 0);
    }
}

