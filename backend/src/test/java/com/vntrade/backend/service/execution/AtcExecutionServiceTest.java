package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.AtcOrderFlowDto;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.marketdata.StockPriceService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AtcExecutionServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private AtcExecutionService atcService;

    @BeforeEach
    void setUp() {
        StockQuote quote = StockQuote.builder()
            .symbol("HPG")
            .price(BigDecimal.valueOf(30000))
            .changePercent(BigDecimal.valueOf(1.8))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);
    }

    @Test
    void testEvaluateAtcWindow_Bullish() {
        AtcOrderFlowDto atc = atcService.evaluateAtcWindow("HPG");

        assertNotNull(atc);
        assertEquals("HPG", atc.getSymbol());
        assertEquals("HOLD_FOR_OVERNIGHT_GAP", atc.getAtcActionSignal());
        assertTrue(atc.getAtcBuyPressureRatio().doubleValue() > 1.5, "Lực mua ATC phải lớn hơn bán");
        assertNotNull(atc.getProfessionalTactic());
    }

    @Test
    void testEvaluateTopAtcStocks() {
        List<AtcOrderFlowDto> list = atcService.evaluateTopAtcStocks();

        assertNotNull(list);
        assertFalse(list.isEmpty());
    }
}