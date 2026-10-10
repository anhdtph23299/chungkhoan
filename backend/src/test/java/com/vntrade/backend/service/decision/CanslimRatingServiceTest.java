package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.CanslimRatingDto;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.marketdata.StockPriceService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CanslimRatingServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private CanslimRatingService canslimRatingService;

    @BeforeEach
    void setUp() {
        StockQuote quote = StockQuote.builder()
            .symbol("FPT")
            .price(BigDecimal.valueOf(140000))
            .volume(4500000L)
            .changePercent(BigDecimal.valueOf(2.0))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);
    }

    @Test
    void testRateStock_LeaderStockFPT() {
        CanslimRatingDto canslim = canslimRatingService.rateStock("FPT");

        assertNotNull(canslim);
        assertEquals("FPT", canslim.getSymbol());
        assertEquals("Tập đoàn FPT", canslim.getCompanyName());
        assertTrue(canslim.isInstitutionalGrade(), "FPT phải đạt chuẩn định chế CANSLIM");
        assertTrue(canslim.getCanslimScore() >= 80, "Điểm CANSLIM của FPT phải cao (>= 80)");
        assertEquals("A+", canslim.getCanslimGrade());
        assertTrue(canslim.isSectorLeader());
        assertFalse(canslim.getCanslimHighlights().isEmpty());
        assertNotNull(canslim.getInstitutionalVerdict());
    }

    @Test
    void testGetTopInstitutionalWatchlist() {
        List<CanslimRatingDto> watchlist = canslimRatingService.getTopInstitutionalWatchlist();

        assertNotNull(watchlist);
        assertFalse(watchlist.isEmpty());
        assertEquals(6, watchlist.size());
        // Verify sorted descending by canslim score
        for (int i = 0; i < watchlist.size() - 1; i++) {
            assertTrue(watchlist.get(i).getCanslimScore() >= watchlist.get(i + 1).getCanslimScore());
        }
    }
}