package com.vntrade.backend.service;

import com.vntrade.backend.dto.PortfolioSummary;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private TradeService tradeService;

    @BeforeEach
    void setUp() {
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Kiểm tra chốt lời 50% vị thế: tính phí thuế chuẩn và dời Stop Loss hòa vốn chuẩn bước giá VN")
    void testExecutePartialClose_TickRoundingAndProfitAccounting() {
        // Mua 1,000 cp FPT giá 135,000 đ
        BigDecimal entryPrice = BigDecimal.valueOf(135_000);
        BigDecimal buyFee = entryPrice.multiply(BigDecimal.valueOf(1000)).multiply(BigDecimal.valueOf(0.0015)); // 202,500 đ

        Trade trade = Trade.builder()
            .id(1L)
            .symbol("FPT")
            .exchange("HOSE")
            .type("buy")
            .tradeDate(LocalDate.now().minusDays(3))
            .price(entryPrice)
            .quantity(1000)
            .fee(buyFee)
            .stopLoss(BigDecimal.valueOf(125_000))
            .takeProfit(BigDecimal.valueOf(155_000))
            .status("open")
            .build();

        // Chốt 500 cp tại giá 148,500 đ (+10%)
        BigDecimal closePrice = BigDecimal.valueOf(148_500);
        BigDecimal realizedGain = tradeService.executePartialClose(trade, closePrice, 500, "Chốt lời 50% tiền mặt");

        assertTrue(realizedGain.compareTo(BigDecimal.ZERO) > 0, "Lợi nhuận gặt hái phải dương");
        assertEquals(500, trade.getQuantity(), "Số lượng còn lại phải là 500 cp");
        assertEquals(500, trade.getPartialClosedQuantity(), "Số lượng đã chốt phải là 500 cp");
        assertEquals(realizedGain, trade.getPartialRealizedPnl(), "Lãi thực tế gặt hái phải được lưu");

        // Stop loss hòa vốn: 135,000 * 1.005 = 135,675 -> Giá >= 50,000 đ làm tròn bước giá 100 đ -> 135,700 đ
        assertNotNull(trade.getStopLoss());
        assertEquals(BigDecimal.valueOf(135700), trade.getStopLoss(), "Stop loss hòa vốn phải được làm tròn chuẩn bước giá 100đ sàn HOSE");
    }

    @Test
    @DisplayName("Kiểm tra đóng toàn bộ vị thế sau khi đã chốt một phần: không bị trừ trùng phí mua")
    void testExecuteMarketClose_ProportionalFeeAllocation() {
        BigDecimal entryPrice = BigDecimal.valueOf(50_000);
        // 1,000 cp mua hết 50,000,000 đ. Phí mua 0.15% = 75,000 đ
        BigDecimal totalBuyFee = BigDecimal.valueOf(75_000);

        Trade trade = Trade.builder()
            .id(2L)
            .symbol("HPG")
            .exchange("HOSE")
            .type("buy")
            .tradeDate(LocalDate.now().minusDays(5))
            .price(entryPrice)
            .quantity(500) // Đã bán 500 cp trước đó
            .partialClosedQuantity(500)
            .partialRealizedPnl(BigDecimal.valueOf(1_500_000))
            .fee(totalBuyFee) // Tổng phí ban đầu là 75,000 đ
            .status("open")
            .build();

        // Đóng nốt 500 cp tại giá 55,000 đ
        BigDecimal finalClosePrice = BigDecimal.valueOf(55_000);
        Trade closed = tradeService.executeMarketClose(trade, finalClosePrice, "Đóng toàn bộ vị thế");

        assertEquals("closed", closed.getStatus());
        assertEquals(500, closed.getCloseQuantity());
        // Doanh thu bán: 55,000 * 500 = 27,500,000 đ
        // Phí bán 0.15%: 41,250 đ
        // Thuế bán 0.10%: 27,500 đ
        // Vốn mua cho 500 cp: 50,000 * 500 = 25,000,000 đ
        // Phí mua phân bổ tỷ lệ cho 500/1000 cp: 37,500 đ (thay vì bị trừ đúp 75,000 đ)
        // Lãi ròng đợt cuối: 27,500,000 - 25,000,000 - 37,500 - 41,250 - 27,500 = 2,393,750 đ
        assertEquals(BigDecimal.valueOf(2393750), closed.getPnl(), "Lãi ròng phân bổ phí mua theo tỷ lệ chính xác từng đồng");
    }

    @Test
    @DisplayName("Kiểm tra PortfolioSummary bảo toàn 100% lợi nhuận đã gặt hái từ partialRealizedPnl")
    void testPortfolioSummary_IncludesPartialRealizedPnl() {
        Trade openTradeWithPartial = Trade.builder()
            .id(3L)
            .symbol("MWG")
            .status("open")
            .type("buy")
            .quantity(300)
            .pnl(BigDecimal.valueOf(600_000)) // Unrealized pnl
            .partialRealizedPnl(BigDecimal.valueOf(800_000)) // Harvested cash
            .build();

        Trade closedTradeWithPartial = Trade.builder()
            .id(4L)
            .symbol("TCB")
            .status("closed")
            .type("buy")
            .closeQuantity(500)
            .pnl(BigDecimal.valueOf(1_200_000)) // Final leg pnl
            .partialRealizedPnl(BigDecimal.valueOf(500_000)) // Harvested cash
            .build();

        when(tradeRepository.findOpenBuyTrades()).thenReturn(List.of(openTradeWithPartial));
        when(tradeRepository.findAllByOrderByTradeDateDesc()).thenReturn(List.of(openTradeWithPartial, closedTradeWithPartial));
        when(tradeRepository.countClosedTrades()).thenReturn(1L);
        when(tradeRepository.countWinningTrades()).thenReturn(1L);

        PortfolioSummary summary = tradeService.getPortfolioSummary();

        assertNotNull(summary);
        // Total PnL = Closed (1,200,000 + 500,000) + Open (600,000 + 800,000) = 1,700,000 + 1,400,000 = 3,100,000 đ
        assertEquals(BigDecimal.valueOf(3_100_000), summary.getTotalPnl(), "Toàn bộ tiền lãi gặt hái phải được cộng dồn vào tổng PnL");
        // NAV = 100,000,000 + 3,100,000 = 103,100,000 đ
        assertEquals(BigDecimal.valueOf(103_100_000), summary.getCurrentValue(), "NAV phải tính đủ lợi nhuận thực tế gặt hái");
    }
}
