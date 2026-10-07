package com.vntrade.backend.service;

import com.vntrade.backend.dto.MultiTimeframeConfluenceDto;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MultiTimeframeConfluenceServiceTest {

    @Mock
    private CandleDataService candleDataService;

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private MultiTimeframeConfluenceService confluenceService;

    @BeforeEach
    void setUp() {
        StockQuote quote = StockQuote.builder()
            .symbol("FPT")
            .price(BigDecimal.valueOf(142000))
            .volume(4500000L)
            .changePercent(BigDecimal.valueOf(1.8))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);
    }

    @Test
    void testAnalyzeMultiTimeframe_BullishConfluence() {
        MultiTimeframeConfluenceDto result = confluenceService.analyzeMultiTimeframe("FPT");

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertTrue(result.isTripleGreenAlignment(), "Tín hiệu phải đồng thuận xanh 3 khung thời gian W1-D1-H1");
        assertTrue(result.getConfluenceScore() >= 80, "Điểm đồng thuận phải >= 80");
        assertEquals("STRONG_BUY_EXECUTE", result.getActionSignal());
        assertNotNull(result.getHourlyOptimalEntryPrice());
        assertNotNull(result.getRecommendationVerdict());
    }

    @Test
    void testScanConfluenceLeaders() {
        List<MultiTimeframeConfluenceDto> leaders = confluenceService.scanConfluenceLeaders();

        assertNotNull(leaders);
        assertFalse(leaders.isEmpty());
        assertTrue(leaders.size() >= 3);
        // Verify sorted by confluence score descending
        for (int i = 0; i < leaders.size() - 1; i++) {
            assertTrue(leaders.get(i).getConfluenceScore() >= leaders.get(i + 1).getConfluenceScore());
        }
    }
}
