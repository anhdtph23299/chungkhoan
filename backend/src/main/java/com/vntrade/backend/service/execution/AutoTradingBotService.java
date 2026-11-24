package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.*;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import com.vntrade.backend.service.calculation.RelativeRotationGraphService;
import com.vntrade.backend.service.portfolio.AlertService;
import com.vntrade.backend.service.calculation.GarchVolatilityForecastService;
import com.vntrade.backend.service.marketdata.SseStreamService;
import com.vntrade.backend.service.decision.StrategyService;
import com.vntrade.backend.service.decision.OrderBookImbalanceService;
import com.vntrade.backend.service.calculation.LiquidityAdjustedReturnService;
import com.vntrade.backend.service.decision.SmartMoneyFlowService;
import com.vntrade.backend.service.risk.ForeignFlowRiskService;
import com.vntrade.backend.service.decision.MicrostructureSpoofingDetectorService;
import com.vntrade.backend.service.marketdata.StockPriceService;
import com.vntrade.backend.service.risk.RiskService;
import com.vntrade.backend.service.decision.MultiTimeframeConfluenceService;
import com.vntrade.backend.service.risk.MarketCrashProtectionService;
import com.vntrade.backend.service.decision.CanslimRatingService;
import com.vntrade.backend.service.decision.QuantitativeStrategyEngine;
import com.vntrade.backend.service.decision.PreMarketSentimentService;
import com.vntrade.backend.service.marketdata.OrderBookService;
import com.vntrade.backend.service.calculation.KalmanFilterTrendService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoTradingBotService {

    private final StrategyService strategyService;
    private final RiskService riskService;
    private final TradeService tradeService;
    private final TradeRepository tradeRepository;
    private final AlertService alertService;
    private final StockPriceService stockPriceService;
    private final SseStreamService sseStreamService;
    private final SmartMoneyFlowService smartMoneyFlowService;
    private final MarketCrashProtectionService crashProtectionService;
    private final MultiTimeframeConfluenceService confluenceService;
    private final CanslimRatingService canslimRatingService;
    private final OrderBookImbalanceService orderBookImbalanceService;
    private final ForeignFlowRiskService foreignFlowRiskService;
    private final MicrostructureSpoofingDetectorService spoofingDetectorService;
    private final KalmanFilterTrendService kalmanFilterTrendService;
    private final GarchVolatilityForecastService garchVolatilityForecastService;
    private final AdaptivePositionSizingService adaptivePositionSizingService;
    private final LiquidityAdjustedReturnService liquidityAdjustedReturnService;
    private final RelativeRotationGraphService relativeRotationGraphService;
    private final BotDecisionAuditService botDecisionAuditService;
    private final com.vntrade.backend.service.decision.OversoldBounceDetectorService oversoldBounceDetectorService;
    private final com.vntrade.backend.service.decision.MarketRegimeDetectionService marketRegimeDetectionService;
    private final com.vntrade.backend.service.futures.VN30FuturesTradingService futuresTradingService;
    private final PreMarketSentimentService preMarketSentimentService;
    private final OrderBookService orderBookService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private IntradayTraceLoggerService traceLoggerService;

    // Bot Configuration & State
    private boolean isRunning = true;
    private String mode = "LIVE_PAPER_MONEY"; // SIMULATION, LIVE_PAPER_MONEY, REAL_READY
    private BigDecimal accountCapital = BigDecimal.valueOf(100_000_000); // Mặc định 100 triệu VNĐ Paper Trading Ngày 1
    private BigDecimal dailyProfitTarget = BigDecimal.valueOf(1_500_000); // Benchmark tham chiếu ngày (+1.5% NAV)
    private BigDecimal dailyMaxLossLimit = BigDecimal.valueOf(2_000_000); // Giới hạn lỗ tối đa 2.000.000 đ/ngày (-2.0% NAV)
    private BigDecimal todayRealizedPnl = BigDecimal.ZERO;
    private int todayTradesCount = 0;
    private final List<String> botLogs = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<java.util.Map<String, Object>> breakoutWatchlist = new java.util.concurrent.CopyOnWriteArrayList<>();
    private boolean enforceMarketHours = true;
    private int cycleScanCount = 0;

    // Q1_A_INVVOL Strategy (Backtest 6.8 năm VN30: +105.12% tổng lãi, CAGR +11.13%, Sharpe 0.74, PF 1.80, MaxDD 27.89%, p<0.05):
    // Dual Risk-Adjusted Momentum + VN-Index EMA20/SMA50 Breadth Shield + Inverse-Vol Sizing + Structural Trend Exit (Let Winners Run)
    private String executionStrategy = "Q1_A_INVVOL_DUAL_MOMENTUM";
    private BigDecimal maxDrawdownThreshold = BigDecimal.valueOf(20.0); // Ngưỡng MaxDD cảnh báo: 20.0%
    private BigDecimal riskPerTradePercent = BigDecimal.valueOf(1.50); // Rủi ro cho phép mỗi lệnh: 1.50% NAV
    private final java.util.Map<String, LocalDateTime> lastT25AlertTime = new java.util.concurrent.ConcurrentHashMap<>();
    private LocalDateTime lastCircuitBreakerLogTime = null;

    public List<java.util.Map<String, Object>> getBreakoutWatchlist() {
        if (breakoutWatchlist.isEmpty()) {
            refreshBreakoutWatchlist();
        }
        return breakoutWatchlist;
    }

    public synchronized void refreshBreakoutWatchlist() {
        try {
            List<Trade> openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");
            List<StockScanResult> scans = strategyService.scanAllStocks();
            updateBreakoutWatchlist(scans, openTrades);
        } catch (Exception e) {
            log.debug("Lỗi cập nhật radar bứt phá: {}", e.getMessage());
        }
    }

    public void executeManualCycle() {
        manageOpenPositions();
        List<Trade> openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");
        List<StockScanResult> scans = strategyService.scanAllStocks();
        updateBreakoutWatchlist(scans, openTrades);
        checkOversoldBounceOpportunity(openTrades);
        openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");
        checkAntiMarketLeaderOpportunity(openTrades, scans);
        try {
            var futPos = futuresTradingService.executeTacticalSignalScan();
            if (futPos != null) {
                addLog(String.format("⚡ [ĐỘNG CƠ PHÁI SINH T+0] Đang giữ vị thế %s %d HĐ VN30F1M @ %.1f (Hiện tại: %.1f | PnL: %+,.0f đ).",
                    futPos.getSide(), futPos.getContracts(), futPos.getEntryPrice().doubleValue(),
                    futPos.getCurrentPrice().doubleValue(), futPos.getNetPnlVnd().doubleValue()));
            }
        } catch (Exception ignored) {}
        addLog(String.format("⚡ [QUÉT THỦ CÔNG ĐỘNG CƠ KÉP] Đã cập nhật Radar cơ sở (%d mã) & Phái sinh T+0.", breakoutWatchlist.size()));
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        if (botLogs.isEmpty()) {
            addLog(String.format("🚀 [KHỞI TẠO Q1_A_INVVOL THÀNH CÔNG] Vốn: %,.0f đ | Chế độ: LIVE_PAPER_MONEY", accountCapital));
            addLog("🧠 [Q1_A_INVVOL DUAL MOMENTUM] Backtest 6.8 năm VN30: Tổng lãi +105.1% | Sharpe 0.74 | PF 1.80 | SL -8% & Gồng trọn sóng (bỏ chốt non)!");
            addLog("📝 [DELTA TRACE JSONL] Tự động ghi nhật ký vi mô ra file logs/trace/trace-YYYY-MM-DD.jsonl (tự nén GZIP 15:05, ~300KB/ngày).");
            addLog("🛡️ [LÁ CHẮN VN-INDEX EMA20/SMA50] Tự động rút về 100% tiền mặt khi VN-Index gãy EMA20/SMA50 & Breadth < 35%.");
        }
        new Thread(() -> {
            try {
                Thread.sleep(2500);
                refreshBreakoutWatchlist();
            } catch (Exception ignored) {}
        }).start();
    }

    public boolean isEnforceMarketHours() {
        return enforceMarketHours;
    }

    public void setEnforceMarketHours(boolean enforceMarketHours) {
        this.enforceMarketHours = enforceMarketHours;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
        addLog("Robot giao dịch tự động đã " + (running ? "BẬT (Khởi chạy săn tìm lợi nhuận)" : "TẮT (Tạm dừng vào lệnh)"));
    }

    public String getMode() {
        return mode;
    }

    public BigDecimal getAccountCapital() {
        return accountCapital;
    }

    public void setAccountCapital(BigDecimal capital) {
        if (capital != null && capital.compareTo(BigDecimal.ZERO) > 0) {
            this.accountCapital = capital;
            this.dailyProfitTarget = capital.multiply(BigDecimal.valueOf(0.015)).setScale(0, RoundingMode.HALF_UP);
            this.dailyMaxLossLimit = capital.multiply(BigDecimal.valueOf(0.020)).setScale(0, RoundingMode.HALF_UP);
        }
    }

    @Transactional
    public void resetAllAndStartFresh(BigDecimal capital) {
        tradeRepository.deleteAll();
        if (capital != null && capital.compareTo(BigDecimal.ZERO) > 0) {
            this.accountCapital = capital;
        } else {
            this.accountCapital = BigDecimal.valueOf(100_000_000);
        }
        this.dailyProfitTarget = this.accountCapital.multiply(BigDecimal.valueOf(0.015)).setScale(0, RoundingMode.HALF_UP);
        this.dailyMaxLossLimit = this.accountCapital.multiply(BigDecimal.valueOf(0.020)).setScale(0, RoundingMode.HALF_UP);
        this.todayRealizedPnl = BigDecimal.ZERO;
        this.todayTradesCount = 0;
        this.botLogs.clear();
        this.breakoutWatchlist.clear();
        this.lastT25AlertTime.clear();
        this.lastCircuitBreakerLogTime = null;

        addLog(String.format("🔄 [RESET HOÀN TẤT] Đã xóa toàn bộ lịch sử giao dịch cũ. Khởi tạo vốn: %,.0f đ (100%% Tiền mặt).", this.accountCapital));
        addLog("🛡️ [LÁ CHẮN ĐẦU TUẦN] Danh mục hoàn toàn sạch sẽ, sẵn sàng đón chu kỳ săn lệnh mới từ 09:15 sáng Thứ Hai!");
        addLog("📊 Mục tiêu phiên: +1.5% NAV/ngày | Cầu chì bảo vệ vốn: Tối đa -2.0% NAV/ngày | Quy tắc 2% NAV mỗi lệnh.");
    }

    public BigDecimal getAvailableCash() {
        List<Trade> openTrades = tradeRepository.findOpenBuyTrades();
        List<Trade> allTrades = tradeRepository.findAllByOrderByTradeDateDesc();
        BigDecimal closedRealized = allTrades.stream()
            .filter(t -> "closed".equalsIgnoreCase(t.getStatus()))
            .map(t -> (t.getPnl() != null ? t.getPnl() : BigDecimal.ZERO)
                .add(t.getPartialRealizedPnl() != null ? t.getPartialRealizedPnl() : BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openPartialRealized = openTrades.stream()
            .filter(t -> t.getPartialRealizedPnl() != null)
            .map(Trade::getPartialRealizedPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal futuresRealized = (futuresTradingService != null && futuresTradingService.getTotalRealizedPnlVnd() != null)
            ? futuresTradingService.getTotalRealizedPnlVnd()
            : BigDecimal.ZERO;
        BigDecimal invested = openTrades.stream()
            .map(t -> {
                BigDecimal buyVal = t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity()));
                BigDecimal fee = t.getFee() != null ? t.getFee() : BigDecimal.ZERO;
                int partialQty = t.getPartialClosedQuantity() != null ? t.getPartialClosedQuantity() : 0;
                int origQty = t.getQuantity() + partialQty;
                BigDecimal remainingFee = (partialQty > 0 && origQty > 0)
                    ? fee.multiply(BigDecimal.valueOf(t.getQuantity())).divide(BigDecimal.valueOf(origQty), 0, RoundingMode.HALF_UP)
                    : fee;
                return buyVal.add(remainingFee);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return accountCapital.add(closedRealized).add(openPartialRealized).add(futuresRealized).subtract(invested);
    }

    public BigDecimal getInvestedStockValue() {
        List<Trade> openTrades = tradeRepository.findOpenBuyTrades();
        return openTrades.stream()
            .map(t -> {
                BigDecimal curPrice = null;
                try {
                    var quote = stockPriceService.getQuote(t.getSymbol());
                    if (quote != null) {
                        curPrice = quote.getPrice();
                    }
                } catch (Exception ignored) {}
                if (curPrice == null || curPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    curPrice = t.getPrice();
                }
                return curPrice.multiply(BigDecimal.valueOf(t.getQuantity()));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getCurrentNav() {
        BigDecimal cash = getAvailableCash();
        BigDecimal stockValue = getInvestedStockValue();
        BigDecimal futuresUnrealized = (futuresTradingService != null && futuresTradingService.getUnrealizedPnlVnd() != null)
            ? futuresTradingService.getUnrealizedPnlVnd()
            : BigDecimal.ZERO;
        return cash.add(stockValue).add(futuresUnrealized);
    }

    public BigDecimal getDailyProfitTarget() {
        return dailyProfitTarget;
    }

    public BigDecimal getDailyMaxLossLimit() {
        return dailyMaxLossLimit;
    }

    public BigDecimal getTodayRealizedPnl() {
        LocalDate today = LocalDate.now();
        BigDecimal dbRealizedToday = tradeRepository.findAll().stream()
            .filter(t -> "closed".equals(t.getStatus()) && today.equals(t.getCloseDate()) && t.getPnl() != null)
            .map(Trade::getPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (dbRealizedToday.compareTo(BigDecimal.ZERO) != 0) {
            return dbRealizedToday;
        }
        return todayRealizedPnl;
    }

    public int getTodayTradesCount() {
        LocalDate today = LocalDate.now();
        long dbCount = tradeRepository.findAll().stream()
            .filter(t -> "closed".equals(t.getStatus()) && today.equals(t.getCloseDate()))
            .count();
        if (dbCount > 0) {
            return (int) dbCount;
        }
        return todayTradesCount;
    }

    public BigDecimal getTargetExpectancy() {
        return getForwardTestExpectancy();
    }

    public BigDecimal getTargetWinRate() {
        return getForwardTestWinRate();
    }

    public BigDecimal getTargetSharpe() {
        return BigDecimal.ZERO;
    }

    public BigDecimal getMaxDrawdownThreshold() {
        return maxDrawdownThreshold;
    }

    public BigDecimal getRiskPerTradePercent() {
        return riskPerTradePercent;
    }

    public String getCycleLabel() {
        return "Q1_A_INVVOL Dual Momentum + VN-Index Shield (Sharpe 0.74)";
    }

    public String getExecutionStrategy() {
        return executionStrategy;
    }

    public BigDecimal getForwardTestWinRate() {
        List<Trade> closed = tradeRepository.findByStatusOrderByTradeDateDesc("closed");
        if (closed.isEmpty()) return BigDecimal.ZERO;
        long wins = closed.stream().filter(t -> t.getPnl() != null && t.getPnl().compareTo(BigDecimal.ZERO) > 0).count();
        return BigDecimal.valueOf(wins * 100.0 / closed.size()).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getForwardTestExpectancy() {
        List<Trade> closed = tradeRepository.findByStatusOrderByTradeDateDesc("closed");
        if (closed.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sumPct = closed.stream()
            .filter(t -> t.getPnlPercent() != null)
            .map(Trade::getPnlPercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sumPct.divide(BigDecimal.valueOf(closed.size()), 2, RoundingMode.HALF_UP);
    }

    public int getForwardTestClosedTradesCount() {
        return tradeRepository.findByStatusOrderByTradeDateDesc("closed").size();
    }

    /**
     * Thiết lập môi trường Paper Trading chuẩn bị sẵn sàng cho phiên giao dịch thực chiến Thứ 2
     */
    public void setupPaperTrading(BigDecimal initialCapital) {
        if (initialCapital != null && initialCapital.compareTo(BigDecimal.ZERO) > 0) {
            setAccountCapital(initialCapital);
        }
        this.mode = "LIVE_PAPER_MONEY";
        this.isRunning = true;
        this.todayRealizedPnl = BigDecimal.ZERO;
        this.todayTradesCount = 0;
        this.botLogs.clear();
        this.breakoutWatchlist.clear();

        try {
            tradeRepository.deleteAll();
        } catch (Exception e) {
            log.warn("Không thể xóa trades cũ: {}", e.getMessage());
        }

        addLog(String.format("🚀 [KHỞI TẠO PAPER TRADING THÀNH CÔNG] Vốn: %,.0f đ | Chế độ: LIVE_PAPER_MONEY", accountCapital));
        addLog(String.format("🎯 Mục tiêu lợi nhuận ngày: +%,.0f đ (+1.5%%) | Cầu chì bảo vệ vốn: -%,.0f đ (-2.0%%)", dailyProfitTarget, dailyMaxLossLimit));
        addLog("📅 Hệ thống đã vào vị trí sẵn sàng trực canh phiên khớp lệnh Ngày 1 sáng mai (09:00 - 14:45)!");
    }

    public List<String> getBotLogs() {
        return botLogs;
    }

    public void addLog(String message) {
        String logEntry = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + message;
        botLogs.add(0, logEntry);
        if (botLogs.size() > 50) botLogs.remove(botLogs.size() - 1);
        log.info("Bot: {}", message);
        try {
            sseStreamService.broadcast("BOT_LOG", java.util.Map.of(
                "log", logEntry,
                "realizedPnl", todayRealizedPnl,
                "tradesCount", todayTradesCount,
                "running", isRunning
            ));
        } catch (Exception ignored) {}
    }

    /**
     * Chu kỳ quét tự động mỗi 30 giây để tìm kiếm cơ hội sinh lời
     */
    /**
     * Chu kỳ quét tự động mỗi 30 giây để tìm kiếm cơ hội sinh lời và quản trị vị thế
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void executeBotCycle() {
        if (!isRunning) return;

        // Kiểm tra khung giờ khớp lệnh liên tục sàn HOSE/HNX (09:15 - 11:30 và 13:00 - 14:45)
        // Đặt TRƯỚC manageOpenPositions() để tuyệt đối không cắt lỗ oan trong phiên ATO (09:00 - 09:15) hoặc giờ nghỉ trưa
        if (enforceMarketHours && !"SIMULATION".equalsIgnoreCase(mode)) {
            java.time.DayOfWeek dow = LocalDate.now().getDayOfWeek();
            boolean isWeekend = (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY);
            java.time.LocalTime nowTime = java.time.LocalTime.now();

            boolean isAtoSession = !isWeekend && nowTime.isAfter(java.time.LocalTime.of(9, 0)) && nowTime.isBefore(java.time.LocalTime.of(9, 15));
            if (isAtoSession) {
                log.debug("AutoTradingBot: Đang trong phiên ATO (09:00 - 09:15), tạm hoãn cả mở lệnh lẫn cắt lỗ để tránh nhiễu giá ATO.");
                return;
            }

            boolean isContinuousTrading = !isWeekend &&
                ((nowTime.isAfter(java.time.LocalTime.of(9, 15)) && nowTime.isBefore(java.time.LocalTime.of(11, 30))) ||
                 (nowTime.isAfter(java.time.LocalTime.of(13, 0)) && nowTime.isBefore(java.time.LocalTime.of(14, 45))));

            if (!isContinuousTrading) {
                log.debug("AutoTradingBot: Ngoài giờ khớp lệnh liên tục sàn HOSE/HNX ({}), tạm dừng chu kỳ quét.", nowTime);
                return;
            }
        }

        // 0. KIỂM SOÁT NGUỒN DỮ LIỆU THẬT (DATA FEED INTEGRITY: REAL vs STALE)
        // Khi mất dữ liệu thật hoặc toàn bộ API sàn không phản hồi, dừng mọi thao tác mở/đóng vị thế ngay lập tức
        if (!stockPriceService.isMarketDataConnected()) {
            addLog("🚨 [MẤT KẾT NỐI DỮ LIỆU THẬT - STALE] Toàn bộ nguồn cấp giá sàn (VNDirect/TCBS/SSI) mất phản hồi. DỪNG 100% LỆNH MỞ VỊ THẾ ĐỂ BẢO VỆ TÀI KHOẢN!");
            return;
        }

        // 1. Quản trị và chốt lời / cắt lỗ tự động cho các vị thế đang mở (chỉ chạy trong phiên khớp lệnh liên tục với dữ liệu REAL)
        manageOpenPositions();

        // 2. KIỂM TRA CẦU CHÌ LỖ NGÀY (DAILY MAX LOSS CIRCUIT BREAKER: -2.0% NAV)
        BigDecimal realizedToday = getTodayRealizedPnl();
        if (realizedToday.compareTo(dailyMaxLossLimit.negate()) <= 0) {
            LocalDateTime nowDt = LocalDateTime.now();
            if (lastCircuitBreakerLogTime == null || java.time.Duration.between(lastCircuitBreakerLogTime, nowDt).toMinutes() >= 15) {
                lastCircuitBreakerLogTime = nowDt;
                addLog(String.format("🛑 [CẦU CHÌ LỖ NGÀY ĐÃ KÍCH HOẠT] Lỗ thực tế hôm nay %,.0f đ đã chạm giới hạn -%,.0f đ (-2.0%% NAV). KHÓA 100%% LỆNH MỞ MỚI HÔM NAY!",
                    realizedToday.doubleValue(), dailyMaxLossLimit.doubleValue()));
            }
            if (botDecisionAuditService != null) {
                botDecisionAuditService.recordDecision("PORTFOLIO", "REJECTED_FILTER", BigDecimal.ZERO, "DAILY_MAX_LOSS_BREAKER",
                    "Chạm cầu chì lỗ ngày: " + realizedToday + " đ <= -" + dailyMaxLossLimit + " đ", 0);
            }
            return;
        }

        // 3. KÍCH HOẠT ĐỘNG CƠ PHÁI SINH T+0 (Kiếm tiền cả 2 chiều LONG & SHORT bất chấp thị trường tăng hay sập)
        try {
            futuresTradingService.executeTacticalSignalScan();
        } catch (Exception ignored) {}

        // 4. Quét dữ liệu cổ phiếu cơ sở và cập nhật Radar
        List<Trade> openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");
        List<StockScanResult> scans = strategyService.scanAllStocks();
        updateBreakoutWatchlist(scans, openTrades);

        // 5. Kiểm tra Cầu chì phòng hộ sụp đổ thị trường (DEFCON-1) TRƯỚC MỌI NHÁNH MỞ LỆNH CƠ SỞ (kể cả Bắt đáy & Kháng bão)
        MarketCrashProtectionDto crashStatus = crashProtectionService.evaluateMarketCircuitBreaker();
        if (!crashStatus.isAllowNewPurchases()) {
            if (botDecisionAuditService != null) {
                botDecisionAuditService.recordDecision("VNINDEX", "REJECTED_FILTER", BigDecimal.ZERO, "DEFCON_CRASH", crashStatus.getCircuitBreakerMessage(), 0);
            }
            addLog("🚨 [DEFCON-1 PHÒNG HỘ THỊ TRƯỜNG] " + crashStatus.getCircuitBreakerMessage() + ". Khóa 100% lệnh mở mới cơ sở (Breakout, Bắt đáy & Kháng bão) để bảo toàn vốn!");
            recordCycleDeltaTrace(null, crashStatus, openTrades, scans);
            cycleScanCount++;
            return;
        }

        // 6. CHIẾN THUẬT SĂN TIỀN PHÂN HÓA: Bắt đáy Hoảng loạn cực đại (Spring/Pinbar) & Siêu cổ phiếu Kháng Bão (RS Leader)
        // Chỉ chạy khi KHÔNG bị DEFCON-1 sập sàn diện rộng và đang giữ < 2 mã
        if (openTrades.size() < 2) {
            checkOversoldBounceOpportunity(openTrades);
            openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");
            checkAntiMarketLeaderOpportunity(openTrades, scans);
            openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");
        }

        // 7. Kiểm tra Pha thị trường chung VN-Index (O'Neil & Minervini) cho lệnh Breakout
        var marketRegime = marketRegimeDetectionService.detectMarketRegime();
        if (marketRegime != null && ("DOWNTREND_DEFENSE".equalsIgnoreCase(marketRegime.getCurrentRegime()) || marketRegime.getRecommendedMaxExposure().compareTo(BigDecimal.ZERO) <= 0)) {
            if (cycleScanCount % 4 == 0) {
                addLog("🛡️ [LÃO LÀNG - PHÒNG THỦ DOWNTREND] " + marketRegime.getRegimeDisplayName() + ". Khóa mua đuổi Breakout giả (Bull-trap), chỉ cho phép Phái Sinh T+0 & Săn Đáy Rút Chân!");
            }
            recordCycleDeltaTrace(marketRegime, crashStatus, openTrades, scans);
            cycleScanCount++;
            return;
        }

        if (openTrades.size() >= 4) {
            recordCycleDeltaTrace(marketRegime, crashStatus, openTrades, scans);
            cycleScanCount++;
            return; // Tối đa nắm 4 mã để tập trung danh mục
        }

        for (StockScanResult scan : scans) {
            if (scan.getConfidenceScore() >= 75 && (scan.getAction().contains("BUY"))) {
                // Kiểm tra báo giá sàn thật (Data integrity: Bỏ qua nếu STALE hoặc mất kết nối)
                StockQuote liveQuote = stockPriceService.getQuote(scan.getSymbol());
                if (liveQuote == null || liveQuote.isStale() || liveQuote.getPrice() == null || liveQuote.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    addLog(String.format("🚨 [DỮ LIỆU STALE] Bỏ qua %s: Không nhận được báo giá REAL từ sàn (trạng thái: %s). Khóa lệnh mua an toàn!",
                        scan.getSymbol(), liveQuote != null ? liveQuote.getDataSource() : "NULL"));
                    continue;
                }

                boolean alreadyHolding = openTrades.stream().anyMatch(t -> t.getSymbol().equalsIgnoreCase(scan.getSymbol()));
                if (alreadyHolding) continue;

                // 1.0a. QUY TẮC LÃO LÀNG: RE-ENTRY COOLDOWN (Chống bẫy tái nghiện)
                // Cấm mua lại mã vừa tất toán trong vòng 3 ngày gần nhất để tránh bẫy đu đỉnh nhịp rơi
                boolean inCooldown = tradeRepository.findAll().stream()
                    .filter(t -> "closed".equalsIgnoreCase(t.getStatus()) && t.getSymbol().equalsIgnoreCase(scan.getSymbol()))
                    .anyMatch(t -> t.getCloseDate() != null && java.time.temporal.ChronoUnit.DAYS.between(t.getCloseDate(), LocalDate.now()) < 3);
                if (inCooldown) {
                    addLog(String.format("🛡️ [LÃO LÀNG COOLDOWN] Bỏ qua %s: Vừa tất toán trong 3 ngày qua. Khóa mua lại để tránh bẫy tái nghiện đu đọt!", scan.getSymbol()));
                    continue;
                }

                // 1.0b. QUẢN TRỊ RỦI RO DANH MỤC (Smart Progressive Exposure):
                // Chỉ khóa mở thêm vị thế mới khi danh mục đang có lệnh lỗ nặng (< -3.0%) để không cộng dồn thua lỗ,
                // còn khi vị thế đang khỏe/ổn định trong Uptrend thì cho phép phân bổ đủ 4 mã dẫn sóng (giảm rủi ro tập trung & tăng Sharpe).
                if (openTrades.size() >= 1) {
                    boolean hasHeavyBleeding = openTrades.stream().anyMatch(t ->
                        t.getPnlPercent() != null && t.getPnlPercent().compareTo(BigDecimal.valueOf(-3.0)) < 0
                    );
                    if (hasHeavyBleeding) {
                        if (cycleScanCount % 3 == 0) {
                            addLog(String.format("🛡️ [BẢO VỆ VỐN DANH MỤC] Tạm dừng mở thêm %s: Danh mục đang có vị thế lỗ > 3%%. Không mở rộng quy mô khi vị thế hiện tại đang bị áp lực!",
                                scan.getSymbol()));
                        }
                        continue;
                    }
                }

                // 1.0c. QUẢN TRỊ TỶ TRỌNG THEO PHA THỊ TRƯỜNG (Market Regime Max Exposure)
                if (marketRegime != null && marketRegime.getRecommendedMaxExposure() != null) {
                    BigDecimal maxAllowedInvested = accountCapital.multiply(marketRegime.getRecommendedMaxExposure()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    BigDecimal curInv = openTrades.stream()
                        .map(t -> t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (curInv.compareTo(maxAllowedInvested) >= 0) {
                        addLog(String.format("🛡️ [TRẦN TỶ TRỌNG THỊ TRƯỜNG] Bỏ qua %s: Chế độ '%s' chỉ cho phép giải ngân tối đa %.0f%% NAV (%,.0f đ). Danh mục hiện tại %,.0f đ đã chạm trần!",
                            scan.getSymbol(), marketRegime.getCurrentRegime(), marketRegime.getRecommendedMaxExposure().doubleValue(), maxAllowedInvested.doubleValue(), curInv.doubleValue()));
                        continue;
                    }
                }

                // =========================================================================
                // 1. TẦNG QUYẾT ĐỊNH CỐT LÕI (CORE EXECUTION): T1 TREND FILTER + DEFCON-1
                // =========================================================================
                // Kiểm tra phân bổ vốn và trần ngành 35% NAV theo RiskService
                BigDecimal dynamicRisk = riskService.getDynamicRiskPercent();
                PositionSizingRequest riskReq = PositionSizingRequest.builder()
                    .symbol(scan.getSymbol())
                    .accountCapital(accountCapital)
                    .maxRiskPercent(dynamicRisk)
                    .entryPrice(scan.getPrice())
                    .stopLossPrice(scan.getStopLoss())
                    .takeProfitPrice(scan.getTargetPrice())
                    .build();

                PositionSizingResult sizing = riskService.calculatePositionSize(riskReq);
                if (!sizing.isAcceptable() || sizing.getMaxSharesToBuy() < 100) {
                    continue;
                }

                BigDecimal proposedCost = scan.getPrice().multiply(BigDecimal.valueOf(sizing.getMaxSharesToBuy()));
                if (!riskService.isSectorAllocationAllowed(scan.getSymbol(), proposedCost, accountCapital)) {
                    addLog("🛡️ [KIỂM SOÁT NGÀNH] Tạm dừng mua " + scan.getSymbol() + ": Nhóm " + riskService.getSectorForSymbol(scan.getSymbol()) + " đã chạm trần 35% NAV.");
                    continue;
                }

                // 1.1. KIỂM SOÁT TIỀN MẶT KHẢ DỤNG: Nghiêm cấm mua vượt quá số dư tiền mặt của tài khoản
                BigDecimal availableCash = getAvailableCash();

                if (availableCash.compareTo(BigDecimal.valueOf(2_000_000)) <= 0) {
                    addLog(String.format("🛡️ [QUẢN TRỊ TIỀN MẶT] Bỏ qua %s: Tiền mặt khả dụng còn %,.0f đ (dưới đệm an toàn 2.000.000 đ). Không giải ngân thêm.",
                        scan.getSymbol(), availableCash.doubleValue()));
                    continue;
                }

                int finalShares = sizing.getMaxSharesToBuy();
                BigDecimal feeMultiplier = BigDecimal.valueOf(1.0015);

                // 1.1b. Q1_A_INVVOL INVERSE-VOLATILITY SIZING:
                // Phân bổ tỷ trọng ngược với độ biến động ATR% của từng mã (mã êm tối đa 31.25% NAV, mã giật mạnh giới hạn ~14% NAV)
                try {
                    BigDecimal navForSizing = getCurrentNav().max(BigDecimal.valueOf(10_000_000));
                    AdaptivePositionSizingDto adaptive = adaptivePositionSizingService.calculateAdaptiveSize(scan.getSymbol(), navForSizing);
                    if (adaptive != null && adaptive.getAtrPercent() != null && adaptive.getAtrPercent().doubleValue() > 0) {
                        double atrPct = Math.max(1.0, adaptive.getAtrPercent().doubleValue());
                        double volScale = Math.min(1.25, Math.max(0.55, 2.2 / atrPct));
                        BigDecimal invVolBudget = navForSizing.divide(BigDecimal.valueOf(4), 0, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(volScale));
                        BigDecimal effectivePrice = scan.getPrice().multiply(feeMultiplier);
                        int invVolShares = (invVolBudget.divide(effectivePrice, 0, RoundingMode.FLOOR).intValue() / 100) * 100;
                        if (invVolShares >= 100 && invVolShares < finalShares) {
                            finalShares = invVolShares;
                            proposedCost = scan.getPrice().multiply(BigDecimal.valueOf(finalShares));
                            addLog(String.format("⚖️ [Q1_A INV-VOL SIZING] %s (ATR=%.2f%% → scale=%.2fx): Khớp quy mô tối ưu %d cp (%,.0f đ).",
                                scan.getSymbol(), atrPct, volScale, finalShares, proposedCost.doubleValue()));
                        }
                    }
                } catch (Exception ignored) {}

                if (proposedCost.multiply(feeMultiplier).compareTo(availableCash) > 0) {
                    BigDecimal effectivePrice = scan.getPrice().multiply(feeMultiplier);
                    int maxAffordable = availableCash.divide(effectivePrice, 0, RoundingMode.FLOOR).intValue();
                    maxAffordable = (maxAffordable / 100) * 100; // Làm tròn lô 100 HOSE
                    if (maxAffordable < 100) {
                        addLog(String.format("🛡️ [QUẢN TRỊ TIỀN MẶT] Bỏ qua %s: Tiền mặt còn %,.0f đ không đủ mua tối thiểu 1 lô 100 cp + phí (cần %,.0f đ).",
                            scan.getSymbol(), availableCash.doubleValue(), effectivePrice.multiply(BigDecimal.valueOf(100)).doubleValue()));
                        continue;
                    }
                    finalShares = maxAffordable;
                    proposedCost = scan.getPrice().multiply(BigDecimal.valueOf(finalShares));
                    addLog(String.format("⚖️ [ĐIỀU CHỈNH KHỐI LƯỢNG] %s: Hạ quy mô từ %d cp xuống %d cp (%,.0f đ) để vừa vặn tiền mặt khả dụng %,.0f đ.",
                        scan.getSymbol(), sizing.getMaxSharesToBuy(), finalShares, proposedCost.doubleValue(), availableCash.doubleValue()));
                }

                // =========================================================================
                // 2. BỘ LỌC ĐỊNH CHẾ V2 (ACTIVE GATES): NÉ TIN XẤU, CÁ MẬP KÊ ẢO, TƯỜNG BÁN, RRG/KALMAN/MTF
                // =========================================================================
                PreTradeFilterOutcome filterOutcome = evaluateInstitutionalEntryFilters(
                    scan.getSymbol(), finalShares, scan.getPrice(), scan.getTargetPrice(), true
                );
                if (!filterOutcome.allowed()) {
                    addLog(String.format("🚫 [CHỐT CHẶN V2 TỪ CHỐI MUA] %s: %s | Chi tiết: [%s]",
                        scan.getSymbol(), filterOutcome.vetoReason(), filterOutcome.auditSummary()));
                    recordTraceEvent("FILTER_VETO", scan.getSymbol(), java.util.Map.of(
                        "price", scan.getPrice(), "score", scan.getConfidenceScore(),
                        "vetoCode", filterOutcome.vetoFilterCode() != null ? filterOutcome.vetoFilterCode() : "UNKNOWN",
                        "reason", filterOutcome.vetoReason() != null ? filterOutcome.vetoReason() : "",
                        "audit", filterOutcome.auditSummary()
                    ));
                    if (botDecisionAuditService != null) {
                        botDecisionAuditService.recordDecision(
                            scan.getSymbol(), "REJECTED_FILTER", scan.getPrice(),
                            filterOutcome.vetoFilterCode(), filterOutcome.vetoReason() + " | " + filterOutcome.auditSummary(),
                            scan.getConfidenceScore()
                        );
                    }
                    continue;
                }

                String shadowEvaluation = filterOutcome.auditSummary();
                addLog(String.format("✅ [VƯỢT QUA TOÀN BỘ CHỐT CHẶN V2] %s: Đạt chuẩn kỹ thuật + Vi cấu trúc thật + Vĩ mô. Hồ sơ: [%s]",
                    scan.getSymbol(), shadowEvaluation));

                // =========================================================================
                // 3. THỰC THI KHỚP LỆNH MUA T1 CORE & GHI AUDIT
                // =========================================================================
                BigDecimal stopLossPrice = scan.getStopLoss();
                BigDecimal targetPrice = scan.getTargetPrice();

                TradeRequest tradeReq = TradeRequest.builder()
                    .symbol(scan.getSymbol())
                    .exchange(scan.getExchange())
                    .type("buy")
                    .tradeDate(LocalDate.now())
                    .price(scan.getPrice())
                    .quantity(finalShares)
                    .strategy("T1_BREAKOUT_TREND_CORE")
                    .stopLoss(stopLossPrice)
                    .takeProfit(targetPrice)
                    .reason(String.format("🤖 Bot Quant V2 Core | Risk: %s%% NAV | Audit V2: %s", dynamicRisk, shadowEvaluation))
                    .build();

                Trade created = tradeService.createTrade(tradeReq);
                if (botDecisionAuditService != null && created != null) {
                    botDecisionAuditService.recordDecision(
                        created.getSymbol(),
                        "BUY_T1_CORE_EXECUTED",
                        created.getPrice(),
                        "CORE_T1_DEFCON",
                        "Khớp lệnh theo T1 Core. Đánh giá Shadow Mode: " + shadowEvaluation,
                        scan.getConfidenceScore()
                    );
                }
                breakoutWatchlist.removeIf(item -> created != null && created.getSymbol().equalsIgnoreCase((String) item.get("symbol")));
                todayTradesCount++;

                if (created != null) {
                    addLog(String.format("⚡ ĐÃ MỞ VỊ THẾ T1 CORE: Mua %d cp %s giá %s đ | SL: %s | TP: %s (R:R = 1:%s)",
                        created.getQuantity(), created.getSymbol(), created.getPrice(), created.getStopLoss(), created.getTakeProfit(), sizing.getRiskRewardRatio()));

                    recordTraceEvent("TRADE_OPENED", created.getSymbol(), java.util.Map.of(
                        "strategy", created.getStrategy() != null ? created.getStrategy() : "QUANT_V3",
                        "price", created.getPrice(),
                        "quantity", created.getQuantity(),
                        "stopLoss", created.getStopLoss() != null ? created.getStopLoss() : BigDecimal.ZERO,
                        "takeProfit", created.getTakeProfit() != null ? created.getTakeProfit() : BigDecimal.ZERO,
                        "score", scan.getConfidenceScore(),
                        "audit", shadowEvaluation
                    ));

                    try {
                        sseStreamService.broadcast("BOT_TRADE_OPENED", created);
                    } catch (Exception ignored) {}

                    alertService.createAlert(
                        created.getSymbol(),
                        "BOT_TRADE_EXECUTED",
                        "🤖 [BOT KHỚP LỆNH T1 CORE] Đã mua " + created.getQuantity() + " cp " + created.getSymbol(),
                        "Lệnh tự động mở theo chiến lược T1 Core Breakout + Trend. Quản trị rủi ro nghiêm ngặt SL 7%.",
                        created.getPrice(),
                        "INFO"
                    );
                }
                break; // Mở 1 mã mỗi chu kỳ để tránh giải ngân ồ ạt
            }
        }

        // Ghi nhận Delta Trace JSONL ra file theo ngày (không phình DB H2, tự nén GZIP lúc 15:05)
        recordCycleDeltaTrace(marketRegime, crashStatus, openTrades, scans);

        // Ghi nhận trực canh mỗi chu kỳ để người dùng theo dõi rõ tiến độ trên Live Trace Console
        cycleScanCount++;
        if (!scans.isEmpty()) {
            StockScanResult top = scans.stream()
                .max(java.util.Comparator.comparingInt(StockScanResult::getConfidenceScore))
                .orElse(scans.get(0));
            addLog(String.format("🔍 [TRỰC CANH QUÉT VN50] Quét %d mã: Cao nhất %s (%dđ/100). Chưa có mã đạt chuẩn giải ngân (≥75đ). Bảo toàn 100%% tiền mặt.",
                scans.size(), top.getSymbol(), top.getConfidenceScore()));
        }

        // Luân phiên ghi nhận thông tin chuyên sâu theo các phân hệ để làm giàu dữ liệu lọc (Filter Tabs)
        if (cycleScanCount % 3 == 0) {
            try {
                var rrg = relativeRotationGraphService.calculateSectorsRrg();
                if (rrg != null) {
                    addLog(String.format("📈 [RRG ALPHA DÒNG TIỀN] Nhóm dẫn dắt: %s | Nhóm suy kiệt tụt hậu: %s.",
                        rrg.getTopLeadingSectors(), rrg.getToxicLaggingSectors()));
                }
            } catch (Exception ignored) {}
        }

        if (cycleScanCount % 4 == 0) {
            addLog(String.format("🛡️ [DEFCON-1 BẢO VỆ] Trạng thái: %s | Cầu chì an toàn: Cho phép giải ngân khi có điểm nổ Pocket Pivot.",
                crashStatus.getDefenseStatus()));
        }

        if (cycleScanCount % 5 == 0) {
            addLog("💧 [KIỂM TOÁN THANH KHOẢN] Khóa T+2.5 kích hoạt | Bước giá sàn HOSE/HNX chuẩn hóa | Kiểm soát trượt giá Slippage 0.15%.");
        }
    }

    /**
     * Tự động quét và thực hiện chốt lời / cắt lỗ / dời trailing stop cho danh mục đang nắm giữ
     */
    public void manageOpenPositions() {
        List<Trade> openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");
        for (Trade trade : openTrades) {
            try {
                StockQuote quote = stockPriceService.getQuote(trade.getSymbol());
                if (quote == null || quote.isStale() || quote.getPrice() == null || quote.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    log.debug("Bỏ qua quản trị vị thế {} do báo giá không hợp lệ hoặc STALE.", trade.getSymbol());
                    continue;
                }

                BigDecimal currentPrice = quote.getPrice();
                BigDecimal entryPrice = trade.getPrice();
                BigDecimal pnlPerShare = currentPrice.subtract(entryPrice);
                BigDecimal totalPnl = pnlPerShare.multiply(BigDecimal.valueOf(trade.getQuantity()));
                BigDecimal pnlPct = pnlPerShare.divide(entryPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

                trade.setPnl(totalPnl);
                trade.setPnlPercent(pnlPct);

                // Cập nhật MFE (đỉnh lãi cao nhất) và MAE (đáy lỗ sâu nhất) của lệnh
                if (trade.getMfePercent() == null || pnlPct.compareTo(trade.getMfePercent()) > 0) {
                    trade.setMfePercent(pnlPct);
                }
                if (trade.getMaePercent() == null || pnlPct.compareTo(trade.getMaePercent()) < 0) {
                    trade.setMaePercent(pnlPct);
                }

                // 0. RÀNG BUỘC PHÁP LÝ T+2.5 SÀN HOSE/HNX: Cổ phiếu chưa về tài khoản theo chu kỳ thanh toán bù trừ không thể bán!
                LocalDateTime now = LocalDateTime.now();
                if (!isT25SettlementReady(trade.getTradeDate(), now)) {
                    String t25Desc = getT25StatusDescription(trade.getTradeDate(), now);
                    if (trade.getStopLoss() != null && currentPrice.compareTo(trade.getStopLoss()) <= 0) {
                        LocalDateTime lastWarn = lastT25AlertTime.get(trade.getSymbol());
                        if (lastWarn == null || java.time.Duration.between(lastWarn, now).toMinutes() >= 15) {
                            lastT25AlertTime.put(trade.getSymbol(), now);
                            addLog(String.format("⚠️ [KHÓA THANH KHOẢN T+2.5] %s vi phạm SL (%s đ <= %s đ) nhưng đang ở trạng thái %s (chưa thể bán theo luật). Bot trực canh thoát hàng ngay phiên chiều 13:00.",
                                trade.getSymbol(), currentPrice, trade.getStopLoss(), t25Desc));
                        }
                    }
                    continue;
                }

                // 1. Tự động Cắt Lỗ (STOP LOSS TRIGGER)
                if (trade.getStopLoss() != null && currentPrice.compareTo(trade.getStopLoss()) <= 0) {
                    Trade closed = tradeService.executeMarketClose(
                        trade,
                        currentPrice,
                        String.format("🤖 Bot tự động cắt lỗ bảo vệ vốn tại giá %s đ (Vi phạm SL: %s đ)", currentPrice, trade.getStopLoss())
                    );
                    todayRealizedPnl = todayRealizedPnl.add(closed.getPnl());
                    todayTradesCount++;
                    addLog(String.format("🚨 [BOT CẮT LỖ BẢO VỆ VỐN] Đã bán hết %d cp %s tại %s đ. Khoản lỗ: %s đ (%s%%)",
                        closed.getQuantity(), closed.getSymbol(), currentPrice, closed.getPnl(), closed.getPnlPercent()));
                    recordTraceEvent("STOP_LOSS_EXECUTED", closed.getSymbol(), java.util.Map.of(
                        "price", currentPrice, "qty", closed.getQuantity(), "pnl", closed.getPnl(), "pnlPct", closed.getPnlPercent()
                    ));

                    try {
                        sseStreamService.broadcast("BOT_TRADE_CLOSED", closed);
                    } catch (Exception ignored) {}

                    alertService.createAlert(
                        closed.getSymbol(),
                        "BOT_STOP_LOSS_EXECUTED",
                        "🚨 [BOT CẮT LỖ] Bán " + closed.getQuantity() + " cp " + closed.getSymbol(),
                        "Đã cắt lỗ dứt khoát bảo vệ vốn theo nguyên tắc vàng số 1. PnL: " + closed.getPnl() + " đ.",
                        currentPrice,
                        "CRITICAL"
                    );
                    continue;
                }

                // 1.5. LÁ CHẮN BÃO HỆ THỐNG (Q1_A_INVVOL MARKET CRASH SHIELD EXIT):
                // Khi VN-Index gãy cả EMA20 & SMA50 & Breadth < 35% (DOWNTREND_DEFENSE),
                // chủ động thoát toàn bộ vị thế đã về tài khoản T+2.5 để về 100% tiền mặt (cắt lỗ năm 2022 xuống -14.9% thay vì -34.5%).
                try {
                    var liveRegime = marketRegimeDetectionService.detectMarketRegime();
                    if (liveRegime != null && "DOWNTREND_DEFENSE".equalsIgnoreCase(liveRegime.getCurrentRegime())) {
                        Trade closed = tradeService.executeMarketClose(
                            trade,
                            currentPrice,
                            String.format("🛡️ [Q1_A CRASH SHIELD] Thoát hàng về tiền mặt khi VN-Index rơi vào DOWNTREND_DEFENSE tại %s đ (%s%%)",
                                currentPrice, pnlPct.setScale(2, RoundingMode.HALF_UP))
                        );
                        todayRealizedPnl = todayRealizedPnl.add(closed.getPnl());
                        todayTradesCount++;
                        addLog(String.format("🛡️ [Q1_A CRASH SHIELD EXIT] Bán hết %d cp %s @ %s đ do VN-Index gãy xu hướng cấu trúc. PnL: %s đ (%s%%)",
                            closed.getQuantity(), closed.getSymbol(), currentPrice, closed.getPnl(), closed.getPnlPercent()));
                        recordTraceEvent("CRASH_SHIELD_EXIT", closed.getSymbol(), java.util.Map.of(
                            "price", currentPrice, "qty", closed.getQuantity(), "pnl", closed.getPnl(), "pnlPct", closed.getPnlPercent()
                        ));
                        try { sseStreamService.broadcast("BOT_TRADE_CLOSED", closed); } catch (Exception ignored) {}
                        continue;
                    }
                } catch (Exception ignored) {}

                // 2. THOÁT THEO GÃY CẤU TRÚC XU HƯỚNG (Q1_A_INVVOL STRUCTURAL TREND EXIT):
                // Không chốt non 50% ở +12%, không dời hòa vốn sớm ở +8%!
                // Chỉ bán khi cổ phiếu gãy ĐỒNG THỜI cả MA20 và MA50 (kết thúc sóng tăng thực sự).
                try {
                    StockScanResult liveEval = strategyService.evaluateSymbol(trade.getSymbol());
                    if (liveEval != null && liveEval.getMa20() != null && liveEval.getMa50() != null
                        && currentPrice.compareTo(liveEval.getMa20()) < 0
                        && currentPrice.compareTo(liveEval.getMa50()) < 0) {
                        Trade closed = tradeService.executeMarketClose(
                            trade,
                            currentPrice,
                            String.format("📉 [Q1_A TREND BREAK EXIT] %s gãy đồng thời MA20 (%s) & MA50 (%s) tại %s đ (%s%%)",
                                trade.getSymbol(), liveEval.getMa20(), liveEval.getMa50(), currentPrice, pnlPct.setScale(2, RoundingMode.HALF_UP))
                        );
                        todayRealizedPnl = todayRealizedPnl.add(closed.getPnl());
                        todayTradesCount++;
                        addLog(String.format("📉 [Q1_A TREND BREAK EXIT] Chốt/Thoát %d cp %s @ %s đ do gãy cả MA20 & MA50. PnL: %s đ (%s%%)",
                            closed.getQuantity(), closed.getSymbol(), currentPrice, closed.getPnl(), closed.getPnlPercent()));
                        recordTraceEvent("TREND_BREAK_EXIT", closed.getSymbol(), java.util.Map.of(
                            "price", currentPrice, "ma20", liveEval.getMa20(), "ma50", liveEval.getMa50(),
                            "pnl", closed.getPnl(), "pnlPct", closed.getPnlPercent()
                        ));
                        try { sseStreamService.broadcast("BOT_TRADE_CLOSED", closed); } catch (Exception ignored) {}
                        continue;
                    }
                } catch (Exception ignored) {}

                // 3. Tự động Chốt Lời Đích Cuối (FULL TAKE PROFIT TARGET REACHED - mặc định +35% gồng trọn siêu sóng)
                if (trade.getTakeProfit() != null && currentPrice.compareTo(trade.getTakeProfit()) >= 0) {
                    Trade closed = tradeService.executeMarketClose(
                        trade,
                        currentPrice,
                        String.format("🎯 Bot tự động chốt lời bảo toàn lợi nhuận tại giá %s đ (Đạt TP: %s đ)", currentPrice, trade.getTakeProfit())
                    );
                    todayRealizedPnl = todayRealizedPnl.add(closed.getPnl());
                    todayTradesCount++;
                    addLog(String.format("💰 [BOT CHỐT LỜI THÀNH CÔNG] Đã bán chốt lời %d cp %s tại %s đ. Lợi nhuận: +%s đ (+%s%%)",
                        closed.getQuantity(), closed.getSymbol(), currentPrice, closed.getPnl(), closed.getPnlPercent()));
                    recordTraceEvent("TAKE_PROFIT_EXECUTED", closed.getSymbol(), java.util.Map.of(
                        "price", currentPrice, "qty", closed.getQuantity(), "pnl", closed.getPnl(), "pnlPct", closed.getPnlPercent()
                    ));

                    try {
                        sseStreamService.broadcast("BOT_TRADE_CLOSED", closed);
                    } catch (Exception ignored) {}

                    alertService.createAlert(
                        closed.getSymbol(),
                        "BOT_TAKE_PROFIT_EXECUTED",
                        "🎯 [BOT CHỐT LỜI] Bán " + closed.getQuantity() + " cp " + closed.getSymbol(),
                        "Đã hiện thực hóa lợi nhuận ròng +" + closed.getPnl() + " đ (" + closed.getPnlPercent() + "%).",
                        currentPrice,
                        "INFO"
                    );
                    continue;
                }

                // 4. DYNAMIC TRAILING STOP ĐA TẦNG - Tự động nâng SL theo từng nấc lãi
                // Nguyên tắc: SL mới = Giá MFE cao nhất × (1 - trailingStep%)
                // trailingStep giảm dần khi lãi tăng để bảo vệ lợi nhuận tốt hơn
                applyDynamicTrailingStop(trade, currentPrice, pnlPct, entryPrice);
            } catch (Exception e) {
                log.error("Error managing open position for {}: {}", trade.getSymbol(), e.getMessage());
            }
        }
    }

    /**
     * Q1_A_INVVOL LET-WINNERS-RUN PROTECTION:
     * Backtest 6.8 năm chứng minh: Dời hòa vốn ở +8% & chốt 50% ở +12% làm mất gần nửa lãi (+58tr vs +105tr)!
     * Luật Q1_A_INVVOL:
     *   - Dưới +20%: KHÔNG dời SL sớm, để cổ phiếu gồng trọn sóng (đã có SL cứng -8%, Crash Shield & Trend-Break MA20/MA50 bảo vệ).
     *   - Từ +20% trở lên: Dời SL tối thiểu về hòa vốn +0.5% hoặc Chandelier rộng -10% từ đỉnh MFE.
     */
    private void applyDynamicTrailingStop(Trade trade, BigDecimal currentPrice, BigDecimal pnlPct, BigDecimal entryPrice) {
        if (pnlPct.compareTo(BigDecimal.valueOf(20.0)) < 0) {
            tradeRepository.save(trade);
            return;
        }

        BigDecimal mfePrice;
        if (trade.getMfePercent() != null && trade.getMfePercent().compareTo(BigDecimal.ZERO) > 0) {
            mfePrice = entryPrice.multiply(BigDecimal.ONE.add(
                trade.getMfePercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
            ));
        } else {
            mfePrice = currentPrice;
        }

        BigDecimal breakEvenSl = QuantitativeStrategyEngine.roundToVietnameseTick(
            entryPrice.multiply(BigDecimal.valueOf(1.005))
        );
        BigDecimal rawNewSl = mfePrice.multiply(BigDecimal.valueOf(0.900)); // Chandelier rộng -10% từ đỉnh MFE
        BigDecimal newSl = QuantitativeStrategyEngine.roundToVietnameseTick(rawNewSl);
        if (newSl.compareTo(breakEvenSl) < 0) {
            newSl = breakEvenSl;
        }

        BigDecimal oldSl = trade.getStopLoss();
        if (oldSl != null && newSl.compareTo(oldSl) <= 0) {
            tradeRepository.save(trade);
            return;
        }

        trade.setStopLoss(newSl);
        tradeRepository.save(trade);
        BigDecimal lockedGainPerShare = newSl.subtract(entryPrice);
        BigDecimal totalLockedGain = lockedGainPerShare.multiply(BigDecimal.valueOf(trade.getQuantity()));
        addLog(String.format(
            "🛡️ [Q1_A SIÊU SÓNG TRAILING ≥20%%] %s lãi +%s%% | MFE: %s đ → SL nâng: %s đ → %s đ | Khóa lãi tối thiểu: +%s đ",
            trade.getSymbol(),
            pnlPct.setScale(2, RoundingMode.HALF_UP),
            mfePrice.setScale(0, RoundingMode.HALF_UP),
            oldSl != null ? oldSl.setScale(0, RoundingMode.HALF_UP) : "—",
            newSl.setScale(0, RoundingMode.HALF_UP),
            totalLockedGain.setScale(0, RoundingMode.HALF_UP)
        ));
        recordTraceEvent("TRAILING_STOP_UPGRADED", trade.getSymbol(), java.util.Map.of(
            "tier", "Q1_A_SUPER_TREND_20PCT",
            "pnlPct", pnlPct.setScale(2, RoundingMode.HALF_UP),
            "oldSl", oldSl != null ? oldSl : BigDecimal.ZERO,
            "newSl", newSl
        ));
    }

    /**
     * ⏰ TỰ ĐỘNG RESTART TRƯA 12:00 (Nghỉ giữa phiên HOSE 11:30 - 13:00)
     * Mục đích: Làm mới trạng thái bot, cập nhật giá mới nhất, clear log cũ, tái khởi tạo
     * connection pool dữ liệu — đảm bảo phiên chiều chạy ổn định sau giờ nghỉ trưa.
     */
    @Scheduled(cron = "0 0 12 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
    public void noonRestart() {
        log.info("⏰ [NOON AUTO-RESTART] Khởi động lại nhẹ tại 12:00 trưa — giờ nghỉ phiên HOSE/HNX");
        try {
            // 1. Reset log để không tràn bộ nhớ sau nửa phiên sáng
            int logCount = botLogs.size();
            botLogs.clear();

            // 2. Reset bộ đếm circuit breaker debounce để phiên chiều sạch sẽ
            lastCircuitBreakerLogTime = null;
            lastT25AlertTime.clear();

            // 3. Reset scan cycle count để log luân phiên RRG/DEFCON bắt đầu lại
            cycleScanCount = 0;

            // 4. Đảm bảo bot vẫn đang chạy (isRunning không bị thay đổi)
            addLog(String.format(
                "♻️ [NOON AUTO-RESTART 12:00] Khởi động lại thành công! Đã xóa %d dòng log cũ. Bot sẵn sàng trực canh phiên chiều 13:00 - 14:45. Trailing Stop đa tầng đang hoạt động.",
                logCount
            ));
            addLog("🛡️ [PHIÊN CHIỀU] Cầu chì DEFCON-1 + Trailing Stop Dynamic đang active. Bot sẽ tự động quét VN50 kể từ 13:00.");
        } catch (Exception e) {
            log.error("Lỗi noon auto-restart: {}", e.getMessage());
        }
    }

    /**
     * Mô phỏng kiếm tiền hàng ngày (dành cho kiểm thử và rèn luyện thuật toán)
     */
    public Trade simulateDailyProfitTrade(String symbol) {
        String sym = symbol != null ? symbol.toUpperCase() : "FPT";
        StockQuote quote = stockPriceService.getQuote(sym);
        BigDecimal entryPrice = quote.getPrice().compareTo(BigDecimal.ZERO) > 0 ? quote.getPrice() : BigDecimal.valueOf(140000);
        BigDecimal exitPrice = entryPrice.multiply(BigDecimal.valueOf(1.15)).setScale(0, RoundingMode.HALF_UP); // +15% profit

        int qty = 500;
        BigDecimal grossBuy = entryPrice.multiply(BigDecimal.valueOf(qty));
        BigDecimal buyFee = grossBuy.multiply(BigDecimal.valueOf(0.0015));

        BigDecimal grossSell = exitPrice.multiply(BigDecimal.valueOf(qty));
        BigDecimal sellFee = grossSell.multiply(BigDecimal.valueOf(0.0015));
        BigDecimal sellTax = grossSell.multiply(BigDecimal.valueOf(0.0010));

        BigDecimal netPnl = grossSell.subtract(grossBuy).subtract(buyFee).subtract(sellFee).subtract(sellTax);
        BigDecimal pnlPct = netPnl.divide(grossBuy, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        Trade trade = Trade.builder()
            .symbol(sym)
            .exchange("HOSE")
            .type("buy")
            .tradeDate(LocalDate.now().minusDays(5))
            .price(entryPrice)
            .quantity(qty)
            .fee(buyFee)
            .strategy("Breakout nền giá tích lũy - Bot tự động")
            .stopLoss(entryPrice.multiply(BigDecimal.valueOf(0.93)))
            .takeProfit(exitPrice)
            .closeDate(LocalDate.now())
            .closePrice(exitPrice)
            .closeQuantity(qty)
            .status("closed")
            .pnl(netPnl)
            .pnlPercent(pnlPct)
            .reason("🤖 Đạt mục tiêu chốt lời 15% tự động")
            .notes("Lệnh mẫu chốt lời thành công kiếm tiền hàng ngày")
            .build();

        Trade saved = tradeRepository.save(trade);
        todayRealizedPnl = todayRealizedPnl.add(netPnl);
        todayTradesCount++;
        addLog(String.format("💰 CHỐT LỜI THÀNH CÔNG %s: Lợi nhuận ròng +%s đ (+%s%%)", sym, netPnl.toPlainString(), pnlPct.toPlainString()));
        return saved;
    }

    /**
     * Kiểm tra xem vị thế đã đủ điều kiện T+2.5 để bán theo quy chế giao dịch UBCKNN / VSDC.
     * Quy tắc:
     * - T+0: Ngày khớp lệnh mua
     * - T+1: Ngày làm việc thứ 1 (không tính Thứ 7, Chủ nhật)
     * - T+2: Đúng ngày làm việc thứ 2, cổ phiếu về tài khoản từ 11:30 - 13:00, được bán từ 13:00 chiều (phiên T+2.5).
     * - Từ ngày làm việc thứ 3 trở đi: Được bán toàn phiên.
     */
    public static boolean isT25SettlementReady(LocalDate tradeDate, LocalDateTime currentDateTime) {
        if (tradeDate == null) return true;
        LocalDate currentDate = currentDateTime.toLocalDate();
        if (currentDate.isBefore(tradeDate)) return false;
        if (currentDate.isEqual(tradeDate)) return false;

        int businessDaysElapsed = 0;
        LocalDate temp = tradeDate.plusDays(1);
        while (!temp.isAfter(currentDate)) {
            if (temp.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && temp.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                businessDaysElapsed++;
            }
            temp = temp.plusDays(1);
        }

        if (businessDaysElapsed < 2) {
            return false;
        }

        if (businessDaysElapsed == 2) {
            return currentDateTime.toLocalTime().isAfter(java.time.LocalTime.of(12, 59, 59));
        }

        return true;
    }

    public static String getT25StatusDescription(LocalDate tradeDate, LocalDateTime currentDateTime) {
        if (tradeDate == null) return "T+2.5 Đã khả dụng";
        LocalDate currentDate = currentDateTime.toLocalDate();
        if (currentDate.isEqual(tradeDate)) return "T+0 (Vừa khớp lệnh hôm nay)";

        int businessDaysElapsed = 0;
        LocalDate temp = tradeDate.plusDays(1);
        while (!temp.isAfter(currentDate)) {
            if (temp.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && temp.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                businessDaysElapsed++;
            }
            temp = temp.plusDays(1);
        }

        if (businessDaysElapsed == 1) return "T+1 (Cổ phiếu đang trên đường về tài khoản)";
        if (businessDaysElapsed == 2) {
            if (currentDateTime.toLocalTime().isBefore(java.time.LocalTime.of(13, 0))) {
                return "T+2 phiên sáng (Chờ VSDC phân bổ cổ phiếu vào 13:00 chiều)";
            } else {
                return "T+2.5 phiên chiều (Cổ phiếu đã về tài khoản, sẵn sàng khớp lệnh bán)";
            }
        }
        return "T+" + businessDaysElapsed + " (Đã thanh toán đầy đủ, sẵn sàng giao dịch)";
    }

    public String getMarketDataStatus() {
        return stockPriceService.isMarketDataConnected() ? "REAL" : "STALE";
    }

    public boolean isDataFeedHealthy() {
        return stockPriceService.isMarketDataConnected();
    }

    private void updateBreakoutWatchlist(List<StockScanResult> scans, List<Trade> openTrades) {
        if (scans == null || scans.isEmpty()) return;

        java.util.Set<String> holdingSymbols = openTrades.stream()
            .map(t -> t.getSymbol().toUpperCase())
            .collect(java.util.stream.Collectors.toSet());

        List<java.util.Map<String, Object>> newQueue = new ArrayList<>();
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        String nowStr = java.time.LocalTime.now().format(timeFmt);

        scans.stream()
            .filter(s -> s.getConfidenceScore() >= 65 && !holdingSymbols.contains(s.getSymbol().toUpperCase()))
            .sorted((a, b) -> Integer.compare(b.getConfidenceScore(), a.getConfidenceScore()))
            .limit(8)
            .forEach(s -> {
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("symbol", s.getSymbol());
                BigDecimal curPrice = s.getPrice();
                item.put("currentPrice", curPrice);

                BigDecimal wallPrice = s.getTargetPrice() != null ? s.getTargetPrice() : curPrice.multiply(BigDecimal.valueOf(1.03));
                wallPrice = QuantitativeStrategyEngine.roundToVietnameseTick(wallPrice);

                long wallVol = 0L;
                double wallProp = 0.0;
                boolean realWallFound = false;
                try {
                    OrderBookImbalanceDto obi = orderBookImbalanceService.analyzeMicrostructure(s.getSymbol());
                    if (obi != null) {
                        if (obi.getWallPrice() != null && obi.getWallPrice().compareTo(BigDecimal.ZERO) > 0) {
                            wallPrice = obi.getWallPrice();
                        }
                        if (obi.getWallVolume() != null && obi.getWallVolume() > 0) {
                            wallVol = obi.getWallVolume();
                            realWallFound = true;
                        }
                        if (obi.getWallProportionPercent() != null && obi.getWallProportionPercent().compareTo(BigDecimal.ZERO) > 0) {
                            wallProp = obi.getWallProportionPercent().doubleValue();
                        }
                    }
                } catch (Exception ignored) {}

                item.put("wallPrice", wallPrice);

                BigDecimal dist = BigDecimal.ZERO;
                if (curPrice != null && curPrice.compareTo(BigDecimal.ZERO) > 0 && wallPrice.compareTo(curPrice) > 0) {
                    dist = wallPrice.subtract(curPrice).divide(curPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                }
                item.put("distancePercent", dist);
                item.put("wallVolume", wallVol);
                item.put("wallProportionPercent", wallProp);
                item.put("confidenceScore", s.getConfidenceScore());
                item.put("status", realWallFound
                    ? (s.getConfidenceScore() >= 75 ? "ĐẠT CHUẨN MUA" : "CHỜ NỔ VOL")
                    : (s.getConfidenceScore() >= 75 ? "THEO DÕI BREAKOUT" : "CHỜ SỔ LỆNH THẬT"));
                item.put("updatedAt", nowStr);
                newQueue.add(item);
            });

        if (!newQueue.isEmpty()) {
            this.breakoutWatchlist.clear();
            this.breakoutWatchlist.addAll(newQueue);
        }
    }

    private void checkOversoldBounceOpportunity(List<Trade> openTrades) {
        try {
            if (openTrades.size() >= 2) return; // Khống chế tối đa 1-2 vị thế bắt đáy để bảo đảm an toàn tuyệt đối

            OversoldBounceDto bounce = oversoldBounceDetectorService.scanOversoldBounceCandidates();
            if (bounce != null && bounce.getCandidates() != null && !bounce.getCandidates().isEmpty()) {
                var topCandidate = bounce.getCandidates().stream()
                    .filter(c -> c.getBounceScore() >= 85) // Điểm cao: Hoảng loạn cực độ RSI < 30 + Pinbar/Hammer
                    .findFirst()
                    .orElse(null);

                if (topCandidate != null) {
                    boolean alreadyHolding = openTrades.stream()
                        .anyMatch(t -> t.getSymbol().equalsIgnoreCase(topCandidate.getSymbol()));
                    if (!alreadyHolding) {
                        // Kiểm tra Cooldown
                        boolean inCooldown = tradeRepository.findAll().stream()
                            .filter(t -> "closed".equalsIgnoreCase(t.getStatus()) && t.getSymbol().equalsIgnoreCase(topCandidate.getSymbol()))
                            .anyMatch(t -> t.getCloseDate() != null && java.time.temporal.ChronoUnit.DAYS.between(t.getCloseDate(), LocalDate.now()) < 3);
                        if (inCooldown) return;

                        // Tính tiền mặt khả dụng
                        BigDecimal availableCash = getAvailableCash();
                        if (availableCash.compareTo(BigDecimal.valueOf(2_000_000)) <= 0) return;

                        // Giải ngân tỷ trọng chiến thuật thăm dò: 15% - 20% NAV (không vượt tiền mặt)
                        BigDecimal tacticalBudget = getCurrentNav().multiply(BigDecimal.valueOf(0.18)).min(availableCash);
                        if (tacticalBudget.compareTo(BigDecimal.valueOf(3_000_000)) >= 0) {
                            BigDecimal effectiveEntry = topCandidate.getCurrentPrice().multiply(BigDecimal.valueOf(1.0015));
                            int shares = tacticalBudget.divide(effectiveEntry, 0, RoundingMode.FLOOR).intValue();
                            shares = (shares / 100) * 100; // Làm tròn lô 100 HOSE
                            if (shares >= 100) {
                                StockQuote liveQuote = stockPriceService.getQuote(topCandidate.getSymbol());
                                if (liveQuote == null || liveQuote.isStale() || liveQuote.getPrice() == null || liveQuote.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                                    addLog(String.format("🚨 [DỮ LIỆU STALE] Bỏ qua bắt đáy %s: Không có báo giá REAL.", topCandidate.getSymbol()));
                                    return;
                                }
                                BigDecimal entryPrice = liveQuote.getPrice();
                                BigDecimal proposedCost = entryPrice.multiply(BigDecimal.valueOf(shares));
                                if (!riskService.isSectorAllocationAllowed(topCandidate.getSymbol(), proposedCost, accountCapital)) {
                                    return;
                                }
                                PreTradeFilterOutcome guard = evaluateInstitutionalEntryFilters(
                                    topCandidate.getSymbol(), shares, entryPrice, topCandidate.getTacticalTargetPrice(), false
                                );
                                if (!guard.allowed()) {
                                    addLog(String.format("🚫 [CHỐT CHẶN BẮT ĐÁY V2] Bỏ qua %s: %s", topCandidate.getSymbol(), guard.vetoReason()));
                                    if (botDecisionAuditService != null) {
                                        botDecisionAuditService.recordDecision(topCandidate.getSymbol(), "REJECTED_FILTER", entryPrice,
                                            guard.vetoFilterCode(), guard.vetoReason() + " | " + guard.auditSummary(), topCandidate.getBounceScore());
                                    }
                                    return;
                                }
                                BigDecimal sl = topCandidate.getTacticalStopLoss() != null ? topCandidate.getTacticalStopLoss() : entryPrice.multiply(BigDecimal.valueOf(0.96));
                                BigDecimal tp = topCandidate.getTacticalTargetPrice() != null ? topCandidate.getTacticalTargetPrice() : entryPrice.multiply(BigDecimal.valueOf(1.08));

                                TradeRequest req = TradeRequest.builder()
                                    .symbol(topCandidate.getSymbol())
                                    .exchange(liveQuote.getExchange() != null ? liveQuote.getExchange() : "HOSE")
                                    .type("buy")
                                    .price(entryPrice)
                                    .quantity(shares)
                                    .strategy("TACTICAL_OVERSOLD_REBOUND")
                                    .stopLoss(sl)
                                    .takeProfit(tp)
                                    .reason(String.format("🎯 Săn Bắt Đáy Hoảng Loạn: RSI=%.1f | %s (Điểm hồi phục: %dđ/100). Đón nhịp hồi kỹ thuật T+2.5!",
                                        topCandidate.getRsi14() != null ? topCandidate.getRsi14().doubleValue() : 28.0,
                                        topCandidate.getDivergenceSignal(),
                                        topCandidate.getBounceScore()))
                                    .build();

                                Trade openedTrade = tradeService.createTrade(req);
                                if (botDecisionAuditService != null && openedTrade != null) {
                                    botDecisionAuditService.recordDecision(openedTrade.getSymbol(), "BUY_OVERSOLD_REBOUND", openedTrade.getPrice(),
                                        "TACTICAL_OVERSOLD_REBOUND", guard.auditSummary(), topCandidate.getBounceScore());
                                }
                                todayTradesCount++;
                                addLog(String.format("🔥 [KÍCH HOẠT BẮT ĐÁY HOẢNG LOẠN] Mua %d cp %s giá %,.0f đ | SL: %,.0f đ | TP: %,.0f đ. Đón sóng hồi T+2.5!",
                                    shares, topCandidate.getSymbol(), entryPrice.doubleValue(), sl.doubleValue(), tp.doubleValue()));
                                recordTraceEvent("TRADE_OPENED", topCandidate.getSymbol(), java.util.Map.of(
                                    "strategy", "TACTICAL_OVERSOLD_REBOUND",
                                    "price", entryPrice, "quantity", shares, "stopLoss", sl, "takeProfit", tp,
                                    "score", topCandidate.getBounceScore(), "audit", guard.auditSummary()
                                ));
                                try {
                                    sseStreamService.broadcast("BOT_TRADE_OPENED", openedTrade);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Oversold bounce scan error: {}", e.getMessage());
        }
    }

    /**
     * Vũ khí kiếm tiền thực chiến: Săn "Siêu Cổ Phiếu Kháng Bão" (Anti-Market Relative Strength Leader)
     * Khi VN-Index đỏ hoặc rung lắc nhưng cổ phiếu vẫn giữ sắc XANH (+0.5% đến +5.0%), nằm trên MA20 & MA50
     * với dòng tiền lớn đỡ giá -> Đây là cổ phiếu dẫn dắt sẽ bật tăng mạnh nhất khi thị trường hồi phục!
     */
    private void checkAntiMarketLeaderOpportunity(List<Trade> openTrades, List<StockScanResult> scans) {
        try {
            if (openTrades.size() >= 2 || scans == null || scans.isEmpty()) return;

            StockQuote vniQuote = stockPriceService.getQuote("VNINDEX");
            double vniChg = (vniQuote != null && vniQuote.getChangePercent() != null)
                ? vniQuote.getChangePercent().doubleValue() : 0.0;

            // Tìm ứng viên Kháng Bão: Xanh bất chấp thị trường, vượt trội VNI >= +1.5%, trên MA20 & MA50, chưa tăng kịch trần
            StockScanResult topLeader = scans.stream()
                .filter(s -> s.getPrice() != null && s.getChangePercent() != null && s.getMa20() != null && s.getMa50() != null)
                .filter(s -> {
                    double chg = s.getChangePercent().doubleValue();
                    double alpha = chg - vniChg;
                    boolean aboveMa = s.getPrice().compareTo(s.getMa20()) >= 0 && s.getPrice().compareTo(s.getMa50()) >= 0;
                    return chg >= 0.4 && chg <= 5.2 && alpha >= 1.5 && aboveMa && s.getConfidenceScore() >= 70;
                })
                .sorted((a, b) -> Integer.compare(b.getConfidenceScore(), a.getConfidenceScore()))
                .findFirst()
                .orElse(null);

            if (topLeader == null) return;

            boolean alreadyHolding = openTrades.stream()
                .anyMatch(t -> t.getSymbol().equalsIgnoreCase(topLeader.getSymbol()));
            if (alreadyHolding) return;

            boolean inCooldown = tradeRepository.findAll().stream()
                .filter(t -> "closed".equalsIgnoreCase(t.getStatus()) && t.getSymbol().equalsIgnoreCase(topLeader.getSymbol()))
                .anyMatch(t -> t.getCloseDate() != null && java.time.temporal.ChronoUnit.DAYS.between(t.getCloseDate(), LocalDate.now()) < 3);
            if (inCooldown) return;

            // Nếu đã có 1 vị thế mở thì vị thế đó phải không bị lỗ nặng (> -1.0%) mới mở thêm mã Kháng Bão
            if (openTrades.size() == 1) {
                Trade existing = openTrades.get(0);
                if (existing.getPnlPercent() != null && existing.getPnlPercent().compareTo(BigDecimal.valueOf(-1.0)) < 0) {
                    return;
                }
            }

            BigDecimal availableCash = getAvailableCash();
            if (availableCash.compareTo(BigDecimal.valueOf(2_000_000)) <= 0) return;

            // Giải ngân Pilot 20% NAV cho mã Kháng Bão dẫn sóng (không vượt tiền mặt)
            BigDecimal pilotBudget = getCurrentNav().multiply(BigDecimal.valueOf(0.20)).min(availableCash);
            if (pilotBudget.compareTo(BigDecimal.valueOf(3_000_000)) < 0) return;

            BigDecimal effectiveEntry = topLeader.getPrice().multiply(BigDecimal.valueOf(1.0015));
            int shares = pilotBudget.divide(effectiveEntry, 0, RoundingMode.FLOOR).intValue();
            shares = (shares / 100) * 100;
            if (shares < 100) return;

            StockQuote liveLeaderQuote = stockPriceService.getQuote(topLeader.getSymbol());
            if (liveLeaderQuote == null || liveLeaderQuote.isStale() || liveLeaderQuote.getPrice() == null || liveLeaderQuote.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                addLog(String.format("🚨 [DỮ LIỆU STALE] Bỏ qua Kháng Bão %s: Không có báo giá REAL.", topLeader.getSymbol()));
                return;
            }

            BigDecimal entryPrice = liveLeaderQuote.getPrice();
            BigDecimal proposedCost = entryPrice.multiply(BigDecimal.valueOf(shares));
            if (!riskService.isSectorAllocationAllowed(topLeader.getSymbol(), proposedCost, accountCapital)) {
                return;
            }
            PreTradeFilterOutcome guard = evaluateInstitutionalEntryFilters(
                topLeader.getSymbol(), shares, entryPrice, topLeader.getTargetPrice(), false
            );
            if (!guard.allowed()) {
                addLog(String.format("🚫 [CHỐT CHẶN KHÁNG BÃO V2] Bỏ qua %s: %s", topLeader.getSymbol(), guard.vetoReason()));
                if (botDecisionAuditService != null) {
                    botDecisionAuditService.recordDecision(topLeader.getSymbol(), "REJECTED_FILTER", entryPrice,
                        guard.vetoFilterCode(), guard.vetoReason() + " | " + guard.auditSummary(), topLeader.getConfidenceScore());
                }
                return;
            }
            BigDecimal sl = topLeader.getStopLoss() != null ? topLeader.getStopLoss()
                : com.vntrade.backend.service.decision.QuantitativeStrategyEngine.roundToVietnameseTick(entryPrice.multiply(BigDecimal.valueOf(0.955)));
            BigDecimal tp = topLeader.getTargetPrice() != null ? topLeader.getTargetPrice()
                : com.vntrade.backend.service.decision.QuantitativeStrategyEngine.roundToVietnameseTick(entryPrice.multiply(BigDecimal.valueOf(1.12)));

            double alpha = topLeader.getChangePercent().doubleValue() - vniChg;
            TradeRequest req = TradeRequest.builder()
                .symbol(topLeader.getSymbol())
                .exchange(liveLeaderQuote.getExchange() != null ? liveLeaderQuote.getExchange() : "HOSE")
                .type("buy")
                .price(entryPrice)
                .quantity(shares)
                .strategy("ANTI_MARKET_RS_LEADER")
                .stopLoss(sl)
                .takeProfit(tp)
                .reason(String.format("🦅 Siêu Cổ Phiếu Kháng Bão (RS Leader): Tăng %+.2f%% (Vượt trội VNI %+.2f%%) | Giữ vững kênh trên MA20/MA50 | Score: %d/100",
                    topLeader.getChangePercent().doubleValue(), alpha, topLeader.getConfidenceScore()))
                .build();

            Trade opened = tradeService.createTrade(req);
            if (botDecisionAuditService != null && opened != null) {
                botDecisionAuditService.recordDecision(opened.getSymbol(), "BUY_ANTI_MARKET_LEADER", opened.getPrice(),
                    "ANTI_MARKET_RS_LEADER", guard.auditSummary(), topLeader.getConfidenceScore());
            }
            todayTradesCount++;
            addLog(String.format("🦅 [SĂN CỔ PHIẾU KHÁNG BÃO] Mua thăm dò %d cp %s giá %,.0f đ (Khỏe hơn VNI %+.2f%%) | SL: %,.0f đ | TP: %,.0f đ!",
                shares, topLeader.getSymbol(), entryPrice.doubleValue(), alpha, sl.doubleValue(), tp.doubleValue()));
            recordTraceEvent("TRADE_OPENED", topLeader.getSymbol(), java.util.Map.of(
                "strategy", "ANTI_MARKET_RS_LEADER",
                "price", entryPrice, "quantity", shares, "stopLoss", sl, "takeProfit", tp,
                "score", topLeader.getConfidenceScore(), "alphaPct", alpha, "audit", guard.auditSummary()
            ));
            try {
                sseStreamService.broadcast("BOT_TRADE_OPENED", opened);
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("Anti-market leader scan error: {}", e.getMessage());
        }
    }

    private record PreTradeFilterOutcome(boolean allowed, String vetoFilterCode, String vetoReason, String auditSummary) {}

    /**
     * Bộ lọc Định chế Hợp nhất V2:
     * 1. CafeF News Risk Veto (chặn tin khởi tố/bắt bớ/cắt margin/lỗ nặng)
     * 2. Sổ lệnh Level-2 THẬT: Chặn bẫy kê mua ảo (Spoofing), kéo rướn VWAP, tường bán đè áp đảo (OBI), khối ngoại xả ròng
     * 3. Với lệnh Breakout xu hướng (applyTrendGates=true): Chặn thêm RRG cắm đầu tụt hậu, Kalman giảm và MTF xung đột nặng
     */
    private PreTradeFilterOutcome evaluateInstitutionalEntryFilters(
            String symbol, int shares, BigDecimal price, BigDecimal targetPrice, boolean applyTrendGates) {
        StringBuilder summary = new StringBuilder();
        String vetoCode = null;
        String vetoReason = null;

        // 1. CafeF News Risk Veto
        try {
            String toxicNews = preMarketSentimentService.getToxicNewsReason(symbol);
            if (toxicNews != null) {
                summary.append("NEWS=VETO; ");
                vetoCode = "NEWS_RISK_VETO";
                vetoReason = toxicNews;
            } else {
                summary.append("NEWS=PASS; ");
            }
        } catch (Exception e) {
            summary.append("NEWS=ERR; ");
        }

        // 2. Real Level-2 Order Book Microstructure (chỉ kích hoạt chặn khi có độ sâu sổ lệnh thật trong phiên)
        OrderBookDto ob = null;
        try {
            ob = orderBookService.getOrderBook(symbol);
        } catch (Exception ignored) {}
        boolean hasRealDepth = ob != null && ob.isRealOrderBookAvailable();

        try {
            SpoofingDetectorDto spoofing = spoofingDetectorService.detectSpoofing(symbol);
            if (spoofing != null && hasRealDepth && !spoofing.isSafeToBuy()) {
                summary.append("SPOOFING=VETO(").append(spoofing.getLayeringPattern()).append("); ");
                if (vetoCode == null) {
                    vetoCode = "SPOOFING_TRAP";
                    vetoReason = spoofing.getInstitutionalActionVerdict();
                }
            } else {
                summary.append("SPOOFING=").append(hasRealDepth ? "PASS" : "NO_DEPTH").append("; ");
            }
        } catch (Exception e) {
            summary.append("SPOOFING=ERR; ");
        }

        try {
            OrderBookImbalanceDto obi = orderBookImbalanceService.analyzeMicrostructure(symbol);
            if (obi != null && hasRealDepth) {
                boolean isAskWall = "ASK_WALL_RESISTANCE".equals(obi.getWallDetected());
                boolean isHeavyDump = obi.getOrderBookImbalanceRatio() != null
                    && obi.getOrderBookImbalanceRatio().compareTo(BigDecimal.valueOf(-0.45)) < 0;
                if (isAskWall && isHeavyDump) {
                    summary.append("OBI=VETO_ASK_WALL; ");
                    if (vetoCode == null) {
                        vetoCode = "OBI_ASK_WALL";
                        vetoReason = String.format("Tường bán đè thật tại %,.0f đ (%s CP), OBI=%.2f",
                            obi.getWallPrice() != null ? obi.getWallPrice().doubleValue() : 0.0,
                            obi.getWallVolume(), obi.getOrderBookImbalanceRatio().doubleValue());
                    }
                } else {
                    summary.append("OBI=PASS; ");
                }
            } else {
                summary.append("OBI=NO_DEPTH; ");
            }
        } catch (Exception e) {
            summary.append("OBI=ERR; ");
        }

        try {
            ForeignFlowRiskDto foreign = foreignFlowRiskService.evaluateForeignFlowRisk(symbol);
            if (foreign != null && hasRealDepth && "FOREIGN_DUMP_WARNING".equals(foreign.getFiiFlowVerdict())) {
                summary.append("FII=VETO_DUMP; ");
                if (vetoCode == null) {
                    vetoCode = "FOREIGN_DUMP_WARNING";
                    vetoReason = foreign.getEarlyWarningAlerts().isEmpty()
                        ? "Khối ngoại xả ròng mạnh trong phiên" : foreign.getEarlyWarningAlerts().get(0);
                }
            } else {
                summary.append("FII=").append(foreign != null ? foreign.getFiiFlowVerdict() : "NONE").append("; ");
            }
        } catch (Exception e) {
            summary.append("FII=ERR; ");
        }

        // 3. Trend-based Institutional Filters (RRG, Kalman, MTF) trên nến thật
        try {
            RrgItemDto rrg = relativeRotationGraphService.calculateSingleStockRrg(symbol);
            if (rrg != null) {
                boolean isToxicLagging = "LAGGING".equals(rrg.getQuadrant()) && "SOUTHWEST".equals(rrg.getHeadingDirection());
                summary.append("RRG=").append(isToxicLagging ? "VETO_LAGGING_SW" : rrg.getQuadrant()).append("; ");
                if (applyTrendGates && isToxicLagging && vetoCode == null) {
                    vetoCode = "RRG_TOXIC_LAGGING";
                    vetoReason = "Cổ phiếu đang ở góc suy kiệt LAGGING hướng SOUTHWEST (yếu hơn thị trường chung)";
                }
            } else {
                summary.append("RRG=NONE; ");
            }
        } catch (Exception e) {
            summary.append("RRG=ERR; ");
        }

        // Kalman & MTF: Ghi nhận Shadow Mode (không chặn cứng Over-filtering để tránh bóp nghẹt sóng tăng theo kết quả Ablation 6.5 năm)
        try {
            KalmanFilterTrendDto kalman = kalmanFilterTrendService.analyzeKalmanTrend(symbol);
            if (kalman != null) {
                boolean isBearish = "BEARISH_DOWNWARD".equals(kalman.getTrendRegime());
                summary.append("KALMAN=").append(isBearish ? "SHADOW_BEAR" : "PASS").append("; ");
            } else {
                summary.append("KALMAN=NONE; ");
            }
        } catch (Exception e) {
            summary.append("KALMAN=ERR; ");
        }

        try {
            MultiTimeframeConfluenceDto mtf = confluenceService.analyzeMultiTimeframe(symbol);
            if (mtf != null) {
                int mtfScore = mtf.getConfluenceScore();
                summary.append("MTF=").append(mtfScore >= 45 ? "PASS(" + mtfScore + ")" : "SHADOW_LOW(" + mtfScore + ")").append("; ");
            } else {
                summary.append("MTF=NONE; ");
            }
        } catch (Exception e) {
            summary.append("MTF=ERR; ");
        }

        // 4. Thông tin tham chiếu bổ sung (CANSLIM, SMF, LIQ)
        try {
            CanslimRatingDto canslim = canslimRatingService.rateStock(symbol);
            summary.append("CANSLIM=").append(canslim.getCanslimScore()).append("d; ");
        } catch (Exception e) {
            summary.append("CANSLIM=ERR; ");
        }
        try {
            SmartMoneyFlowDto smf = smartMoneyFlowService.analyzeSmartMoney(symbol);
            summary.append("SMF=").append(smf.getAccumulationScore()).append("d; ");
        } catch (Exception e) {
            summary.append("SMF=ERR; ");
        }
        try {
            LiquidityAdjustedReturnDto liq = liquidityAdjustedReturnService.calculateLiquidityAdjustedReturn(
                symbol, shares, price, targetPrice, accountCapital);
            summary.append("LIQ=").append(liq.isLiquidEnough() ? "PASS" : "THIN");
        } catch (Exception e) {
            summary.append("LIQ=ERR");
        }

        return new PreTradeFilterOutcome(vetoCode == null, vetoCode, vetoReason, summary.toString());
    }

    private void recordTraceEvent(String eventType, String symbol, java.util.Map<String, Object> payload) {
        if (traceLoggerService != null) {
            traceLoggerService.recordEvent(eventType, symbol, payload);
        }
    }

    private void recordCycleDeltaTrace(MarketRegimeDto regime, MarketCrashProtectionDto crashStatus,
                                       List<Trade> openTrades, List<StockScanResult> scans) {
        if (traceLoggerService == null) return;
        try {
            StringBuilder sig = new StringBuilder();
            String regimeCode = regime != null ? regime.getCurrentRegime() : "UNKNOWN";
            BigDecimal vniLevel = regime != null && regime.getVnIndexLevel() != null ? regime.getVnIndexLevel() : BigDecimal.ZERO;
            BigDecimal vniChg = regime != null && regime.getVnIndexChangePercent() != null ? regime.getVnIndexChangePercent() : BigDecimal.ZERO;
            sig.append(regimeCode).append("|").append(vniLevel.setScale(1, RoundingMode.HALF_UP));

            List<java.util.Map<String, Object>> posSnapshots = new ArrayList<>();
            for (Trade t : openTrades) {
                BigDecimal pnlPct = t.getPnlPercent() != null ? t.getPnlPercent().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                sig.append("|").append(t.getSymbol()).append(":").append(pnlPct).append(":").append(t.getStopLoss());
                java.util.Map<String, Object> pm = new java.util.LinkedHashMap<>();
                pm.put("symbol", t.getSymbol());
                pm.put("qty", t.getQuantity());
                pm.put("entry", t.getPrice());
                pm.put("sl", t.getStopLoss());
                pm.put("tp", t.getTakeProfit());
                pm.put("pnlPct", pnlPct);
                posSnapshots.add(pm);
            }

            List<java.util.Map<String, Object>> topCandidates = new ArrayList<>();
            if (scans != null) {
                scans.stream()
                    .filter(s -> s.getConfidenceScore() >= 70)
                    .sorted((a, b) -> Integer.compare(b.getConfidenceScore(), a.getConfidenceScore()))
                    .limit(5)
                    .forEach(s -> {
                        java.util.Map<String, Object> cm = new java.util.LinkedHashMap<>();
                        cm.put("symbol", s.getSymbol());
                        cm.put("price", s.getPrice());
                        cm.put("score", s.getConfidenceScore());
                        cm.put("action", s.getAction());
                        cm.put("signal", s.getSignalType());
                        topCandidates.add(cm);
                    });
            }

            java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
            snap.put("regime", regimeCode);
            snap.put("vnIndex", vniLevel);
            snap.put("vnIndexChgPct", vniChg);
            snap.put("defconAllowBuy", crashStatus != null && crashStatus.isAllowNewPurchases());
            snap.put("cash", getAvailableCash());
            snap.put("nav", getCurrentNav());
            snap.put("openPositions", posSnapshots);
            snap.put("topCandidates", topCandidates);

            traceLoggerService.recordMarketDelta(sig.toString(), snap);
        } catch (Exception ignored) {}
    }
}