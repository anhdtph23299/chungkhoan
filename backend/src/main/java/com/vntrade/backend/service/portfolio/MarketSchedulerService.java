package com.vntrade.backend.service.portfolio;

import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.entity.Watchlist;
import com.vntrade.backend.repository.TradeRepository;
import com.vntrade.backend.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import com.vntrade.backend.service.marketdata.StockPriceService;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSchedulerService {

    private final TradeRepository tradeRepository;
    private final WatchlistRepository watchlistRepository;
    private final StockPriceService stockPriceService;
    private final AlertService alertService;

    /**
     * Chạy định kỳ mỗi 60 giây để giám sát vị thế đang mở và kiểm tra Stop Loss / Take Profit
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 15000)
    public void monitorActiveTradesAndRisk() {
        log.debug("Scheduler: Monitoring active trades and risk limits at {}", LocalDateTime.now());
        List<Trade> openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");

        for (Trade trade : openTrades) {
            try {
                StockQuote quote = stockPriceService.getQuote(trade.getSymbol());
                if (quote.getPrice() == null || quote.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal currentPrice = quote.getPrice();
                BigDecimal entryPrice = trade.getPrice();
                BigDecimal pnlPerShare = currentPrice.subtract(entryPrice);
                BigDecimal totalPnl = pnlPerShare.multiply(BigDecimal.valueOf(trade.getQuantity()));
                BigDecimal pnlPercent = pnlPerShare.divide(entryPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

                trade.setPnl(totalPnl);
                trade.setPnlPercent(pnlPercent);

                // 1. Kiểm tra Cắt Lỗ Vi Phạm (CRITICAL STOP LOSS)
                if (trade.getStopLoss() != null && currentPrice.compareTo(trade.getStopLoss()) <= 0) {
                    alertService.createAlert(
                        trade.getSymbol(),
                        "STOP_LOSS_WARNING",
                        "🚨 [CẮT LỖ KHẨN CẤP] " + trade.getSymbol() + " đã chạm ngưỡng dừng lỗ!",
                        String.format("Giá hiện tại %s đ đã thủng ngưỡng cắt lỗ kế hoạch %s đ (Lỗ: %s%%, tương đương -%s đ). Hãy dứt khoát bán bảo vệ vốn theo kỷ luật!",
                            currentPrice, trade.getStopLoss(), pnlPercent.setScale(2, RoundingMode.HALF_UP), totalPnl.abs()),
                        currentPrice,
                        "CRITICAL"
                    );
                }

                // 2. Kiểm tra Đạt Mục Tiêu Chốt Lời (TAKE PROFIT HIT)
                if (trade.getTakeProfit() != null && currentPrice.compareTo(trade.getTakeProfit()) >= 0) {
                    alertService.createAlert(
                        trade.getSymbol(),
                        "TAKE_PROFIT_TRIGGERED",
                        "🎯 [CHỐT LỜI ĐẠT CHUẨN] " + trade.getSymbol() + " đã đạt mục tiêu lợi nhuận!",
                        String.format("Giá hiện tại %s đ đã chạm mục tiêu chốt lời %s đ (Lãi: +%s%%, tương đương +%s đ). Đề xuất hiện thực hóa 50%% lợi nhuận và nâng trailing stop!",
                            currentPrice, trade.getTakeProfit(), pnlPercent.setScale(2, RoundingMode.HALF_UP), totalPnl),
                        currentPrice,
                        "INFO"
                    );
                }

                // 3. Tự Động Nâng Stop Loss Hòa Vốn (Trailing Stop To Breakeven) khi lãi > 10%
                if (pnlPercent.compareTo(BigDecimal.valueOf(10.0)) >= 0 && trade.getStopLoss() != null && trade.getStopLoss().compareTo(entryPrice) < 0) {
                    trade.setStopLoss(entryPrice);
                    alertService.createAlert(
                        trade.getSymbol(),
                        "DISCIPLINE_REMINDER",
                        "🛡️ [NÂNG STOP LOSS HÒA VỐN] " + trade.getSymbol() + " đã lãi trên 10%",
                        String.format("Vị thế %s đã sinh lời +%s%%. Hệ thống tự động dời điểm cắt lỗ lên giá vốn %s đ để đảm bảo giao dịch không thể thua lỗ!",
                            trade.getSymbol(), pnlPercent.setScale(2, RoundingMode.HALF_UP), entryPrice),
                        currentPrice,
                        "INFO"
                    );
                }

                tradeRepository.save(trade);
            } catch (Exception e) {
                log.error("Error monitoring trade {}: {}", trade.getSymbol(), e.getMessage());
            }
        }

        // Cập nhật giá cho Watchlist
        List<Watchlist> watchlists = watchlistRepository.findAll();
        for (Watchlist w : watchlists) {
            try {
                StockQuote q = stockPriceService.getQuote(w.getSymbol());
                if (q.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                    w.setCurrentPrice(q.getPrice());
                    watchlistRepository.save(w);
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Nhắc nhở kỷ luật 15 phút cuối phiên (14:15 VN time)
     */
    @Scheduled(cron = "0 15 14 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
    public void goldenFifteenMinutesReminder() {
        log.info("Scheduler: 14:15 Golden 15 Minutes Alert triggered");
        alertService.createAlert(
            "VN-INDEX",
            "DISCIPLINE_REMINDER",
            "⚡ [14:15 - 15 PHÚT VÀNG QUYẾT ĐỊNH XU HƯỚNG]",
            "Dòng tiền tạo lập Big Boys bắt đầu hành động. Rà soát lại toàn bộ danh mục để đưa ra quyết định mua bùng nổ theo đà hoặc dứt khoát cắt lỗ trước phiên ATC.",
            BigDecimal.ZERO,
            "WARNING"
        );
    }

    /**
     * Tổng kết phiên giao dịch và đánh giá kỷ luật (15:00 VN time)
     */
    @Scheduled(cron = "0 0 15 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
    public void postMarketReview() {
        log.info("Scheduler: Post-market review executed");
        alertService.createAlert(
            "PORTFOLIO",
            "DISCIPLINE_REMINDER",
            "📔 [TỔNG KẾT PHIÊN] Đã hết giờ giao dịch - Ghi chép nhật ký",
            "Thị trường đã đóng cửa. Hãy ghi lại cảm xúc, sai sót tâm lý và bài học của các lệnh hôm nay vào Nhật Ký Giao Dịch.",
            BigDecimal.ZERO,
            "INFO"
        );
    }
}