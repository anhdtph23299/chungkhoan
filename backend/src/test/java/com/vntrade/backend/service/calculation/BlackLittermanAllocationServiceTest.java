package com.vntrade.backend.service.calculation;

import com.vntrade.backend.dto.BlackLittermanAllocationDto;
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
public class BlackLittermanAllocationServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private BlackLittermanAllocationService blackLittermanService;

    @BeforeEach
    void setUp() {
        when(stockPriceService.getQuote(anyString())).thenAnswer(invocation -> {
            String sym = invocation.getArgument(0);
            BigDecimal price = switch (sym) {
                case "FPT" -> BigDecimal.valueOf(135000);
                case "TCB" -> BigDecimal.valueOf(38000);
                case "HPG" -> BigDecimal.valueOf(28000);
                case "SSI" -> BigDecimal.valueOf(35000);
                case "MWG" -> BigDecimal.valueOf(65000);
                default -> BigDecimal.valueOf(90000);
            };
            return StockQuote.builder()
                .symbol(sym)
                .price(price)
                .volume(5_000_000L)
                .build();
        });
    }

    @Test
    void testCalculateBlackLittermanAllocation() {
        BigDecimal nav = BigDecimal.valueOf(250_000_000);
        BlackLittermanAllocationDto result = blackLittermanService.calculateBlackLittermanAllocation(nav);

        assertNotNull(result);
        assertEquals(nav, result.getPortfolioNav());
        assertNotNull(result.getMarketRiskAversionDelta());
        assertNotNull(result.getActiveSharePercent());
        assertTrue(result.getActiveSharePercent().doubleValue() > 0, "Active Share phải lớn hơn 0 chứng minh sự lựa chọn chủ động");
        assertNotNull(result.getExpectedPosteriorReturnPercent());
        assertTrue(result.getExpectedPosteriorReturnPercent().doubleValue() > 0);
        assertNotNull(result.getPosteriorSharpeRatio());
        assertFalse(result.getQuantitativeAlphaViews().isEmpty());
        assertFalse(result.getAssetAllocations().isEmpty());

        for (var item : result.getAssetAllocations()) {
            assertNotNull(item.getSymbol());
            assertNotNull(item.getSector());
            assertTrue(item.getOptimalBlackLittermanWeightPercent().doubleValue() <= 25.0, "Trần tỷ trọng từng mã tối đa 25% NAV");
            assertTrue(item.getAllocatedMoneyVnd().compareTo(BigDecimal.ZERO) > 0);
            assertEquals(0, item.getTargetSharesLot100() % 100, "Số cổ phiếu phân bổ phải tròn lô 100 cp");
        }

        assertNotNull(result.getInstitutionalAuditSummary());
        assertTrue(result.getInstitutionalAuditSummary().contains("BLACK-LITTERMAN"));
    }
}