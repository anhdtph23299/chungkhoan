package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.KellySizingDto;
import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.repository.TradeRepository;
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
import com.vntrade.backend.service.backtest.InstitutionalBacktestService;
import com.vntrade.backend.service.marketdata.StockPriceService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class KellyCriterionServiceTest {

    @Mock
    private StockPriceService stockPriceService;
    @Mock
    private InstitutionalBacktestService institutionalBacktestService;
    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private KellyCriterionService kellyCriterionService;

    @BeforeEach
    void setUp() {
        StockQuote quote = StockQuote.builder()
            .symbol("SSI")
            .price(BigDecimal.valueOf(35000))
            .volume(8000000L)
            .changePercent(BigDecimal.valueOf(2.5))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);
    }

    @Test
    void testCalculateKellySizing() {
        BigDecimal nav = BigDecimal.valueOf(300_000_000);
        KellySizingDto result = kellyCriterionService.calculateKellySizing("SSI", nav);

        assertNotNull(result);
        assertEquals("SSI", result.getSymbol());
        assertEquals(nav, result.getAccountCapital());
        assertEquals(0, result.getProbabilityOfRuin().compareTo(BigDecimal.ZERO), "Xác suất cháy tài khoản theo công thức Half-Kelly phải bằng 0%");
        assertTrue(result.getRecommendedAllocationPercent().doubleValue() <= 25.0, "Giới hạn an toàn danh mục tối đa 25% cho 1 vị thế");
        assertEquals(0, result.getRecommendedSharesToBuy() % 100, "Số lượng cổ phiếu mua phải tròn lô 100");
        assertTrue(result.getRecommendedAllocationMoney().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(result.getMathematicalVerdict());
    }
}