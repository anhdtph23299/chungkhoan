package com.vntrade.backend.service.calculation;

import com.vntrade.backend.dto.FootprintDeltaDto;
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
public class IntradayOrderFlowFootprintServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private IntradayOrderFlowFootprintService footprintService;

    @BeforeEach
    void setUp() {
        StockQuote quote = StockQuote.builder()
            .symbol("FPT")
            .price(BigDecimal.valueOf(142000))
            .volume(5000000L)
            .changePercent(BigDecimal.valueOf(1.8))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);
    }

    @Test
    void testAnalyzeFootprintOrderFlow_BullishAbsorption() {
        FootprintDeltaDto footprint = footprintService.analyzeFootprintOrderFlow("FPT");

        assertNotNull(footprint);
        assertEquals("FPT", footprint.getSymbol());
        assertTrue(footprint.getNetDeltaVolume() > 0, "Net Delta của FPT phải dương khi giá tăng");
        assertTrue(footprint.getTotalActiveBuyVolume() > footprint.getTotalActiveSellVolume(),
            "Khối lượng chủ động mua phải lớn hơn khối lượng chủ động bán");
        assertEquals("BULLISH_ABSORPTION", footprint.getOrderFlowDivergence());
        assertEquals("AGGRESSIVE_ACCUMULATION_CONFIRMED", footprint.getInstitutionalActionSignal());
        assertTrue(footprint.getBacktestConfidenceScore() >= 80, "Độ tin cậy Backtest phải >= 80");
        assertFalse(footprint.getWhaleClusters().isEmpty(), "Phải phát hiện được các cụm lệnh cá mập");
        assertNotNull(footprint.getFootprintSummaryVerdict());
    }
}