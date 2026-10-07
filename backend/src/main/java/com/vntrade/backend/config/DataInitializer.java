package com.vntrade.backend.config;

import com.vntrade.backend.entity.Alert;
import com.vntrade.backend.entity.ScheduleEvent;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.entity.Watchlist;
import com.vntrade.backend.repository.AlertRepository;
import com.vntrade.backend.repository.ScheduleEventRepository;
import com.vntrade.backend.repository.TradeRepository;
import com.vntrade.backend.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final TradeRepository tradeRepository;
    private final WatchlistRepository watchlistRepository;
    private final ScheduleEventRepository scheduleEventRepository;
    private final AlertRepository alertRepository;

    @Override
    public void run(String... args) {
        initTrades();
        initWatchlist();
        initScheduleEvents();
        initAlerts();
    }

    private void initTrades() {
        // Chuẩn bị tài khoản sạch sẽ 100% tiền mặt cho phiên Live Trace ngày đầu tiên
        log.info("Hệ thống khởi động chế độ Live Trace Ngày 1: Không nạp lệnh mẫu giả, sẵn sàng chờ thị trường mở cửa!");
    }

    private void initWatchlist() {
        if (watchlistRepository.count() > 0) return;
        log.info("Seeding watchlist data...");

        List<Watchlist> watchlists = List.of(
            Watchlist.builder()
                .symbol("FPT")
                .exchange("HOSE")
                .targetPrice(BigDecimal.valueOf(155000))
                .currentPrice(BigDecimal.valueOf(141000))
                .stopLoss(BigDecimal.valueOf(131000))
                .takeProfit(BigDecimal.valueOf(160000))
                .rsi(BigDecimal.valueOf(58.5))
                .notes("Leader dòng công nghệ, tăng trưởng EPS 20%+, tiếp tục nắm giữ")
                .build(),

            Watchlist.builder()
                .symbol("HPG")
                .exchange("HOSE")
                .targetPrice(BigDecimal.valueOf(34000))
                .currentPrice(BigDecimal.valueOf(29800))
                .stopLoss(BigDecimal.valueOf(27500))
                .takeProfit(BigDecimal.valueOf(35000))
                .rsi(BigDecimal.valueOf(54.2))
                .notes("Dung Quất 2 sắp vận hành thương mại, định giá P/B còn rẻ")
                .build(),

            Watchlist.builder()
                .symbol("SSI")
                .exchange("HOSE")
                .targetPrice(BigDecimal.valueOf(40000))
                .currentPrice(BigDecimal.valueOf(35000))
                .stopLoss(BigDecimal.valueOf(32500))
                .takeProfit(BigDecimal.valueOf(41000))
                .rsi(BigDecimal.valueOf(62.1))
                .notes("Hưởng lợi hệ thống KRX & nâng hạng thị trường FTSE Russell")
                .build(),

            Watchlist.builder()
                .symbol("TCB")
                .exchange("HOSE")
                .targetPrice(BigDecimal.valueOf(28000))
                .currentPrice(BigDecimal.valueOf(24600))
                .stopLoss(BigDecimal.valueOf(22800))
                .takeProfit(BigDecimal.valueOf(29000))
                .rsi(BigDecimal.valueOf(51.0))
                .notes("CASA cao nhất ngành, định giá hấp dẫn cho mục tiêu trung hạn")
                .build(),

            Watchlist.builder()
                .symbol("MWG")
                .exchange("HOSE")
                .targetPrice(BigDecimal.valueOf(78000))
                .currentPrice(BigDecimal.valueOf(68500))
                .stopLoss(BigDecimal.valueOf(63500))
                .takeProfit(BigDecimal.valueOf(80000))
                .rsi(BigDecimal.valueOf(48.0))
                .notes("Bách Hóa Xanh có lãi, chuỗi EraBlue Indonesia mở rộng mạnh")
                .build(),

            Watchlist.builder()
                .symbol("VHM")
                .exchange("HOSE")
                .targetPrice(BigDecimal.valueOf(48000))
                .currentPrice(BigDecimal.valueOf(42300))
                .stopLoss(BigDecimal.valueOf(39500))
                .takeProfit(BigDecimal.valueOf(47000))
                .rsi(BigDecimal.valueOf(38.5))
                .notes("Kế hoạch mua lại cổ phiếu quỹ 370 triệu cp, rủi ro nợ vay cần theo dõi")
                .build()
        );

        watchlistRepository.saveAll(watchlists);
    }

    private void initScheduleEvents() {
        if (scheduleEventRepository.count() > 0) return;
        log.info("Seeding schedule events...");

        List<ScheduleEvent> events = List.of(
            ScheduleEvent.builder()
                .title("Đáo Hạn Hợp Đồng Phái Sinh VN30F (Tháng 10)")
                .eventDate(LocalDate.now().plusDays(11))
                .eventType("Đáo Hạn Phái Sinh")
                .notes("Biến động mạnh phiên ATC, chỉ số trụ bị giằng co mạnh. Hạn chế mở mới vị thế.")
                .build(),

            ScheduleEvent.builder()
                .title("Kỳ Cơ Cấu Danh Mục Các Quỹ ETF Ngoại")
                .eventDate(LocalDate.now().plusDays(19))
                .eventType("Cơ Cấu Quỹ ETF")
                .notes("Khối lượng đột biến các mã Bluechip trong rổ VN30 và Diamond ETF.")
                .build(),

            ScheduleEvent.builder()
                .title("Mùa Công Bố Báo Cáo Tài Chính & KQKD Quý 3/2026")
                .eventDate(LocalDate.now().plusDays(24))
                .eventType("Báo Cáo Tài Chính")
                .notes("Tập trung các doanh nghiệp có tăng trưởng lợi nhuận đột biến > 25% (Công nghệ, Thép, Bán lẻ).")
                .build(),

            ScheduleEvent.builder()
                .title("Kỳ Họp Lãi Suất FOMC - Cục Dự Trữ Liên Bang Mỹ FED")
                .eventDate(LocalDate.now().plusDays(32))
                .eventType("Vĩ Mô Quốc Tế")
                .notes("Dự báo lộ trình hạ lãi suất của FED, tác động trực tiếp tới dòng vốn ngoại và tỷ giá USD/VND.")
                .build()
        );

        scheduleEventRepository.saveAll(events);
    }

    private void initAlerts() {
        if (alertRepository.count() > 0) return;
        log.info("Seeding initial alerts...");

        List<Alert> alerts = List.of(
            Alert.builder()
                .symbol("FPT")
                .alertType("BREAKOUT_SIGNAL")
                .title("🚀 Tín hiệu bùng nổ khối lượng (Breakout Vol)")
                .message("FPT vượt đỉnh thời đại kèm thanh khoản gấp 1.85 lần trung bình 20 phiên. Điểm mua Pocket Pivot chuẩn xác.")
                .priceAtAlert(BigDecimal.valueOf(141000))
                .severity("INFO")
                .isRead(false)
                .build(),

            Alert.builder()
                .symbol("HPG")
                .alertType("GOLDEN_CROSS")
                .title("⚡ Golden Cross MA20 vượt MA50")
                .message("Đường MA20 cắt lên đường MA50 xác nhận xu hướng tăng trung hạn đã hình thành.")
                .priceAtAlert(BigDecimal.valueOf(29800))
                .severity("INFO")
                .isRead(false)
                .build()
        );

        alertRepository.saveAll(alerts);
    }
}
