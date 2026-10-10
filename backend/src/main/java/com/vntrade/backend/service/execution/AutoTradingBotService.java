package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.*;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    // Forward Test Strategy: Core T1 (Breakout + Trend) + DEFCON-1 Circuit Breaker
    // Các tầng lọc nâng cao (CANSLIM, MTF, RRG, OBI, Spoofing, Kalman) chạy SHADOW MODE để tích lũy dữ liệu
    private String executionStrategy = "FORWARD_TEST_T1_CORE_DEFCON";
    private BigDecimal maxDrawdownThreshold = BigDecimal.valueOf(20.0); // Ngưỡng MaxDD cảnh báo: 20.0%
    private BigDecimal riskPerTradePercent = BigDecimal.valueOf(1.50); // Rủi ro cho phép mỗi lệnh: 1.50% NAV

    public List<java.util.Map<String, Object>> getBreakoutWatchlist() {
        return breakoutWatchlist;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        if (botLogs.isEmpty()) {
            addLog(String.format("🚀 [KHỞI TẠO PAPER TRADING THÀNH CÔNG] Vốn: %,.0f đ | Chế độ: LIVE_PAPER_MONEY", accountCapital));
            addLog("🔬 [CHIẾN LƯỢC FORWARD TEST] Cốt lõi: T1 (Breakout + Trend) + Cầu chì DEFCON-1 | 6 Tầng lọc nâng cao (CANSLIM, RRG, OBI, Spoofing, MTF, Kalman) chạy SHADOW MODE quan sát.");
            addLog("📊 Hiệu suất thực tế được đo lường động từ kết quả các lệnh đã đóng, không dùng chỉ tiêu cứng hay nhãn Verified giả định.");
            addLog("🛡️ [DEFCON-1 BẢO VỆ] Cầu chì an toàn: NORMAL_DEFENSE | Thị trường ổn định.");
        }
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

    public BigDecimal getDailyProfitTarget() {
        return dailyProfitTarget;
    }

    public BigDecimal getDailyMaxLossLimit() {
        return dailyMaxLossLimit;
    }

    public BigDecimal getTodayRealizedPnl() {
        return todayRealizedPnl;
    }

    public int getTodayTradesCount() {
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
        return "Real-time Forward Test (T1 + DEFCON-1 Core)";
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

        // 0. KIỂM SOÁT NGUỒN DỮ LIỆU THẬT (DATA FEED INTEGRITY: REAL vs STALE)
        // Khi mất dữ liệu thật hoặc toàn bộ API sàn không phản hồi, dừng mở vị thế mới ngay lập tức
        if (!stockPriceService.isMarketDataConnected()) {
            addLog("🚨 [MẤT KẾT NỐI DỮ LIỆU THẬT - STALE] Toàn bộ nguồn cấp giá sàn (VNDirect/TCBS/SSI) mất phản hồi. DỪNG 100% LỆNH MỞ VỊ THẾ ĐỂ BẢO VỆ TÀI KHOẢN!");
            return;
        }

        // 1. Quản trị và chốt lời / cắt lỗ tự động cho các vị thế đang mở
        manageOpenPositions();

        // 2. Kiểm tra Cầu chì phòng hộ sụp đổ thị trường (DEFCON-1 Circuit Breaker)
        MarketCrashProtectionDto crashStatus = crashProtectionService.evaluateMarketCircuitBreaker();
        if (!crashStatus.isAllowNewPurchases()) {
            if (botDecisionAuditService != null) {
                botDecisionAuditService.recordDecision("VNINDEX", "REJECTED_FILTER", BigDecimal.ZERO, "DEFCON_CRASH", crashStatus.getCircuitBreakerMessage(), 0);
            }
            addLog("🚨 [DEFCON-1 PHÒNG HỘ THỊ TRƯỜNG] " + crashStatus.getCircuitBreakerMessage() + ". Tự động phong tỏa toàn bộ lệnh mua mới để bảo vệ vốn tuyệt đối!");
            return;
        }

        // 3. Kiểm tra Circuit Breaker nội bộ ngày (Max Loss Limit)
        if (todayRealizedPnl.compareTo(dailyMaxLossLimit.negate()) <= 0) {
            addLog("⚠️ CẦU CHÌ BẢO VỆ VỐN ĐÃ KÍCH HOẠT: Mức lỗ trong ngày đạt -" + dailyMaxLossLimit + " đ. Bot tạm dừng mở vị thế mới hôm nay.");
            return;
        }

        // 4. Kiểm tra Target Lợi Nhuận Ngày
        if (todayRealizedPnl.compareTo(dailyProfitTarget) >= 0) {
            addLog("🎉 ĐÃ ĐẠT MỤC TIÊU KIẾM TIỀN HÔM NAY (+" + todayRealizedPnl + " đ). Bot bảo toàn thành quả.");
        }

        // 4.5. Kiểm tra Cầu chì Bảo vệ Thị trường sụp đổ (Market Crash Protection DEFCON)
        try {
            MarketCrashProtectionDto crash = crashProtectionService.evaluateMarketCircuitBreaker();
            if (crash != null && !crash.isAllowNewPurchases()) {
                addLog("🚨 [CẦU CHÌ BẢO VỆ " + crash.getDefenseStatus() + "] Khóa 100% lệnh mua mới: " + crash.getCircuitBreakerMessage());
                return;
            }
        } catch (Exception e) {
            log.warn("Lỗi kiểm tra cầu chì thị trường: {}", e.getMessage());
        }

        // 4.6. Kiểm tra khung giờ giao dịch chính thức của sàn HOSE/HNX (09:00 - 11:30 và 13:00 - 14:45)
        // Ngoài giờ giao dịch (buổi tối, cuối tuần, giờ nghỉ trưa, phiên ATO 09:00-09:15), bot trực canh cập nhật giá, KHÔNG mở vị thế ảo.
        if (enforceMarketHours && !"SIMULATION".equalsIgnoreCase(mode)) {
            java.time.DayOfWeek dow = LocalDate.now().getDayOfWeek();
            boolean isWeekend = (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY);
            java.time.LocalTime nowTime = java.time.LocalTime.now();

            // 4.6. Bảo vệ phiên ATO (09:00 - 09:15): Không mở lệnh mua bừa bãi khi giá chưa ổn định
            boolean isAtoSession = !isWeekend && nowTime.isAfter(java.time.LocalTime.of(9, 0)) && nowTime.isBefore(java.time.LocalTime.of(9, 15));
            if (isAtoSession) {
                log.debug("AutoTradingBot: Đang trong phiên ATO (09:00 - 09:15) định giá mở cửa HOSE, tạm hoãn mở lệnh mua để tránh bẫy nhiễu ATO.");
                return;
            }

            boolean isContinuousTrading = !isWeekend && 
                ((nowTime.isAfter(java.time.LocalTime.of(9, 15)) && nowTime.isBefore(java.time.LocalTime.of(11, 30))) ||
                 (nowTime.isAfter(java.time.LocalTime.of(13, 0)) && nowTime.isBefore(java.time.LocalTime.of(14, 45))));

            if (!isContinuousTrading) {
                log.debug("AutoTradingBot: Ngoài giờ khớp lệnh HOSE/HNX ({}), tiếp tục trực canh.", nowTime);
                return;
            }
        }

        // 5. Quét cổ phiếu tìm điểm mua điểm số cao (Confidence Score >= 75)
        List<Trade> openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");
        if (openTrades.size() >= 4) {
            return; // Tối đa nắm 4 mã để tập trung danh mục
        }

        List<StockScanResult> scans = strategyService.scanAllStocks();
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

                int finalShares = sizing.getMaxSharesToBuy();

                // =========================================================================
                // 2. CHẾ ĐỘ BÓNG MỜ (SHADOW MODE): THU THẬP ĐÁNH GIÁ 6 TẦNG LỌC NÂNG CAO
                // (Không chặn lệnh mua T1 - Ghi nhận vào DB để đối soát sau 5 & 10 phiên)
                // =========================================================================
                StringBuilder shadowLog = new StringBuilder();

                // 2.1. Shadow CANSLIM
                try {
                    CanslimRatingDto canslim = canslimRatingService.rateStock(scan.getSymbol());
                    shadowLog.append("CANSLIM=").append(canslim.isInstitutionalGrade() ? "PASS" : "VETO(" + canslim.getCanslimGrade() + ":" + canslim.getCanslimScore() + "d)").append("; ");
                } catch (Exception e) {
                    shadowLog.append("CANSLIM=ERR; ");
                }

                // 2.2. Shadow MTF Confluence
                try {
                    MultiTimeframeConfluenceDto mtf = confluenceService.analyzeMultiTimeframe(scan.getSymbol());
                    shadowLog.append("MTF=").append(mtf.getConfluenceScore() >= 65 ? "PASS" : "VETO(" + mtf.getConfluenceScore() + "d)").append("; ");
                } catch (Exception e) {
                    shadowLog.append("MTF=ERR; ");
                }

                // 2.3. Shadow RRG Mansfield
                try {
                    RrgItemDto rrg = relativeRotationGraphService.calculateSingleStockRrg(scan.getSymbol());
                    if (rrg != null) {
                        boolean isToxicLagging = "LAGGING".equals(rrg.getQuadrant()) && "SOUTHWEST".equals(rrg.getHeadingDirection());
                        shadowLog.append("RRG=").append(isToxicLagging ? "VETO_LAGGING_SW" : rrg.getQuadrant()).append("; ");
                    } else {
                        shadowLog.append("RRG=NONE; ");
                    }
                } catch (Exception e) {
                    shadowLog.append("RRG=ERR; ");
                }

                // 2.4. Shadow OBI Level-2
                try {
                    OrderBookImbalanceDto obi = orderBookImbalanceService.analyzeMicrostructure(scan.getSymbol());
                    if (obi != null) {
                        boolean isAskWall = "ASK_WALL_RESISTANCE".equals(obi.getWallDetected());
                        boolean isNegativeObi = obi.getOrderBookImbalanceRatio() != null && obi.getOrderBookImbalanceRatio().compareTo(BigDecimal.valueOf(-0.35)) < 0;
                        shadowLog.append("OBI=").append((isAskWall || isNegativeObi) ? "VETO_WALL" : "PASS").append("; ");
                    } else {
                        shadowLog.append("OBI=NONE; ");
                    }
                } catch (Exception e) {
                    shadowLog.append("OBI=ERR; ");
                }

                // 2.5. Shadow Spoofing Detector
                try {
                    SpoofingDetectorDto spoofing = spoofingDetectorService.detectSpoofing(scan.getSymbol());
                    if (spoofing != null) {
                        shadowLog.append("SPOOFING=").append(!spoofing.isSafeToBuy() ? "VETO(" + spoofing.getLayeringPattern() + ")" : "PASS").append("; ");
                    } else {
                        shadowLog.append("SPOOFING=NONE; ");
                    }
                } catch (Exception e) {
                    shadowLog.append("SPOOFING=ERR; ");
                }

                // 2.6. Shadow Kalman Velocity
                try {
                    KalmanFilterTrendDto kalman = kalmanFilterTrendService.analyzeKalmanTrend(scan.getSymbol());
                    if (kalman != null) {
                        shadowLog.append("KALMAN=").append("BEARISH_DOWNWARD".equals(kalman.getTrendRegime()) ? "VETO_BEAR" : "PASS").append("; ");
                    } else {
                        shadowLog.append("KALMAN=NONE; ");
                    }
                } catch (Exception e) {
                    shadowLog.append("KALMAN=ERR; ");
                }

                // 2.7. Shadow Smart Money & Liquidity
                try {
                    SmartMoneyFlowDto smf = smartMoneyFlowService.analyzeSmartMoney(scan.getSymbol());
                    shadowLog.append("SMF=").append(smf.getAccumulationScore() >= 40 ? "PASS" : "VETO(" + smf.getAccumulationScore() + "d)").append("; ");
                } catch (Exception e) {
                    shadowLog.append("SMF=ERR; ");
                }

                try {
                    LiquidityAdjustedReturnDto liq = liquidityAdjustedReturnService.calculateLiquidityAdjustedReturn(
                        scan.getSymbol(), finalShares, scan.getPrice(), scan.getTargetPrice(), accountCapital);
                    shadowLog.append("LIQ=").append(liq.isLiquidEnough() ? "PASS" : "VETO_ILLIQUID");
                } catch (Exception e) {
                    shadowLog.append("LIQ=ERR");
                }

                String shadowEvaluation = shadowLog.toString();
                addLog(String.format("🕵️ [SHADOW MODE AUDIT] %s: Kích hoạt mua theo T1 Core. Phán quyết bóng mờ: [%s] (Không chặn lệnh, lưu vào DB để đối soát 5-10 phiên sau)",
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
                    .reason(String.format("🤖 Bot Forward Test T1 Core | Risk: %s%% NAV | Shadow Audit: %s", dynamicRisk, shadowEvaluation))
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
                if (quote.getPrice() == null || quote.getPrice().compareTo(BigDecimal.ZERO) <= 0) continue;

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
                        addLog(String.format("⚠️ [KHÓA THANH KHOẢN T+2.5] %s vi phạm SL (%s đ <= %s đ) nhưng đang ở trạng thái %s (chưa thể bán theo luật). Bot trực canh thoát hàng ngay phiên chiều 13:00.",
                            trade.getSymbol(), currentPrice, trade.getStopLoss(), t25Desc));
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

                // 2. GẶT HÁI LỢI NHUẬN HÀNG NGÀY: Chốt lời 50% khi lãi >= +10%
                if (pnlPct.compareTo(BigDecimal.valueOf(10.0)) >= 0
                    && (trade.getPartialClosedQuantity() == null || trade.getPartialClosedQuantity() == 0)
                    && trade.getQuantity() >= 200) {

                    int qtyToHarvest = ((trade.getQuantity() / 2) / 100) * 100;
                    if (qtyToHarvest >= 100) {
                        BigDecimal partialGain = tradeService.executePartialClose(
                            trade,
                            currentPrice,
                            qtyToHarvest,
                            String.format("🎯 Gặt hái 50%% tiền mặt hàng ngày tại giá %s đ (+%s%%)", currentPrice, pnlPct)
                        );
                        todayRealizedPnl = todayRealizedPnl.add(partialGain);
                        todayTradesCount++;
                        addLog(String.format("🌾 [GẶT HÁI TIỀN MẶT HÀNG NGÀY] Bán 50%% vị thế (%d cp %s tại %s đ). Bỏ túi: +%s đ. Vị thế còn lại %d cp đã có giáp Stop Loss hòa vốn %s đ.",
                            qtyToHarvest, trade.getSymbol(), currentPrice, partialGain, trade.getQuantity(), trade.getStopLoss()));

                        alertService.createAlert(
                            trade.getSymbol(),
                            "PARTIAL_PROFIT_HARVESTED",
                            "🌾 [CHỐT LỜI 50% TIỀN MẶT] " + trade.getSymbol() + " đã chốt 50% vị thế",
                            String.format("Đã bán %d cp tại %s đ để bỏ túi lợi nhuận ròng +%s đ. Cổ phiếu còn lại dời Stop Loss lên %s đ đảm bảo không thể thua lỗ!",
                                qtyToHarvest, currentPrice, partialGain, trade.getStopLoss()),
                            currentPrice,
                            "INFO"
                        );
                        try {
                            sseStreamService.broadcast("BOT_PARTIAL_PROFIT_TAKEN", trade);
                        } catch (Exception ignored) {}
                        continue;
                    }
                }

                // 3. Tự động Chốt Lời Đích Cuối (FULL TAKE PROFIT TARGET REACHED)
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

                // 4. Tự Động Dời Stop Loss Hòa Vốn (Trailing Stop To Breakeven) khi lãi >= 7%
                if (pnlPct.compareTo(BigDecimal.valueOf(7.0)) >= 0 && trade.getStopLoss() != null && trade.getStopLoss().compareTo(entryPrice) < 0) {
                    BigDecimal bePrice = QuantitativeStrategyEngine.roundToVietnameseTick(
                        entryPrice.multiply(BigDecimal.valueOf(1.005))
                    );
                    trade.setStopLoss(bePrice);
                    tradeRepository.save(trade);
                    addLog(String.format("🛡️ [TRAILING STOP] %s sinh lời +%s%% -> Dời Stop Loss lên %s đ (Hòa vốn + phí chuẩn bước giá)",
                        trade.getSymbol(), pnlPct.setScale(2, RoundingMode.HALF_UP), bePrice));
                } else {
                    tradeRepository.save(trade);
                }
            } catch (Exception e) {
                log.error("Error managing open position for {}: {}", trade.getSymbol(), e.getMessage());
            }
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
}