package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.ExecutionAlgoDto;
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
public class OrderExecutionAlgorithmServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private OrderExecutionAlgorithmService executionAlgorithmService;

    @BeforeEach
    void setUp() {
        StockQuote quote = StockQuote.builder()
            .symbol("HPG")
            .price(BigDecimal.valueOf(29000))
            .volume(15000000L)
            .changePercent(BigDecimal.valueOf(1.2))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);
    }

    @Test
    void testPlanInstitutionalExecution_VWAP() {
        ExecutionAlgoDto plan = executionAlgorithmService.planInstitutionalExecution("HPG", 10000, "VWAP_SMART_SLICING");

        assertNotNull(plan);
        assertEquals("HPG", plan.getSymbol());
        assertEquals(10000, plan.getTotalOrderQuantity());
        assertEquals("VWAP_SMART_SLICING", plan.getAlgorithmType());
        assertEquals(5, plan.getScheduledTranches().size(), "Phải chia thành 5 đợt khớp lệnh tương ứng 5 khung giờ thanh khoản");
        
        // Sum of tranches must equal total quantity exactly
        int totalTrancheQty = plan.getScheduledTranches().stream()
            .mapToInt(ExecutionAlgoDto.OrderTranche::getTrancheQuantity)
            .sum();
        assertEquals(10000, totalTrancheQty, "Tổng số lượng các đợt phải đúng bằng tổng khối lượng đặt lệnh");

        // Verify slippage savings
        assertTrue(plan.getEstimatedCostSavings().compareTo(BigDecimal.ZERO) > 0, "Thuật toán phải tiết kiệm chi phí trượt giá đáng kể");
        assertTrue(plan.getEstimatedSlippageWithAlgo().compareTo(plan.getEstimatedSlippageNoAlgo()) < 0, "Độ trượt giá khi dùng thuật toán phải thấp hơn thị trường");
        assertNotNull(plan.getExecutionStrategyNote());
    }

    @Test
    void testPlanInstitutionalExecution_BoardLotEnforcement() {
        // Test rounding to 100 shares lot
        ExecutionAlgoDto plan = executionAlgorithmService.planInstitutionalExecution("HPG", 1345, "VWAP_SMART_SLICING");

        assertNotNull(plan);
        assertEquals(1300, plan.getTotalOrderQuantity(), "Phải làm tròn theo lô 100 cổ phiếu chuẩn sàn Việt Nam");
    }
}