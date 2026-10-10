package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.entity.PortfolioSnapshot;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.vntrade.backend.service.marketdata.SseStreamService;
import com.vntrade.backend.service.portfolio.PortfolioHistoryService;
import com.vntrade.backend.service.marketdata.StockPriceService;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSimulationService {

    private final StockPriceService stockPriceService;
    private final AutoTradingBotService botService;
    private final TradeRepository tradeRepository;
    private final PortfolioHistoryService portfolioHistoryService;
    private final SseStreamService sseStreamService;

    private final Random random = new Random();

    /**
     * Mô phỏng 1 nhịp biến động giá thị trường (Tick) trong biên độ sàn HOSE (+-7%)
     */
    public Map<String, Object> simulateMarketTick() {
        List<Trade> openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");
        Map<String, BigDecimal> updatedPrices = new HashMap<>();

        for (Trade trade : openTrades) {
            String symbol = trade.getSymbol();
            StockQuote currentQuote = stockPriceService.getQuote(symbol);
            BigDecimal currentPrice = currentQuote.getPrice();

            // Biến động thực tế trong phiên: từ -1.0% đến +1.8%
            double deltaPercent = -1.0 + (random.nextDouble() * 2.8);
            BigDecimal multiplier = BigDecimal.valueOf(1.0 + (deltaPercent / 100.0));
            BigDecimal newPrice = currentPrice.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);

            // Đảm bảo bước giá chẵn 50đ hoặc 100đ theo luật HOSE
            long p = newPrice.longValue();
            long roundedPrice = (p / 100) * 100;
            BigDecimal finalPrice = BigDecimal.valueOf(roundedPrice);

            stockPriceService.updateMarketPrice(symbol, finalPrice);
            updatedPrices.put(symbol, finalPrice);
        }

        // Kích hoạt bot quản lý vị thế: kiểm tra TP, SL, Chốt lời 50% hàng ngày
        botService.manageOpenPositions();

        // Ghi nhận snapshot NAV sau khi giá cập nhật
        PortfolioSnapshot snapshot = portfolioHistoryService.recordCurrentSnapshot();

        try {
            sseStreamService.broadcast("MARKET_TICK_SIMULATED", Map.of(
                "updatedPrices", updatedPrices,
                "nav", snapshot.getTotalNav(),
                "realizedPnl", snapshot.getRealizedPnl(),
                "unrealizedPnl", snapshot.getUnrealizedPnl()
            ));
        } catch (Exception ignored) {}

        log.info("Market tick simulated for {} positions. New NAV: {} đ", openTrades.size(), snapshot.getTotalNav());

        return Map.of(
            "status", "TICK_COMPLETED",
            "updatedPositionsCount", openTrades.size(),
            "updatedPrices", updatedPrices,
            "currentNav", snapshot.getTotalNav(),
            "realizedPnl", snapshot.getRealizedPnl(),
            "unrealizedPnl", snapshot.getUnrealizedPnl()
        );
    }

    /**
     * Tua nhanh 1 ngày giao dịch hoàn chỉnh:
     * Đẩy sóng tăng điểm các mã mạnh -> Kích hoạt gặt hái lợi nhuận hàng ngày -> Tìm điểm mua mới
     */
    public Map<String, Object> fastForwardTradingDay() {
        List<Trade> openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");
        Map<String, Object> sessionResults = new HashMap<>();

        // 1. Mô phỏng phiên bùng nổ của cổ phiếu dẫn dắt (đẩy FPT hoặc HPG lên trên +10%)
        if (!openTrades.isEmpty()) {
            Trade leadTrade = openTrades.get(0);
            BigDecimal targetHarvestPrice = leadTrade.getPrice().multiply(BigDecimal.valueOf(1.108)).setScale(0, RoundingMode.HALF_UP);
            stockPriceService.updateMarketPrice(leadTrade.getSymbol(), targetHarvestPrice);
            log.info("🚀 Sóng tăng đẩy {} lên {} đ (+10.8%) để kích hoạt gặt hái tiền mặt hàng ngày", leadTrade.getSymbol(), targetHarvestPrice);
        }

        // 2. Chạy chu kỳ hoàn chỉnh của bot
        botService.executeBotCycle();

        // 3. Cập nhật số liệu NAV
        PortfolioSnapshot snapshot = portfolioHistoryService.recordCurrentSnapshot();

        sessionResults.put("status", "DAY_FAST_FORWARDED");
        sessionResults.put("todayRealizedPnl", botService.getTodayRealizedPnl());
        sessionResults.put("todayTradesCount", botService.getTodayTradesCount());
        sessionResults.put("nav", snapshot.getTotalNav());
        sessionResults.put("winRate", snapshot.getWinRate());

        try {
            sseStreamService.broadcast("DAY_FAST_FORWARDED", sessionResults);
        } catch (Exception ignored) {}

        return sessionResults;
    }

    /**
     * Kích hoạt ngay lập tức cơ chế gặt hái lợi nhuận 50% tiền tươi cho 1 mã
     */
    public Map<String, Object> triggerDirectProfitHarvest(String symbol) {
        String sym = symbol.toUpperCase().trim();
        List<Trade> openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");
        Trade targetTrade = openTrades.stream()
            .filter(t -> t.getSymbol().equalsIgnoreCase(sym))
            .findFirst()
            .orElse(null);

        if (targetTrade == null) {
            return Map.of("status", "ERROR", "message", "Không tìm thấy vị thế đang mở cho mã " + sym);
        }

        // Đẩy giá vượt +10.5% so với giá vốn
        BigDecimal harvestPrice = targetTrade.getPrice().multiply(BigDecimal.valueOf(1.105)).setScale(0, RoundingMode.HALF_UP);
        stockPriceService.updateMarketPrice(sym, harvestPrice);

        // Kích hoạt bot chốt lời 50%
        botService.manageOpenPositions();

        PortfolioSnapshot snapshot = portfolioHistoryService.recordCurrentSnapshot();

        return Map.of(
            "status", "PROFIT_HARVESTED",
            "symbol", sym,
            "harvestPrice", harvestPrice,
            "todayRealizedPnl", botService.getTodayRealizedPnl(),
            "remainingShares", targetTrade.getQuantity(),
            "newStopLoss", targetTrade.getStopLoss(),
            "nav", snapshot.getTotalNav()
        );
    }
}