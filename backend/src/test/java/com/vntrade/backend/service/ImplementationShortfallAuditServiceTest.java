package com.vntrade.backend.service;

import com.vntrade.backend.dto.ImplementationShortfallAuditDto;
import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ImplementationShortfallAuditServiceTest {

    private StockPriceService stockPriceService;
    private OrderBookService orderBookService;
    private ImplementationShortfallAuditService implementationShortfallAuditService;

    @BeforeEach
    void setUp() {
        stockPriceService = Mockito.mock(StockPriceService.class);
        orderBookService = Mockito.mock(OrderBookService.class);
        implementationShortfallAuditService = new ImplementationShortfallAuditService(stockPriceService, orderBookService);

        when(stockPriceService.getQuote("FPT")).thenReturn(
            StockQuote.builder()
                .symbol("FPT")
                .price(BigDecimal.valueOf(140000))
                .volume(5000000L)
                .build()
        );

        when(orderBookService.getOrderBook(anyString())).thenReturn(
            OrderBookDto.builder()
                .symbol("FPT")
                .currentPrice(BigDecimal.valueOf(140000))
                .build()
        );

        when(orderBookService.getTickSize(any(BigDecimal.class))).thenReturn(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("Kiểm tra kiểm toán hao hụt thực thi Perold Implementation Shortfall (IS): Delay cost, Slippage, Execution Alpha và tiết kiệm chi phí")
    void testAuditImplementationShortfall() {
        ImplementationShortfallAuditDto result = implementationShortfallAuditService.auditImplementationShortfall(
            "FPT", 10000, "VWAP_SMART_SLICING"
        );

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertEquals("VWAP_SMART_SLICING", result.getAlgorithmName());
        assertEquals(10000, result.getTotalOrderQuantity());
        assertEquals(BigDecimal.valueOf(140000), result.getDecisionBenchmarkPrice());
        assertEquals(BigDecimal.valueOf(140100), result.getArrivalPrice());
        assertNotNull(result.getAverageFillPrice());
        assertTrue(result.getAverageFillPrice().compareTo(result.getDecisionBenchmarkPrice()) >= 0);

        assertNotNull(result.getDelayCostBps());
        assertNotNull(result.getPriceImpactBps());
        assertNotNull(result.getFeeAndTaxBps());
        assertNotNull(result.getTotalImplementationShortfallBps());
        assertNotNull(result.getCostSavingsVsMarketOrderVnd());
        assertTrue(result.getCostSavingsVsMarketOrderVnd().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(result.getExecutionAlphaBps());
        assertTrue(result.getExecutionAlphaBps().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(result.getExecutionQualityGrade());
        assertNotNull(result.getInstitutionalAuditVerdict());
        assertTrue(result.getInstitutionalAuditVerdict().contains("KIỂM TOÁN THỰC THI LỆNH"));
    }
}
