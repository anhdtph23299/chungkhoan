package com.vntrade.backend.service.risk;

import com.vntrade.backend.dto.MarketCrashProtectionDto;
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
public class MarketCrashProtectionServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private MarketCrashProtectionService crashService;

    @BeforeEach
    void setUp() {
        StockQuote normalQuote = StockQuote.builder()
            .symbol("VCB")
            .price(BigDecimal.valueOf(92000))
            .changePercent(BigDecimal.valueOf(0.8))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(normalQuote);
    }

    @Test
    void testEvaluateMarketCircuitBreaker_Normal() {
        MarketCrashProtectionDto dto = crashService.evaluateMarketCircuitBreaker();

        assertNotNull(dto);
        assertEquals(0, dto.getDefenseLevel(), "Thị trường bình thường level phải là 0");
        assertTrue(dto.isAllowNewPurchases(), "Thị trường thuận lợi phải cho phép giải ngân");
        assertNotNull(dto.getCircuitBreakerMessage());
        assertNotNull(dto.getActionProtocol());
    }
}