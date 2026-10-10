package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.OrderBookDto.OrderBookLevel;
import com.vntrade.backend.dto.OrderBookImbalanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.marketdata.OrderBookService;

class OrderBookImbalanceServiceTest {

    private OrderBookService orderBookService;
    private OrderBookImbalanceService orderBookImbalanceService;

    @BeforeEach
    void setUp() {
        orderBookService = Mockito.mock(OrderBookService.class);
        orderBookImbalanceService = new OrderBookImbalanceService(orderBookService);
    }

    @Test
    @DisplayName("Kiểm tra phân tích vi cấu trúc sổ lệnh Level-2: OBI, Stoikov Micro-Price, phát hiện tường bán đè giá")
    void testAnalyzeMicrostructure_WithAskWall() {
        List<OrderBookLevel> bids = new ArrayList<>();
        bids.add(OrderBookLevel.builder().level(1).price(BigDecimal.valueOf(130000)).volume(100_000L).build());
        bids.add(OrderBookLevel.builder().level(2).price(BigDecimal.valueOf(129900)).volume(120_000L).build());
        bids.add(OrderBookLevel.builder().level(3).price(BigDecimal.valueOf(129800)).volume(150_000L).build());

        List<OrderBookLevel> asks = new ArrayList<>();
        asks.add(OrderBookLevel.builder().level(1).price(BigDecimal.valueOf(130100)).volume(100_000L).build());
        asks.add(OrderBookLevel.builder().level(2).price(BigDecimal.valueOf(130200)).volume(500_000L).build()); // Wall >= 40% và >= 200k
        asks.add(OrderBookLevel.builder().level(3).price(BigDecimal.valueOf(130300)).volume(150_000L).build());

        OrderBookDto ob = OrderBookDto.builder()
            .symbol("FPT")
            .currentPrice(BigDecimal.valueOf(130000))
            .bidLevels(bids)
            .askLevels(asks)
            .build();

        when(orderBookService.getOrderBook("FPT")).thenReturn(ob);

        OrderBookImbalanceDto result = orderBookImbalanceService.analyzeMicrostructure("FPT");

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertEquals(370_000L, result.getTotalBidVolume());
        assertEquals(750_000L, result.getTotalAskVolume());
        // Ask > Bid -> OBI phải âm (áp lực bán lớn hơn)
        assertTrue(result.getOrderBookImbalanceRatio().compareTo(BigDecimal.ZERO) < 0);
        // Phát hiện ASK_WALL_RESISTANCE
        assertEquals("ASK_WALL_RESISTANCE", result.getWallDetected());
        assertEquals(BigDecimal.valueOf(130200), result.getWallPrice());
        assertEquals(500_000L, result.getWallVolume());
        assertNotNull(result.getInstitutionalVerdict());
        assertTrue(result.getInstitutionalVerdict().contains("FPT"));
    }

    @Test
    @DisplayName("Kiểm tra vi cấu trúc khi dòng tiền gom hàng mạnh (Bullish Flow): OBI dương, Stoikov Micro-Price cao hơn Mid-Price")
    void testAnalyzeMicrostructure_BullishFlow() {
        List<OrderBookLevel> bids = new ArrayList<>();
        bids.add(OrderBookLevel.builder().level(1).price(BigDecimal.valueOf(130000)).volume(400_000L).build());
        bids.add(OrderBookLevel.builder().level(2).price(BigDecimal.valueOf(129900)).volume(300_000L).build());
        bids.add(OrderBookLevel.builder().level(3).price(BigDecimal.valueOf(129800)).volume(200_000L).build());

        List<OrderBookLevel> asks = new ArrayList<>();
        asks.add(OrderBookLevel.builder().level(1).price(BigDecimal.valueOf(130100)).volume(50_000L).build());
        asks.add(OrderBookLevel.builder().level(2).price(BigDecimal.valueOf(130200)).volume(60_000L).build());
        asks.add(OrderBookLevel.builder().level(3).price(BigDecimal.valueOf(130300)).volume(70_000L).build());

        OrderBookDto ob = OrderBookDto.builder()
            .symbol("FPT")
            .currentPrice(BigDecimal.valueOf(130000))
            .bidLevels(bids)
            .askLevels(asks)
            .build();

        when(orderBookService.getOrderBook("FPT")).thenReturn(ob);

        OrderBookImbalanceDto result = orderBookImbalanceService.analyzeMicrostructure("FPT");

        assertNotNull(result);
        assertTrue(result.getOrderBookImbalanceRatio().compareTo(BigDecimal.valueOf(0.35)) > 0);
        assertEquals("BULLISH_AGGRESSIVE_FLOW", result.getMicrostructureSignal());
        assertEquals("TWAP_ICEBERG_ACCUMULATE", result.getOptimalExecutionStrategy());
        // Stoikov Micro-Price phải kéo về phía Ask vì Bid dày hơn hẳn
        assertTrue(result.getMicroPrice().compareTo(result.getMidPrice()) >= 0);
    }
}