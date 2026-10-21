package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.dto.StockScanResult;
import com.vntrade.backend.entity.SignalValidationTrade;
import com.vntrade.backend.repository.SignalValidationTradeRepository;
import com.vntrade.backend.service.decision.QuantitativeStrategyEngine;
import com.vntrade.backend.service.decision.StrategyService;
import com.vntrade.backend.service.marketdata.StockPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignalValidatorService {

    private final SignalValidationTradeRepository validationRepository;
    private final StrategyService strategyService;
    private final StockPriceService stockPriceService;

    private boolean isRunning = true;
    private int cycleCount = 0;
    private final List<String> validatorLogs = new CopyOnWriteArrayList<>();

    // Universe mở rộng kiểm chứng: 50 mã VN50 cốt lõi + các mã Midcap/SMCAP thanh khoản
    public static final List<String> VALIDATION_SYMBOLS = StrategyService.FOCUS_SYMBOLS;

    @jakarta.annotation.PostConstruct
    public void init() {
        addLog("🚀 [JOB 2 KHỞI CHẠY] Signal Validator: Đã kích hoạt chế độ Mở Toang Cánh Cửa — trực canh kiểm chứng toàn bộ tín hiệu mua không giới hạn vốn!");
        addLog("🔬 [NGUYÊN TẮC KIỂM CHỨNG] Không bị ràng buộc bởi tiền mặt 100tr, trần ngành 35% hay giới hạn 4 mã. Bắt trọn vẹn mọi điểm nổ Score ≥ 70!");
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void executeValidatorCycle() {
        if (!isRunning) return;

        // Kiểm tra giờ giao dịch sàn HOSE/HNX (09:15 - 11:30 và 13:00 - 14:45 từ Thứ 2 đến Thứ 6)
        LocalDate today = LocalDate.now();
        java.time.DayOfWeek dow = today.getDayOfWeek();
        boolean isWeekend = (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY);
        LocalTime nowTime = LocalTime.now();
        boolean isTradingSession = !isWeekend && (
            (nowTime.isAfter(LocalTime.of(9, 15)) && nowTime.isBefore(LocalTime.of(11, 30))) ||
            (nowTime.isAfter(LocalTime.of(13, 0)) && nowTime.isBefore(LocalTime.of(14, 45)))
        );

        // 1. Luôn theo dõi cập nhật các lệnh PENDING đã ghi nhận
        trackPendingSignals();

        if (!isTradingSession) {
            return;
        }

        // 2. MỞ TOANG CÁNH CỬA: Quét toàn bộ cổ phiếu tìm tín hiệu MUA
        // KHÔNG bị giới hạn bởi: Vốn 100tr, Tiền mặt khả dụng, Trần ngành 35% NAV, hay Tối đa 4 vị thế!
        try {
            List<StockScanResult> scans = strategyService.scanAllStocks();
            int newSignalsCount = 0;

            for (StockScanResult scan : scans) {
                // Tín hiệu đạt chuẩn công thức (Score >= 70 và có khuyến nghị BUY)
                if (scan.getConfidenceScore() >= 70 && scan.getAction() != null && scan.getAction().contains("BUY")) {
                    String symbol = scan.getSymbol().toUpperCase();

                    // Kiểm tra xem hôm nay hoặc mã này đã có lệnh PENDING mở gần đây chưa để tránh duplicate spam
                    boolean alreadyPending = validationRepository.findAllPending().stream()
                        .anyMatch(t -> t.getSymbol().equalsIgnoreCase(symbol));

                    if (alreadyPending) {
                        continue;
                    }

                    boolean alreadySignaledToday = validationRepository.existsBySymbolAndSignalDate(symbol, today);
                    if (alreadySignaledToday) {
                        continue;
                    }

                    StockQuote liveQuote = stockPriceService.getQuote(symbol);
                    BigDecimal entryPrice = (liveQuote != null && liveQuote.getPrice() != null && liveQuote.getPrice().compareTo(BigDecimal.ZERO) > 0)
                        ? liveQuote.getPrice()
                        : scan.getPrice();

                    if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

                    BigDecimal stopLoss = scan.getStopLoss() != null
                        ? scan.getStopLoss()
                        : QuantitativeStrategyEngine.roundToVietnameseTick(entryPrice.multiply(BigDecimal.valueOf(0.93)));

                    BigDecimal takeProfit = scan.getTargetPrice() != null
                        ? scan.getTargetPrice()
                        : QuantitativeStrategyEngine.roundToVietnameseTick(entryPrice.multiply(BigDecimal.valueOf(1.10)));

                    SignalValidationTrade record = SignalValidationTrade.builder()
                        .symbol(symbol)
                        .signalDate(today)
                        .entryPrice(entryPrice)
                        .stopLoss(stopLoss)
                        .takeProfit(takeProfit)
                        .confidenceScore(scan.getConfidenceScore())
                        .signalReason(String.format("Tín hiệu %s (Score %d/100) | SL: %s | TP: %s",
                            scan.getSignalType(), scan.getConfidenceScore(), stopLoss, takeProfit))
                        .outcome("PENDING")
                        .currentPrice(entryPrice)
                        .currentPnlPercent(BigDecimal.ZERO)
                        .mfePercent(BigDecimal.ZERO)
                        .maePercent(BigDecimal.ZERO)
                        .daysTracked(0)
                        .build();

                    validationRepository.save(record);
                    newSignalsCount++;

                    addLog(String.format("🚪 [MỞ TOANG CÁNH CỬA] Phát hiện tín hiệu %s: Giá %s đ | Score %dđ | SL %s đ | TP %s đ (Ghi nhận kiểm chứng không giới hạn vốn)",
                        symbol, entryPrice, scan.getConfidenceScore(), stopLoss, takeProfit));
                }
            }

            cycleCount++;
            if (cycleCount == 1 || cycleCount % 5 == 0) {
                var stats = getStats();
                addLog(String.format("📊 [JOB 2 VALIDATOR THỐNG KÊ] Tổng: %s tín hiệu | Đang theo dõi: %s | Thắng: %s - Thua: %s | Win Rate: %s%% | Kỳ vọng: %s%%",
                    stats.get("totalSignals"), stats.get("pendingCount"), stats.get("winCount"), stats.get("lossCount"),
                    stats.get("winRate"), stats.get("expectancy")));
            }

        } catch (Exception e) {
            log.error("Lỗi trong chu kỳ quét SignalValidator: {}", e.getMessage());
        }
    }

    /**
     * Bám đuôi các tín hiệu PENDING để cập nhật giá, PnL, MFE, MAE và đóng vị thế khi chạm SL/TP
     */
    public void trackPendingSignals() {
        List<SignalValidationTrade> pendings = validationRepository.findAllPending();
        LocalDate today = LocalDate.now();

        for (SignalValidationTrade trade : pendings) {
            try {
                StockQuote quote = stockPriceService.getQuote(trade.getSymbol());
                if (quote == null || quote.getPrice() == null || quote.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal curPrice = quote.getPrice();
                trade.setCurrentPrice(curPrice);

                BigDecimal pnlDiff = curPrice.subtract(trade.getEntryPrice());
                BigDecimal pnlPct = pnlDiff.divide(trade.getEntryPrice(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                trade.setCurrentPnlPercent(pnlPct);

                // Cập nhật MFE (đỉnh lãi cao nhất)
                if (trade.getMfePercent() == null || pnlPct.compareTo(trade.getMfePercent()) > 0) {
                    trade.setMfePercent(pnlPct);
                }
                // Cập nhật MAE (đáy lỗ sâu nhất)
                if (trade.getMaePercent() == null || pnlPct.compareTo(trade.getMaePercent()) < 0) {
                    trade.setMaePercent(pnlPct);
                }

                int days = (int) ChronoUnit.DAYS.between(trade.getSignalDate(), today);
                trade.setDaysTracked(days);

                // 1. Chạm TAKE PROFIT
                if (trade.getTakeProfit() != null && curPrice.compareTo(trade.getTakeProfit()) >= 0) {
                    trade.setOutcome("WIN");
                    trade.setClosedDate(today);
                    validationRepository.save(trade);
                    addLog(String.format("🎯 [KIỂM CHỨNG THÀNH CÔNG] %s chạm Take Profit %s đ (Lãi +%s%% sau %d ngày). Công thức phát huy hiệu quả!",
                        trade.getSymbol(), trade.getTakeProfit(), pnlPct, days));
                    continue;
                }

                // 2. Chạm STOP LOSS
                if (trade.getStopLoss() != null && curPrice.compareTo(trade.getStopLoss()) <= 0) {
                    trade.setOutcome("STOPPED_OUT");
                    trade.setClosedDate(today);
                    validationRepository.save(trade);
                    addLog(String.format("🛑 [KIỂM CHỨNG CẮT LỖ] %s chạm Stop Loss %s đ (Lỗ %s%% sau %d ngày). Ghi nhận vi phạm rủi ro.",
                        trade.getSymbol(), trade.getStopLoss(), pnlPct, days));
                    continue;
                }

                // 3. Hết hạn theo dõi (Quá 15 ngày giao dịch)
                if (days >= 15) {
                    trade.setClosedDate(today);
                    if (pnlPct.compareTo(BigDecimal.ZERO) > 0) {
                        trade.setOutcome("WIN");
                    } else {
                        trade.setOutcome("LOSS");
                    }
                    validationRepository.save(trade);
                    addLog(String.format("⏰ [KẾT THÚC THEO DÕI 15 NGÀY] %s đóng ở giá %s đ (PnL: %s%%). Kết quả: %s.",
                        trade.getSymbol(), curPrice, pnlPct, trade.getOutcome()));
                    continue;
                }

                validationRepository.save(trade);
            } catch (Exception e) {
                log.debug("Lỗi theo dõi pending signal {}: {}", trade.getSymbol(), e.getMessage());
            }
        }
    }

    public Map<String, Object> getStats() {
        List<SignalValidationTrade> all = validationRepository.findAllByOrderBySignalDateDesc();
        List<SignalValidationTrade> closed = all.stream().filter(t -> !"PENDING".equals(t.getOutcome())).toList();
        List<SignalValidationTrade> pending = all.stream().filter(t -> "PENDING".equals(t.getOutcome())).toList();

        // Thống kê các lệnh đã đóng (Closed outcomes)
        long winCount = closed.stream().filter(t -> "WIN".equals(t.getOutcome()) || (t.getCurrentPnlPercent() != null && t.getCurrentPnlPercent().compareTo(BigDecimal.ZERO) > 0)).count();
        long lossCount = closed.size() - winCount;

        BigDecimal winRate = closed.isEmpty() ? BigDecimal.ZERO :
            BigDecimal.valueOf(winCount * 100.0 / closed.size()).setScale(1, RoundingMode.HALF_UP);

        BigDecimal sumPnl = closed.stream()
            .map(t -> t.getCurrentPnlPercent() != null ? t.getCurrentPnlPercent() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectancy = closed.isEmpty() ? BigDecimal.ZERO :
            sumPnl.divide(BigDecimal.valueOf(closed.size()), 2, RoundingMode.HALF_UP);

        BigDecimal avgWin = winCount == 0 ? BigDecimal.ZERO :
            closed.stream()
                .filter(t -> t.getCurrentPnlPercent() != null && t.getCurrentPnlPercent().compareTo(BigDecimal.ZERO) > 0)
                .map(SignalValidationTrade::getCurrentPnlPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(winCount), 2, RoundingMode.HALF_UP);

        BigDecimal avgLoss = lossCount == 0 ? BigDecimal.ZERO :
            closed.stream()
                .filter(t -> t.getCurrentPnlPercent() != null && t.getCurrentPnlPercent().compareTo(BigDecimal.ZERO) <= 0)
                .map(SignalValidationTrade::getCurrentPnlPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(lossCount), 2, RoundingMode.HALF_UP);

        // Thống kê các lệnh đang bám đuôi realtime (Realtime Pending Metrics)
        long pendingGreen = pending.stream().filter(t -> t.getCurrentPnlPercent() != null && t.getCurrentPnlPercent().compareTo(BigDecimal.ZERO) > 0).count();
        long pendingRed = pending.stream().filter(t -> t.getCurrentPnlPercent() != null && t.getCurrentPnlPercent().compareTo(BigDecimal.ZERO) < 0).count();
        long pendingRef = pending.size() - pendingGreen - pendingRed;

        BigDecimal pendingWinRate = pending.isEmpty() ? BigDecimal.ZERO :
            BigDecimal.valueOf(pendingGreen * 100.0 / pending.size()).setScale(1, RoundingMode.HALF_UP);

        BigDecimal pendingSumPnl = pending.stream()
            .map(t -> t.getCurrentPnlPercent() != null ? t.getCurrentPnlPercent() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingAvgPnl = pending.isEmpty() ? BigDecimal.ZERO :
            pendingSumPnl.divide(BigDecimal.valueOf(pending.size()), 2, RoundingMode.HALF_UP);

        // Tìm mã tăng mạnh nhất và giảm nhiều nhất trong các mã đang bám đuôi
        String bestPerformer = "—";
        String worstPerformer = "—";
        if (!pending.isEmpty()) {
            var best = pending.stream()
                .filter(t -> t.getCurrentPnlPercent() != null)
                .max(Comparator.comparing(SignalValidationTrade::getCurrentPnlPercent))
                .orElse(null);
            if (best != null && best.getCurrentPnlPercent() != null) {
                bestPerformer = String.format("%s (+%s%%)", best.getSymbol(), best.getCurrentPnlPercent().setScale(2, RoundingMode.HALF_UP));
            }

            var worst = pending.stream()
                .filter(t -> t.getCurrentPnlPercent() != null)
                .min(Comparator.comparing(SignalValidationTrade::getCurrentPnlPercent))
                .orElse(null);
            if (worst != null && worst.getCurrentPnlPercent() != null) {
                worstPerformer = String.format("%s (%s%%)", worst.getSymbol(), worst.getCurrentPnlPercent().setScale(2, RoundingMode.HALF_UP));
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalSignals", all.size());
        res.put("pendingCount", pending.size());
        res.put("closedCount", closed.size());
        res.put("winCount", winCount);
        res.put("lossCount", lossCount);
        res.put("winRate", winRate);
        res.put("expectancy", expectancy);
        res.put("avgWinPercent", avgWin);
        res.put("avgLossPercent", avgLoss);

        // Realtime stats
        res.put("pendingGreenCount", pendingGreen);
        res.put("pendingRedCount", pendingRed);
        res.put("pendingRefCount", pendingRef);
        res.put("pendingWinRate", pendingWinRate);
        res.put("pendingAvgPnl", pendingAvgPnl);
        res.put("bestPerformer", bestPerformer);
        res.put("worstPerformer", worstPerformer);

        res.put("universeSize", VALIDATION_SYMBOLS.size());
        res.put("lastScanTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")));
        return res;
    }

    public List<SignalValidationTrade> getRecentSignals() {
        return validationRepository.findAllByOrderBySignalDateDesc();
    }

    public List<String> getValidatorLogs() {
        return validatorLogs;
    }

    public void addLog(String msg) {
        String entry = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + msg;
        validatorLogs.add(0, entry);
        if (validatorLogs.size() > 50) validatorLogs.remove(validatorLogs.size() - 1);
        log.info("SignalValidator: {}", msg);
    }

    public void resetAll() {
        validationRepository.deleteAll();
        validatorLogs.clear();
        addLog("♻️ Đã khởi tạo lại toàn bộ dữ liệu kiểm chứng tín hiệu mở toang cánh cửa.");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
    }
}
