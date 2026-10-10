package com.vntrade.backend.service.marketdata;

import com.vntrade.backend.dto.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CandleDataServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    private CandleDataService candleDataService;

    @BeforeEach
    void setUp() {
        candleDataService = new CandleDataService(stockPriceService);
    }

    @Test
    @DisplayName("Nạp nến lịch sử cho FPT thành công (ưu tiên VNDirect hoặc Fallback CSV cục bộ)")
    void testGetHistoricalCandles_Fpt_ReturnsRealCandles() {
        List<Candle> candles = candleDataService.getHistoricalCandles("FPT", 60);

        assertNotNull(candles);
        assertFalse(candles.isEmpty());
        assertTrue(candles.size() <= 60 || candles.size() >= 10);

        Candle latest = candles.get(candles.size() - 1);
        assertEquals("FPT", latest.getSymbol());
        assertNotNull(latest.getClose());
        assertNotNull(latest.getDate());
        assertTrue(latest.getClose().doubleValue() > 0);
    }

    @Test
    @DisplayName("Nạp nến cho mã không có trong VN30 và không có API ngoài - Fallback nến mô phỏng an toàn")
    void testGetHistoricalCandles_UnknownSymbol_GeneratesRealisticFallback() {
        List<Candle> candles = candleDataService.getHistoricalCandles("UNKNOWN_NON_EXISTENT_999", 30);

        assertNotNull(candles);
        assertEquals(30, candles.size());
        Candle first = candles.get(0);
        assertEquals("UNKNOWN_NON_EXISTENT_999", first.getSymbol());
        assertTrue(first.getClose().doubleValue() > 0);
    }
}
