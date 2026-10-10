package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.PreMarketSentimentDto;
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
public class PreMarketSentimentServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private PreMarketSentimentService preMarketService;

    @BeforeEach
    void setUp() {
        StockQuote plxQuote = StockQuote.builder()
            .symbol("PLX")
            .price(BigDecimal.valueOf(37700))
            .changePercent(BigDecimal.valueOf(4.14))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(plxQuote);
    }

    @Test
    void testAnalyzePreMarketSentiment() {
        PreMarketSentimentDto dto = preMarketService.analyzePreMarketSentiment();

        assertNotNull(dto);
        assertNotNull(dto.getBrentPrice());
        assertTrue(dto.getBrentPrice().compareTo(BigDecimal.valueOf(100.0)) >= 0, "Dầu Brent phải trên 100 USD");
        assertEquals("SURGING_OVER_100_HIGH_ALERT", dto.getOilMarketStatus());
        assertNotNull(dto.getHypothesisVerdict());
        assertNotNull(dto.getRecommendedOpeningTactic());
        assertFalse(dto.getPriorityWatchlist().isEmpty(), "Danh mục trực canh tiền trạm không được rỗng");
        assertTrue(dto.getPriorityWatchlist().stream().anyMatch(w -> "PLX".equals(w.getSymbol())));
    }
}