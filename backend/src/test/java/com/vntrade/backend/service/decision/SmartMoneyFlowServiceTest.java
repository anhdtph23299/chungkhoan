package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.SmartMoneyFlowDto;
import com.vntrade.backend.dto.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.marketdata.StockPriceService;

@ExtendWith(MockitoExtension.class)
public class SmartMoneyFlowServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private SmartMoneyFlowService smartMoneyFlowService;

    @BeforeEach
    void setUp() {
        StockQuote fpt = StockQuote.builder()
            .symbol("FPT")
            .price(BigDecimal.valueOf(142000))
            .change(BigDecimal.valueOf(2500))
            .changePercent(BigDecimal.valueOf(1.8))
            .volume(4500000L)
            .build();

        when(stockPriceService.getQuote(anyString())).thenReturn(fpt);
    }

    @Test
    void testAnalyzeSmartMoney_StrongAccumulation() {
        SmartMoneyFlowDto dto = smartMoneyFlowService.analyzeSmartMoney("FPT");

        assertNotNull(dto);
        assertEquals("FPT", dto.getSymbol());
        assertTrue(dto.getBuyActiveRatio().doubleValue() > 50.0, "Tỷ lệ mua chủ động phải vượt 50%");
        assertTrue(dto.getAccumulationScore() >= 60, "Điểm gom hàng cá mập phải cao khi giá tăng 1.8%");
        assertEquals("STRONG_ACCUMULATION", dto.getMoneyFlowStatus());
        assertNotNull(dto.getForeignNetBuy());
    }

    @Test
    void testGetTopAccumulationSymbols() {
        List<SmartMoneyFlowDto> top = smartMoneyFlowService.getTopAccumulationSymbols();

        assertNotNull(top);
        assertFalse(top.isEmpty());
        // Danh sách phải được sắp xếp giảm dần theo accumulationScore
        for (int i = 0; i < top.size() - 1; i++) {
            assertTrue(top.get(i).getAccumulationScore() >= top.get(i + 1).getAccumulationScore());
        }
    }
}