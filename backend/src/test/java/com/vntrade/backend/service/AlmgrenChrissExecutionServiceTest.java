package com.vntrade.backend.service;

import com.vntrade.backend.dto.AlmgrenChrissExecutionDto;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AlmgrenChrissExecutionServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @Mock
    private OrderBookService orderBookService;

    @InjectMocks
    private AlmgrenChrissExecutionService executionService;

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
    void testPlanOptimalExecution() {
        int orderShares = 15000;
        AlmgrenChrissExecutionDto plan = executionService.planOptimalExecution("FPT", orderShares, 0.5);

        assertNotNull(plan);
        assertEquals("FPT", plan.getSymbol());
        assertEquals(orderShares, plan.getTotalOrderShares());
        assertEquals(BigDecimal.valueOf(135000), plan.getCurrentMarketPrice());
        assertTrue(plan.getMarketParticipationRatePercent().doubleValue() > 0);
        assertTrue(plan.getTotalExecutionDragBps().doubleValue() >= 0);
        assertTrue(plan.getNetExecutionEfficiencyScore().doubleValue() > 0);
        assertNotNull(plan.getOptimalTrajectory());
        assertEquals(6, plan.getOptimalTrajectory().size(), "Phải chia thành 6 khung giờ giao dịch chuẩn sàn HOSE");

        // Tổng số lượng của các đợt phải đúng bằng tổng lệnh đặt và tuân thủ lô 100
        int sumShares = 0;
        for (var step : plan.getOptimalTrajectory()) {
            assertEquals(0, step.getTrancheShares() % 100, "Mỗi đợt gom phải tròn lô 100 cp");
            sumShares += step.getTrancheShares();
            assertTrue(step.getTargetLimitPrice().remainder(BigDecimal.valueOf(100)).compareTo(BigDecimal.ZERO) == 0,
                "Giá Limit phải tròn theo bước giá HOSE (100đ)");
        }
        assertEquals(orderShares, sumShares, "Tổng các đợt phải đúng bằng tổng khối lượng lệnh ban đầu");
        assertNotNull(plan.getInstitutionalExecutionSummary());
    }
}
