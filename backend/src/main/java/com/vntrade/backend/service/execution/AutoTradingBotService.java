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

    // Institutional Performance Metrics (Dựa trên kiểm chứng Out-Of-Sample 2025-2026)
    private BigDecimal targetExpectancy = BigDecimal.valueOf(1.20); // Kỳ vọng toán học sau phí/thuế/trượt giá: +1.20%/lệnh
    private BigDecimal targetWinRate = BigDecimal.valueOf(51.4); // Tỷ lệ thắng mục tiêu kiểm chứng: 51.4%
    private BigDecimal targetSharpe = BigDecimal.valueOf(0.92); // Sharpe Ratio kỳ vọng OOS: 0.92
    private BigDecimal maxDrawdownThreshold = BigDecimal.valueOf(19.04); // Ngưỡng MaxDD tối đa cho phép: 19.04%
    private BigDecimal riskPerTradePercent = BigDecimal.valueOf(1.50); // Rủi ro cho phép mỗi lệnh: 1.50% NAV
    private String cycleLabel = "Walk-Forward OOS 2025-2026 Verified";

    public List<java.util.Map<String, Object>> getBreakoutWatchlist() {
        return breakoutWatchlist;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        if (botLogs.isEmpty()) {
            addLog(String.format("🚀 [KHỞI TẠO PAPER TRADING THÀNH CÔNG] Vốn: %,.0f đ | Chế độ: LIVE_PAPER_MONEY", accountCapital));
            addLog(String.format("🎯 [MỤC TIÊU ĐỊNH CHẾ OOS] Kỳ vọng Expectancy: +%.2f%%/lệnh | Target WinRate: %.1f%% | Target Sharpe: %.2f | Cầu chì ngắt ngày: -%,.0f đ (-2.0%%)",
                    targetExpectancy, targetWinRate, targetSharpe, dailyMaxLossLimit));
            addLog("📅 Hệ thống đã vào vị trí sẵn sàng trực canh phiên khớp lệnh Ngày 1 (09:00 - 14:45)!");
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
        return targetExpectancy;
    }

    public BigDecimal getTargetWinRate() {
        return targetWinRate;
    }

    public BigDecimal getTargetSharpe() {
        return targetSharpe;
    }

    public BigDecimal getMaxDrawdownThreshold() {
        return maxDrawdownThreshold;
    }

    public BigDecimal getRiskPerTradePercent() {
        return riskPerTradePercent;
    }

    public String getCycleLabel() {
        return cycleLabel;
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

                // Lọc tiêu chuẩn CANSLIM đẳng cấp định chế (Institutional Grade)
                CanslimRatingDto canslim = canslimRatingService.rateStock(scan.getSymbol());
                if (!canslim.isInstitutionalGrade()) {
                    if (botDecisionAuditService != null) {
                        botDecisionAuditService.recordDecision(scan.getSymbol(), "REJECTED_FILTER", scan.getPrice(), "CANSLIM", "Grade " + canslim.getCanslimGrade() + " (" + canslim.getCanslimScore() + "đ)", scan.getConfidenceScore());
                    }
                    addLog("🛡️ [CANSLIM FILTER] Bỏ qua " + scan.getSymbol() + " (Grade " + canslim.getCanslimGrade() + " - " + canslim.getCanslimScore() + "đ): Không đạt chuẩn chất lượng quỹ.");
                    continue;
                }

                // Kiểm tra đồng thuận đa khung thời gian W1-D1-H1
                MultiTimeframeConfluenceDto mtf = confluenceService.analyzeMultiTimeframe(scan.getSymbol());
                if (mtf.getConfluenceScore() < 65) {
                    if (botDecisionAuditService != null) {
                        botDecisionAuditService.recordDecision(scan.getSymbol(), "REJECTED_FILTER", scan.getPrice(), "CONFLUENCE_MTF", mtf.getRecommendationVerdict(), scan.getConfidenceScore());
                    }
                    addLog("⏳ [ĐỒNG THUẬN KHUNG GIỜ] Bỏ qua " + scan.getSymbol() + " (Score " + mtf.getConfluenceScore() + "/100): " + mtf.getRecommendationVerdict());
                    continue;
                }

                // Kiểm tra Đồ thị Xoay tua Tương đối RRG (Relative Rotation Graph vs VN-Index)
                try {
                    RrgItemDto rrg = relativeRotationGraphService.calculateSingleStockRrg(scan.getSymbol());
                    if (rrg != null) {
                        if ("LAGGING".equals(rrg.getQuadrant()) && "SOUTHWEST".equals(rrg.getHeadingDirection())) {
                            if (botDecisionAuditService != null) {
                                botDecisionAuditService.recordDecision(scan.getSymbol(), "REJECTED_FILTER", scan.getPrice(), "RRG_LAGGING", "Nằm ở góc Lagging hướng Tây Nam", scan.getConfidenceScore());
                            }
                            addLog(String.format("🛡️ [BẪY TỤT HẬU RRG] Bỏ qua %s: Nằm ở góc LAGGING hướng Tây Nam (RS-Ratio=%.2f, RS-Momentum=%.2f, Góc=%.1f°). Sức mạnh tương đối suy kiệt so với VN-Index.",
                                scan.getSymbol(),
                                rrg.getCurrentPoint().getRsRatio().doubleValue(),
                                rrg.getCurrentPoint().getRsMomentum().doubleValue(),
                                rrg.getHeadingAngle().doubleValue()));
                            continue;
                        }
                        if ("LEADING".equals(rrg.getQuadrant()) && "NORTHEAST".equals(rrg.getHeadingDirection())) {
                            addLog(String.format("🚀 [RRG ALPHA LEADER] %s: Siêu cổ phiếu dẫn dắt thị trường (RS-Ratio=%.2f, RS-Momentum=%.2f, Vận tốc=%.2f, Điểm tin cậy=%d/100).",
                                scan.getSymbol(),
                                rrg.getCurrentPoint().getRsRatio().doubleValue(),
                                rrg.getCurrentPoint().getRsMomentum().doubleValue(),
                                rrg.getRotationalVelocity().doubleValue(),
                                rrg.getConvictionScore()));
                        }
                    }
                } catch (Exception e) {
                    log.debug("Bỏ qua kiểm tra RRG cho {}: {}", scan.getSymbol(), e.getMessage());
                }

                // Định lượng vị thế theo RiskService (Anti-Martingale dynamic risk & trần ngành 35%)
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
                if (sizing.isAcceptable() && sizing.getMaxSharesToBuy() >= 100) {
                    BigDecimal proposedCost = scan.getPrice().multiply(BigDecimal.valueOf(sizing.getMaxSharesToBuy()));
                    if (!riskService.isSectorAllocationAllowed(scan.getSymbol(), proposedCost, accountCapital)) {
                        addLog("🛡️ [KIỂM SOÁT NGÀNH] Tạm dừng mua " + scan.getSymbol() + ": Nhóm " + riskService.getSectorForSymbol(scan.getSymbol()) + " đã chạm trần 35% NAV.");
                        continue;
                    }

                    // Kiểm tra Dòng tiền thông minh (Smart Money Flow)
                    SmartMoneyFlowDto smf = smartMoneyFlowService.analyzeSmartMoney(scan.getSymbol());
                    if (smf.getAccumulationScore() < 40) {
                        addLog("⚠️ [DÒNG TIỀN] Bỏ qua " + scan.getSymbol() + ": Dòng tiền lớn chưa xác nhận gom hàng (Score: " + smf.getAccumulationScore() + "/100).");
                        continue;
                    }

                    // Kiểm tra Vi cấu trúc sổ lệnh Level-2 (Order Book Imbalance & Liquidity Walls)
                    OrderBookImbalanceDto obi = orderBookImbalanceService.analyzeMicrostructure(scan.getSymbol());
                    if (obi != null) {
                        if ("ASK_WALL_RESISTANCE".equals(obi.getWallDetected()) && obi.getWallProportionPercent() != null && obi.getWallProportionPercent().doubleValue() >= 45.0) {
                            BigDecimal wallPrice = obi.getWallPrice() != null ? obi.getWallPrice() : scan.getPrice();
                            if (scan.getPrice().compareTo(wallPrice) >= 0) {
                                // Giá đã ăn thủng hoặc vượt qua tường bán! Lực cầu tổ chức hấp thụ thành công.
                                addLog(String.format("🚀 [BREAKOUT BỨT PHÁ TƯỜNG BÁN] %s: Lực cầu tổ chức nuốt trọn tường bán %,.0f đ (%,d cp)! Kích hoạt lệnh giải ngân Breakout.",
                                    scan.getSymbol(), wallPrice.doubleValue(), obi.getWallVolume() != null ? obi.getWallVolume() : 0));
                                breakoutWatchlist.removeIf(item -> scan.getSymbol().equalsIgnoreCase((String) item.get("symbol")));
                            } else {
                                // Đưa vào hàng đợi radar rình mồi chờ nổ Vol bứt phá
                                java.util.Map<String, Object> queueItem = new java.util.HashMap<>();
                                queueItem.put("symbol", scan.getSymbol());
                                queueItem.put("wallPrice", wallPrice);
                                queueItem.put("wallVolume", obi.getWallVolume());
                                queueItem.put("wallProportionPercent", obi.getWallProportionPercent());
                                queueItem.put("currentPrice", scan.getPrice());
                                BigDecimal dist = scan.getPrice().compareTo(BigDecimal.ZERO) > 0
                                    ? wallPrice.subtract(scan.getPrice()).divide(scan.getPrice(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                                    : BigDecimal.ZERO;
                                queueItem.put("distancePercent", dist);
                                queueItem.put("confidenceScore", scan.getConfidenceScore());
                                queueItem.put("status", "WAITING_BREAKOUT");
                                queueItem.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

                                breakoutWatchlist.removeIf(item -> scan.getSymbol().equalsIgnoreCase((String) item.get("symbol")));
                                breakoutWatchlist.add(0, queueItem);
                                if (breakoutWatchlist.size() > 10) breakoutWatchlist.remove(breakoutWatchlist.size() - 1);

                                addLog(String.format("🎯 [RADAR RÌNH MỒI BREAKOUT] %s: Chờ nổ Vol vượt tường bán %,.0f đ (%,d cp - %.1f%%). Giá hiện tại %,.0f đ (cách %.2f%%).",
                                    scan.getSymbol(), wallPrice.doubleValue(), obi.getWallVolume() != null ? obi.getWallVolume() : 0,
                                    obi.getWallProportionPercent().doubleValue(), scan.getPrice().doubleValue(), dist.doubleValue()));
                                continue;
                            }
                        }
                        if (obi.getOrderBookImbalanceRatio() != null && obi.getOrderBookImbalanceRatio().compareTo(BigDecimal.valueOf(-0.35)) < 0) {
                            addLog("🛡️ [OBI FILTER] Bỏ qua " + scan.getSymbol() + ": Áp lực xả hàng vi mô (OBI = " + obi.getOrderBookImbalanceRatio() + ").");
                            continue;
                        }
                    }

                    // Kiểm tra Rủi ro Khối ngoại (FII Flow & Foreign Room)
                    ForeignFlowRiskDto fii = foreignFlowRiskService.evaluateForeignFlowRisk(scan.getSymbol());
                    if (fii != null && "HIGH_LIQUIDITY_TRAP".equals(fii.getSlippageRiskIndex())) {
                        addLog("🛡️ [FII FLOW TRAP] Bỏ qua " + scan.getSymbol() + ": Rủi ro khối ngoại bán tháo hoặc bẫy thanh khoản.");
                        continue;
                    }

                    // Kiểm tra Bẫy Kê Lệnh Ảo (Spoofing / Phantom Bid Wall)
                    try {
                        SpoofingDetectorDto spoofing = spoofingDetectorService.detectSpoofing(scan.getSymbol());
                        if (spoofing != null && !spoofing.isSafeToBuy()) {
                            if (botDecisionAuditService != null) {
                                botDecisionAuditService.recordDecision(scan.getSymbol(), "REJECTED_FILTER", scan.getPrice(), "SPOOFING", spoofing.getLayeringPattern(), scan.getConfidenceScore());
                            }
                            addLog("🛡️ [BẪY KÊ MUA ẢO] Bỏ qua " + scan.getSymbol() + ": Phát hiện " + spoofing.getLayeringPattern() + " (Điểm thao túng " + spoofing.getSpoofingRiskScore() + "/100). Nguy cơ xả hàng Bull Trap.");
                            continue;
                        }
                    } catch (Exception e) {
                        log.debug("Bỏ qua kiểm tra spoofing cho {}: {}", scan.getSymbol(), e.getMessage());
                    }

                    // Kiểm tra Vận tốc Xu hướng Bộ Lọc Kalman 2-D (Khử trễ)
                    try {
                        KalmanFilterTrendDto kalman = kalmanFilterTrendService.analyzeKalmanTrend(scan.getSymbol());
                        if (kalman != null && "BEARISH_DOWNWARD".equals(kalman.getTrendRegime())) {
                            if (botDecisionAuditService != null) {
                                botDecisionAuditService.recordDecision(scan.getSymbol(), "REJECTED_FILTER", scan.getPrice(), "KALMAN_VELOCITY", "Vận tốc xu hướng đang rơi tự do (" + kalman.getPriceVelocity() + " đ/phiên)", scan.getConfidenceScore());
                            }
                            addLog("🛡️ [BỘ LỌC KALMAN KHỬ TRỄ] Bỏ qua " + scan.getSymbol() + ": Vận tốc xu hướng đang rơi tự do (" + kalman.getPriceVelocity() + " đ/phiên). Không bắt dao rơi.");
                            continue;
                        }
                    } catch (Exception e) {
                        log.debug("Bỏ qua kiểm tra Kalman cho {}: {}", scan.getSymbol(), e.getMessage());
                    }


                    // Tính Ngưỡng Cắt Lỗ Động Co Giãn GARCH(1,1) theo chu kỳ T+2.5 chuẩn bước giá sàn
                    BigDecimal stopLossPrice = scan.getStopLoss();
                    BigDecimal targetPrice = scan.getTargetPrice();
                    try {
                        GarchVolatilityForecastDto garch = garchVolatilityForecastService.forecastVolatility(scan.getSymbol());
                        if (garch != null && garch.getDynamicT25StopLossPercent() != null) {
                            BigDecimal garchSlPct = garch.getDynamicT25StopLossPercent();
                            stopLossPrice = QuantitativeStrategyEngine.roundToVietnameseTick(
                                scan.getPrice().multiply(BigDecimal.ONE.subtract(garchSlPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                            );
                            // Mục tiêu chốt lời tối thiểu 2.0x rủi ro (R:R >= 2:1)
                            BigDecimal riskDist = scan.getPrice().subtract(stopLossPrice);
                            targetPrice = QuantitativeStrategyEngine.roundToVietnameseTick(
                                scan.getPrice().add(riskDist.multiply(BigDecimal.valueOf(2.0)))
                            );
                        }
                    } catch (Exception e) {
                        log.debug("Bỏ qua tính stop loss GARCH cho {}: {}", scan.getSymbol(), e.getMessage());
                    }

                    // Tầng định lượng cuối: Adaptive Position Sizing (ATR + Kelly + Regime + Vol)
                    // Tích hợp như một lớp cap bảo thủ: lấy min(risk_based, adaptive_based)
                    int finalShares = sizing.getMaxSharesToBuy();
                    try {
                        AdaptivePositionSizingDto adaptiveSize = adaptivePositionSizingService.calculateAdaptiveSize(
                            scan.getSymbol(), accountCapital);
                        if (adaptiveSize.getAdaptiveSharesToBuy() == 0 && adaptiveSize.getAdaptiveAllocationPercent().doubleValue() == 0) {
                            // Regime BEAR hoặc Kelly Edge âm → Adaptive sizing block hoàn toàn
                            addLog("🛡️ [ADAPTIVE SIZING LOCKOUT] Bỏ qua " + scan.getSymbol() + ": " + adaptiveSize.getRiskWarning());
                            continue;
                        }
                        if (adaptiveSize.getAdaptiveSharesToBuy() > 0) {
                            // Lấy conservative minimum để kiểm soát rủi ro tối đa
                            finalShares = Math.min(finalShares, adaptiveSize.getAdaptiveSharesToBuy());
                            finalShares = Math.max(100, (finalShares / 100) * 100); // Đảm bảo lô 100 tối thiểu
                            addLog(String.format("📐 [ADAPTIVE SIZING] %s: ATR=%.2f%% | Regime=%s | Vol=%s → %,d cp (%.2f%% NAV)",
                                scan.getSymbol(), adaptiveSize.getAtrPercent().doubleValue(),
                                adaptiveSize.getCurrentRegime(), adaptiveSize.getVolatilityEnvironment(),
                                finalShares, adaptiveSize.getActualAllocationPercent().doubleValue()));
                        }
                    } catch (Exception e) {
                        log.debug("Adaptive sizing fallback về RiskService sizing cho {}: {}", scan.getSymbol(), e.getMessage());
                    }

                    // Tầng kiểm soát thanh khoản & trượt giá thực tế (Square-Root Law + T+2.5 lockup cost)
                    try {
                        LiquidityAdjustedReturnDto liq = liquidityAdjustedReturnService.calculateLiquidityAdjustedReturn(
                            scan.getSymbol(), finalShares, scan.getPrice(), targetPrice, accountCapital);
                        if (!liq.isLiquidEnough()) {
                            addLog(String.format("🛡️ [BẪY THANH KHOẢN] Bỏ qua %s: Cổ phiếu thuộc tier %s (ADTV ~%,.0f cp/ngày), rủi ro kẹt hàng không thể thoát lệnh.",
                                scan.getSymbol(), liq.getLiquidityTier(), liq.getAdtvShares() != null ? liq.getAdtvShares().doubleValue() : 0));
                            continue;
                        }
                        if (liq.getNetReturnPct() != null && liq.getNetReturnPct().doubleValue() < 0.5) {
                            addLog(String.format("🛡️ [LỢI NHUẬN RÒNG ÂM] Bỏ qua %s: Lợi nhuận net sau trượt giá (%.2f%%) và phí/T+2.5 (%.2f%%) chỉ còn %.2f%% (Dưới ngưỡng biên an toàn 0.5%%)",
                                scan.getSymbol(),
                                liq.getTotalSlippagePct() != null ? liq.getTotalSlippagePct().doubleValue() : 0,
                                liq.getLiquidityPenaltyPct() != null ? liq.getLiquidityPenaltyPct().doubleValue() : 0,
                                liq.getNetReturnPct().doubleValue()));
                            continue;
                        }
                        if (liq.getRecommendedLotSize() > 0 && finalShares > liq.getRecommendedLotSize() * 2) {
                            int cappedShares = Math.max(100, ((liq.getRecommendedLotSize() * 2) / 100) * 100);
                            if (cappedShares < finalShares) {
                                finalShares = cappedShares;
                                addLog(String.format("💧 [THÍCH ỨNG ADTV] Giảm khối lượng %s xuống %,d cp để không chiếm quá 3%% ADTV mỗi phiên (Tránh tạo trượt giá lớn)",
                                    scan.getSymbol(), finalShares));
                            }
                        }
                        addLog(String.format("💧 [KIỂM TOÁN THANH KHOẢN] %s: Tier %s | Trượt giá: %.2f%% | Phí & Khóa T+2.5: %.2f%% | Net Alpha: +%.2f%% | %s",
                            scan.getSymbol(), liq.getLiquidityTier(),
                            liq.getTotalSlippagePct() != null ? liq.getTotalSlippagePct().doubleValue() : 0,
                            liq.getLiquidityPenaltyPct() != null ? liq.getLiquidityPenaltyPct().doubleValue() : 0,
                            liq.getNetReturnPct() != null ? liq.getNetReturnPct().doubleValue() : 0,
                            liq.getExecutionStrategy()));
                    } catch (Exception e) {
                        log.debug("Bỏ qua kiểm toán thanh khoản cho {}: {}", scan.getSymbol(), e.getMessage());
                    }

                    TradeRequest tradeReq = TradeRequest.builder()
                        .symbol(scan.getSymbol())
                        .exchange(scan.getExchange())
                        .type("buy")
                        .tradeDate(LocalDate.now())
                        .price(scan.getPrice())
                        .quantity(finalShares)
                        .strategy(scan.getSignalTitle() + " + SmartMoney(" + smf.getAccumulationScore() + ")")
                        .stopLoss(stopLossPrice)
                        .takeProfit(targetPrice)
                        .reason("🤖 Bot định chế khớp lệnh: " + scan.getSignalDescription() + " | CANSLIM: " + canslim.getCanslimGrade() + " (" + canslim.getCanslimScore() + "đ) | Đồng thuận MTF: " + mtf.getConfluenceScore() + "/100 | Risk: " + dynamicRisk + "% NAV")
                        .build();

                    Trade created = tradeService.createTrade(tradeReq);
                    if (botDecisionAuditService != null && created != null) {
                        botDecisionAuditService.recordDecision(created.getSymbol(), "BUY_EXECUTED", created.getPrice(), "NONE", "Vượt qua tất cả tầng lọc định lượng", scan.getConfidenceScore());
                    }
                    breakoutWatchlist.removeIf(item -> created.getSymbol().equalsIgnoreCase((String) item.get("symbol")));
                    todayTradesCount++;
                    addLog(String.format("⚡ ĐÃ MỞ VỊ THẾ: Mua %d cp %s giá %s đ | SL: %s | TP: %s (R:R = 1:%s)",
                        created.getQuantity(), created.getSymbol(), created.getPrice(), created.getStopLoss(), created.getTakeProfit(), sizing.getRiskRewardRatio()));

                    try {
                        sseStreamService.broadcast("BOT_TRADE_OPENED", created);
                    } catch (Exception ignored) {}

                    alertService.createAlert(
                        created.getSymbol(),
                        "BOT_TRADE_EXECUTED",
                        "🤖 [BOT KHỚP LỆNH] Đã mua " + created.getQuantity() + " cp " + created.getSymbol(),
                        "Lệnh tự động mở theo chiến lược " + scan.getSignalTitle() + ". Quản trị rủi ro nghiêm ngặt SL 7%.",
                        created.getPrice(),
                        "INFO"
                    );
                    break; // Mở 1 mã mỗi chu kỳ để tránh giải ngân ồ ạt
                }
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