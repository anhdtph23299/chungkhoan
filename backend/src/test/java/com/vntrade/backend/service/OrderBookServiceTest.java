package com.vntrade.backend.service;

import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class OrderBookServiceTest {

    private StockPriceService stockPriceService;
    private OrderBookService orderBookService;

    @BeforeEach
    void setUp() {
        stockPriceService = Mockito.mock(StockPriceService.class);
        orderBookService = new OrderBookService(stockPriceService);
    }

    @Test
    @DisplayName("Kiểm tra mô phỏng sổ lệnh Level-2: 3 bước giá Dư mua / Dư bán, biên độ trần sàn +-7%, bước giá đúng quy định HOSE")
    void testGetOrderBook() {
        StockQuote quote = StockQuote.builder()
            .symbol("HPG")
            .price(BigDecimal.valueOf(29800))
            .open(BigDecimal.valueOf(29500))
            .volume(12_000_000L)
            .build();

        when(stockPriceService.getQuote("HPG")).thenReturn(quote);

        OrderBookDto ob = orderBookService.getOrderBook("HPG");

        assertNotNull(ob);
        assertEquals("HPG", ob.getSymbol());
        assertEquals(3, ob.getBidLevels().size());
        assertEquals(3, ob.getAskLevels().size());

        // Giá 29.800 thuộc vùng 10.000 - 49.950 -> bước giá 50đ
        // Bids: 29800, 29750, 29700
        assertEquals(0, ob.getBidLevels().get(0).getPrice().compareTo(BigDecimal.valueOf(29800)));
        assertEquals(0, ob.getBidLevels().get(1).getPrice().compareTo(BigDecimal.valueOf(29750)));
        assertEquals(0, ob.getBidLevels().get(2).getPrice().compareTo(BigDecimal.valueOf(29700)));

        // Asks: 29850, 29900, 29950
        assertEquals(0, ob.getAskLevels().get(0).getPrice().compareTo(BigDecimal.valueOf(29850)));
        assertEquals(0, ob.getAskLevels().get(1).getPrice().compareTo(BigDecimal.valueOf(29900)));
        assertEquals(0, ob.getAskLevels().get(2).getPrice().compareTo(BigDecimal.valueOf(29950)));

        // Trần = 29500 * 1.07 = 31565
        assertTrue(ob.getCeilingPrice().compareTo(ob.getReferencePrice()) > 0);
        assertTrue(ob.getFloorPrice().compareTo(ob.getReferencePrice()) < 0);
        assertTrue(ob.getLiquidityGrade().contains("AAA"));
        assertTrue(ob.getEstimatedSlippagePercent().doubleValue() < 0.25);
    }
}
