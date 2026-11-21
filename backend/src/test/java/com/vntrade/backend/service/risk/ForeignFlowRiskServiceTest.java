package com.vntrade.backend.service.risk;

import com.vntrade.backend.dto.ForeignFlowRiskDto;
import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.service.marketdata.OrderBookService;
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
public class ForeignFlowRiskServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @Mock
    private OrderBookService orderBookService;

    @InjectMocks
    private ForeignFlowRiskService foreignFlowRiskService;

    @BeforeEach
    void setUp() {
        StockQuote quote = StockQuote.builder()
            .symbol("FPT")
            .price(BigDecimal.valueOf(140000))
            .volume(4000000L)
            .changePercent(BigDecimal.valueOf(1.5))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);

        OrderBookDto ob = OrderBookDto.builder()
            .symbol("FPT")
            .currentPrice(BigDecimal.valueOf(140000))
            .totalMatchedVolume(4_000_000L)
            .foreignBuyVolume(850_000L)
            .foreignSellVolume(120_000L)
            .foreignNetVolume(730_000L)
            .foreignRoomRemaining(0L)
            .realOrderBookAvailable(true)
            .dataSource("24HMONEY_REAL")
            .build();
        when(orderBookService.getOrderBook(anyString())).thenReturn(ob);
    }

    @Test
    void testEvaluateForeignFlowRisk_FPT() {
        ForeignFlowRiskDto report = foreignFlowRiskService.evaluateForeignFlowRisk("FPT");

        assertNotNull(report);
        assertEquals("FPT", report.getSymbol());
        assertTrue(report.getForeignNetValueVnd().compareTo(BigDecimal.ZERO) > 0, "Giá trị ròng khối ngoại FPT phải dương");
        assertEquals("LOW", report.getSlippageRiskIndex());
        assertEquals("STRONG_FOREIGN_NET_ACCUMULATION", report.getFiiFlowVerdict());
        assertTrue(report.getLiquidityHealthScore() >= 85);
        assertFalse(report.getEarlyWarningAlerts().isEmpty());
        assertNotNull(report.getInstitutionalRecommendation());
    }
}