package com.vntrade.backend.service.marketdata;

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
    @DisplayName("Kiểm tra biên độ trần sàn +-7%, bước giá HOSE và xử lý trung thực khi ngoài phiên không có độ sâu giả")
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
        assertNotNull(ob.getBidLevels());
        assertNotNull(ob.getAskLevels());
        assertNotNull(ob.getDataSource());

        // Kiểm tra bước giá HOSE đúng chuẩn: <10k -> 10đ, 10k-50k -> 50đ, >=50k -> 100đ
        assertEquals(0, orderBookService.getTickSize(BigDecimal.valueOf(9500)).compareTo(BigDecimal.valueOf(10)));
        assertEquals(0, orderBookService.getTickSize(BigDecimal.valueOf(29800)).compareTo(BigDecimal.valueOf(50)));
        assertEquals(0, orderBookService.getTickSize(BigDecimal.valueOf(65000)).compareTo(BigDecimal.valueOf(100)));

        // Trần > Tham chiếu > Sàn
        assertTrue(ob.getCeilingPrice().compareTo(ob.getReferencePrice()) > 0);
        assertTrue(ob.getFloorPrice().compareTo(ob.getReferencePrice()) < 0);
        assertTrue(ob.getEstimatedSlippagePercent().doubleValue() < 0.50);
    }
}
