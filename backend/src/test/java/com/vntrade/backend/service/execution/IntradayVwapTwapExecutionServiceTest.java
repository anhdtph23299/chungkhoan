package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.IntradayVwapTwapExecutionDto;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.marketdata.OrderBookService;
import com.vntrade.backend.service.marketdata.StockPriceService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class IntradayVwapTwapExecutionServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @Mock
    private OrderBookService orderBookService;

    @InjectMocks
    private IntradayVwapTwapExecutionService executionService;

    @BeforeEach
    void setUp() {
        StockQuote quote = StockQuote.builder()
            .symbol("FPT")
            .price(BigDecimal.valueOf(135000))
            .volume(4_000_000L)
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);
        when(orderBookService.getTickSize(any(BigDecimal.class))).thenReturn(BigDecimal.valueOf(100));
    }

    @Test
    void testPlanIntradayExecution_VwapBuy() {
        int orderShares = 12000;
        IntradayVwapTwapExecutionDto plan = executionService.planIntradayExecution(
            "FPT", "BUY", orderShares, "VWAP_VOLUME_WEIGHTED"
        );

        assertNotNull(plan);
        assertEquals("FPT", plan.getSymbol());
        assertEquals("BUY", plan.getOrderSide());
        assertEquals(orderShares, plan.getTotalOrderShares());
        assertEquals(BigDecimal.valueOf(135000), plan.getCurrentMarketPrice());
        assertNotNull(plan.getExpectedVwapPrice());
        assertNotNull(plan.getExpectedTwapPrice());
        assertTrue(plan.getEstimatedCostSavingsVnd().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(plan.getSlippageSavingsBps().doubleValue() > 0);
        assertNotNull(plan.getT25MandatorySettlementDeadline());
        assertTrue(plan.getT25MandatorySettlementDeadline().contains("13:00"));
        assertNotNull(plan.getTrancheSchedules());
        assertEquals(6, plan.getTrancheSchedules().size(), "Đường cong thanh khoản chia làm 6 khung giờ");

        int totalTrancheShares = 0;
        for (var tranche : plan.getTrancheSchedules()) {
            assertEquals(0, tranche.getRecommendedTrancheShares() % 100, "Mỗi đợt gom phải tròn lô 100 cp");
            totalTrancheShares += tranche.getRecommendedTrancheShares();
            assertEquals(0, tranche.getTargetLimitPrice().remainder(BigDecimal.valueOf(100)).compareTo(BigDecimal.ZERO),
                "Giá Limit phải tròn theo bước giá HOSE (100đ)");
            assertNotNull(tranche.getExecutionTactic());
        }

        assertEquals(orderShares, totalTrancheShares, "Tổng các đợt gom phải đúng bằng tổng khối lượng lệnh");
        assertNotNull(plan.getInstitutionalComplianceVerdict());
    }
}