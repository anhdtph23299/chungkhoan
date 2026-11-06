package com.vntrade.backend.service.futures;

import com.vntrade.backend.dto.*;
import com.vntrade.backend.entity.FuturesPosition;
import com.vntrade.backend.repository.FuturesPositionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class VN30FuturesTradingService {

    private final VN30FuturesStrategyEngine strategyEngine;
    private final FuturesPositionRepository futuresPositionRepository;

    // Hằng số tài chính phái sinh HNX
    public static final BigDecimal POINT_MULTIPLIER = BigDecimal.valueOf(100_000); // 100.000 đ/điểm
    public static final BigDecimal MARGIN_RATE = BigDecimal.valueOf(0.175);        // 17.5% ký quỹ VSDC
    public static final BigDecimal ROUNDTRIP_FEE_PER_CONTRACT = BigDecimal.valueOf(9_400); // Phí sở + môi giới 2 chiều

    private final AtomicLong positionIdGen = new AtomicLong(1000);
    private final Map<String, FuturesPositionDto> openPositions = new ConcurrentHashMap<>();
    private final List<FuturesPositionDto> positionHistory = new CopyOnWriteArrayList<>();

    // Cấu hình Bot phái sinh - MẶC ĐỊNH TẮT TỰ ĐỘNG ĐỂ BẢO VỆ TÀI KHOẢN THEO ĐỀ XUẤT 1
    private boolean autoTrading = false;
    private String mode = "LIVE_PAPER";
    private BigDecimal capital = BigDecimal.valueOf(100_000_000); // Vốn 100M VND
    private int maxContracts = 2;                                // Mặc định 2 HĐ (an toàn trong 100M)
    private BigDecimal stopLossPoints = BigDecimal.valueOf(2.5);  // Cắt lỗ 2.5 điểm
    private BigDecimal takeProfitPoints = BigDecimal.valueOf(5.0);// Chốt lời 5.0 điểm
    private BigDecimal trailingStopTrigger = BigDecimal.valueOf(3.0); // Kích hoạt khi lãi >= 3.0 điểm
    private BigDecimal trailingStopDistance = BigDecimal.valueOf(1.5); // Bám sau 1.5 điểm
    private boolean closeBeforeAtc = true;

    // Nhật ký hoạt động trong ngày & Cầu chì chống Overtrading
    private BigDecimal todayRealizedPnlVnd = BigDecimal.ZERO;
    private BigDecimal todayPnlPoints = BigDecimal.ZERO;
    private int todayTradesCount = 0;
    private LocalDateTime lastCloseTime = null;
    private java.time.LocalDate currentTradingDate = java.time.LocalDate.now();
    private static final int MAX_DAILY_FUTURES_TRADES = 3;
    private static final int COOLDOWN_MINUTES_AFTER_CLOSE = 15;
    private static final BigDecimal DAILY_FUTURES_CIRCUIT_BREAKER = BigDecimal.valueOf(-1_500_000);
    private final List<String> botLogs = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void initFromDatabase() {
        try {
            // Nạp các vị thế OPEN từ Database để không mất trạng thái khi restart
            List<FuturesPosition> dbOpen = futuresPositionRepository.findByStatus("OPEN");
            for (FuturesPosition p : dbOpen) {
                FuturesPositionDto dto = toDto(p);
                openPositions.put(dto.getId(), dto);
            }

            // Nạp lịch sử vị thế đã đóng gần nhất
            List<FuturesPosition> dbClosed = futuresPositionRepository.findByStatusOrderByCloseTimeDesc("CLOSED");
            for (FuturesPosition p : dbClosed) {
                positionHistory.add(toDto(p));
            }

            // Đồng bộ ID generator
            long maxId = 1000;
            List<FuturesPosition> all = futuresPositionRepository.findAll();
            for (FuturesPosition p : all) {
                if (p.getId() != null && p.getId().startsWith("VN30F-")) {
                    try {
                        long num = Long.parseLong(p.getId().substring(6));
                        if (num > maxId) maxId = num;
                    } catch (Exception ignored) {}
                }
            }
            positionIdGen.set(maxId);

            // Tính lãi/lỗ hôm nay từ DB
            java.time.LocalDate today = java.time.LocalDate.now();
            BigDecimal todayDbPnl = dbClosed.stream()
                .filter(p -> p.getCloseTime() != null && today.equals(p.getCloseTime().toLocalDate()))
                .map(p -> p.getNetPnlVnd() != null ? p.getNetPnlVnd() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            this.todayRealizedPnlVnd = todayDbPnl;

            addLog(String.format("💾 [ĐỒNG BỘ DB PHÁI SINH THÀNH CÔNG] Đã nạp %d vị thế mở, %d lịch sử lệnh. AutoTrading=%s.",
                openPositions.size(), positionHistory.size(), autoTrading ? "BẬT" : "TẮT (AN TOÀN)"));
        } catch (Exception e) {
            log.warn("Lỗi nạp dữ liệu phái sinh từ DB: {}", e.getMessage());
        }
    }

    public FuturesBotConfigDto getBotConfig() {
        return FuturesBotConfigDto.builder()
            .autoTrading(autoTrading)
            .mode(mode)
            .capital(capital)
            .maxContracts(maxContracts)
            .stopLossPoints(stopLossPoints)
            .takeProfitPoints(takeProfitPoints)
            .trailingStopTrigger(trailingStopTrigger)
            .trailingStopDistance(trailingStopDistance)
            .closeBeforeAtc(closeBeforeAtc)
            .preferredStrategy("HYBRID_ALPHA")
            .todayRealizedPnlVnd(todayRealizedPnlVnd)
            .todayPnlPoints(todayPnlPoints)
            .todayTradesCount(todayTradesCount)
            .build();
    }

    public void updateBotConfig(FuturesBotConfigDto update) {
        if (update.getAutoTrading() != null) this.autoTrading = update.getAutoTrading();
        if (update.getCapital() != null && update.getCapital().compareTo(BigDecimal.ZERO) > 0) this.capital = update.getCapital();
        if (update.getMaxContracts() != null && update.getMaxContracts() > 0) this.maxContracts = Math.min(update.getMaxContracts(), 4);
        if (update.getStopLossPoints() != null) this.stopLossPoints = update.getStopLossPoints();
        if (update.getTakeProfitPoints() != null) this.takeProfitPoints = update.getTakeProfitPoints();
        if (update.getTrailingStopTrigger() != null) this.trailingStopTrigger = update.getTrailingStopTrigger();
        if (update.getTrailingStopDistance() != null) this.trailingStopDistance = update.getTrailingStopDistance();
        if (update.getCloseBeforeAtc() != null) this.closeBeforeAtc = update.getCloseBeforeAtc();
        addLog(String.format("⚙️ Cập nhật cấu hình: Auto=%s, MaxContracts=%d, SL=%.1f pts, TP=%.1f pts",
            autoTrading, maxContracts, stopLossPoints.doubleValue(), takeProfitPoints.doubleValue()));
    }

    public void setAutoTrading(boolean enabled) {
        this.autoTrading = enabled;
        addLog("Robot Phái Sinh VN30F: " + (enabled ? "KÍCH HOẠT TỰ ĐỘNG SĂN LỆNH" : "TẠM DỪNG VÀO LỆNH MỚI (CHẾ ĐỘ AN TOÀN)"));
    }

    public List<FuturesPositionDto> getOpenPositions() {
        FuturesQuoteDto quote = strategyEngine.getCurrentQuote();
        BigDecimal currentPrice = quote.getCurrentPrice();

        List<FuturesPositionDto> list = new ArrayList<>(openPositions.values());
        for (FuturesPositionDto pos : list) {
            updatePositionPnL(pos, currentPrice);
        }
        return list;
    }

    public List<FuturesPositionDto> getPositionHistory() {
        return new ArrayList<>(positionHistory);
    }

    public List<String> getBotLogs() {
        return new ArrayList<>(botLogs);
    }

    /**
     * Tổng lãi/lỗ phái sinh đã chốt trong lịch sử (từ DB)
     */
    public BigDecimal getTotalRealizedPnlVnd() {
        try {
            BigDecimal dbPnl = futuresPositionRepository.getTotalRealizedPnlVnd();
            return dbPnl != null ? dbPnl : BigDecimal.ZERO;
        } catch (Exception e) {
            return todayRealizedPnlVnd;
        }
    }

    /**
     * Tổng lãi/lỗ chưa thực hiện (Unrealized PnL) của các vị thế đang mở
     */
    public BigDecimal getUnrealizedPnlVnd() {
        FuturesQuoteDto quote = strategyEngine.getCurrentQuote();
        BigDecimal currentPrice = quote.getCurrentPrice();
        BigDecimal total = BigDecimal.ZERO;
        for (FuturesPositionDto pos : openPositions.values()) {
            updatePositionPnL(pos, currentPrice);
            if (pos.getNetPnlVnd() != null) {
                total = total.add(pos.getNetPnlVnd());
            }
        }
        return total;
    }

    /**
     * Mở vị thế mới (LONG hoặc SHORT)
     */
    public synchronized FuturesPositionDto openPosition(String side, int contracts, BigDecimal entryPrice,
                                                        BigDecimal slPrice, BigDecimal tpPrice, String reason) {
        String cleanSide = side.toUpperCase().trim();
        if (!cleanSide.equals("LONG") && !cleanSide.equals("SHORT")) {
            throw new IllegalArgumentException("Vị thế phải là LONG hoặc SHORT");
        }

        if (contracts <= 0) contracts = 1;

        BigDecimal safeEntry = entryPrice != null ? VN30FuturesStrategyEngine.roundToFuturesTick(entryPrice)
            : strategyEngine.getCurrentQuote().getCurrentPrice();

        if (safeEntry == null || safeEntry.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Không thể mở vị thế: Giá vào không hợp lệ ({})", safeEntry);
            return null;
        }

        BigDecimal safeSl = slPrice != null ? VN30FuturesStrategyEngine.roundToFuturesTick(slPrice)
            : (cleanSide.equals("LONG") ? safeEntry.subtract(stopLossPoints) : safeEntry.add(stopLossPoints));

        BigDecimal safeTp = tpPrice != null ? VN30FuturesStrategyEngine.roundToFuturesTick(tpPrice)
            : (cleanSide.equals("LONG") ? safeEntry.add(takeProfitPoints) : safeEntry.subtract(takeProfitPoints));

        // Kiểm tra tiền ký quỹ
        BigDecimal marginReq = safeEntry.multiply(POINT_MULTIPLIER).multiply(MARGIN_RATE).multiply(BigDecimal.valueOf(contracts));
        if (marginReq.compareTo(capital) > 0) {
            log.warn("Ký quỹ không đủ: Cần {}, Vốn {}", marginReq, capital);
            contracts = 1;
            marginReq = safeEntry.multiply(POINT_MULTIPLIER).multiply(MARGIN_RATE);
        }

        String posId = "VN30F-" + positionIdGen.incrementAndGet();
        FuturesPositionDto position = FuturesPositionDto.builder()
            .id(posId)
            .symbol("VN30F1M")
            .side(cleanSide)
            .contracts(contracts)
            .entryPrice(safeEntry)
            .currentPrice(safeEntry)
            .stopLossPrice(safeSl)
            .takeProfitPrice(safeTp)
            .trailingStopPrice(null)
            .pnlPoints(BigDecimal.ZERO)
            .grossPnlVnd(BigDecimal.ZERO)
            .feesVnd(ROUNDTRIP_FEE_PER_CONTRACT.multiply(BigDecimal.valueOf(contracts)))
            .netPnlVnd(ROUNDTRIP_FEE_PER_CONTRACT.multiply(BigDecimal.valueOf(-contracts)))
            .marginUsed(marginReq.setScale(0, RoundingMode.HALF_UP))
            .status("OPEN")
            .openTime(LocalDateTime.now())
            .build();

        openPositions.put(posId, position);
        todayTradesCount++;

        // Lưu vào Database vĩnh viễn
        savePositionToDb(position);

        addLog(String.format("🎯 [MỞ VỊ THẾ %s] %d HĐ @ %.1f | SL: %.1f | TP: %.1f | Ký quỹ: %,.0f đ | Lý do: %s",
            cleanSide, contracts, safeEntry.doubleValue(), safeSl.doubleValue(), safeTp.doubleValue(),
            marginReq.doubleValue(), reason != null ? reason : "Tín hiệu định lượng"));

        return position;
    }

    /**
     * Đóng vị thế cụ thể
     */
    public synchronized FuturesPositionDto closePosition(String positionId, BigDecimal closePrice, String reason) {
        FuturesPositionDto pos = openPositions.remove(positionId);
        if (pos == null) return null;

        BigDecimal safeClose = closePrice != null ? VN30FuturesStrategyEngine.roundToFuturesTick(closePrice)
            : strategyEngine.getCurrentQuote().getCurrentPrice();

        updatePositionPnL(pos, safeClose);
        pos.setStatus("CLOSED");
        pos.setClosePrice(safeClose);
        pos.setCloseReason(reason != null ? reason : "THỦ_CÔNG");
        pos.setCloseTime(LocalDateTime.now());

        todayRealizedPnlVnd = todayRealizedPnlVnd.add(pos.getNetPnlVnd());
        todayPnlPoints = todayPnlPoints.add(pos.getPnlPoints());
        lastCloseTime = LocalDateTime.now();
        positionHistory.add(0, pos);

        // Lưu cập nhật vào Database
        savePositionToDb(pos);

        addLog(String.format("🏁 [ĐÓNG VỊ THẾ %s] %d HĐ @ %.1f (Vào %.1f) | Lãi/Lỗ: %+.1f pts (%+,.0f đ) | Lý do: %s",
            pos.getSide(), pos.getContracts(), safeClose.doubleValue(), pos.getEntryPrice().doubleValue(),
            pos.getPnlPoints().doubleValue(), pos.getNetPnlVnd().doubleValue(), pos.getCloseReason()));

        return pos;
    }

    public synchronized void closeAllPositions(String reason) {
        FuturesQuoteDto quote = strategyEngine.getCurrentQuote();
        for (String id : new ArrayList<>(openPositions.keySet())) {
            closePosition(id, quote.getCurrentPrice(), reason);
        }
    }

    /**
     * Vòng lặp giám sát vị thế & Quét tín hiệu định lượng tự động
     */
    @Scheduled(fixedDelay = 15000)
    public void runFuturesBotLoop() {
        try {
            FuturesQuoteDto quote = strategyEngine.getCurrentQuote();
            BigDecimal currentPrice = quote.getCurrentPrice();

            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            // 1. Giám sát các vị thế đang mở: Trailing Stop, TP, SL
            for (FuturesPositionDto pos : openPositions.values()) {
                updatePositionPnL(pos, currentPrice);

                boolean isLong = "LONG".equalsIgnoreCase(pos.getSide());
                BigDecimal diff = isLong ? currentPrice.subtract(pos.getEntryPrice()) : pos.getEntryPrice().subtract(currentPrice);

                // Kiểm tra Chốt lời Take Profit
                if ((isLong && currentPrice.compareTo(pos.getTakeProfitPrice()) >= 0) ||
                    (!isLong && currentPrice.compareTo(pos.getTakeProfitPrice()) <= 0)) {
                    closePosition(pos.getId(), currentPrice, "CHỐT_LỜI_ĐÍCH (TP Hit +" + diff + " pts)");
                    continue;
                }

                // Kiểm tra Cắt lỗ Stop Loss
                if ((isLong && currentPrice.compareTo(pos.getStopLossPrice()) <= 0) ||
                    (!isLong && currentPrice.compareTo(pos.getStopLossPrice()) >= 0)) {
                    closePosition(pos.getId(), currentPrice, "CẮT_LỖ_KỶ_LUẬT (SL Hit " + diff + " pts)");
                    continue;
                }

                // 1.3a. GIÁP HÒA VỐN LÃO LÀNG (Break-Even Shield khi lãi >= +2.0 điểm)
                if (diff.compareTo(BigDecimal.valueOf(2.0)) >= 0) {
                    if (isLong) {
                        BigDecimal breakEvenPlus = VN30FuturesStrategyEngine.roundToFuturesTick(pos.getEntryPrice().add(BigDecimal.valueOf(0.4)));
                        if (pos.getStopLossPrice().compareTo(breakEvenPlus) < 0) {
                            pos.setStopLossPrice(breakEvenPlus);
                            savePositionToDb(pos);
                            addLog(String.format("🛡️ [GIÁP HÒA VỐN T+0] Dời SL vị thế LONG %s lên %.1f (+0.4 pts trên giá vốn). Khóa rủi ro thua lỗ 100%%!",
                                pos.getId(), breakEvenPlus.doubleValue()));
                        }
                    } else {
                        BigDecimal breakEvenPlus = VN30FuturesStrategyEngine.roundToFuturesTick(pos.getEntryPrice().subtract(BigDecimal.valueOf(0.4)));
                        if (pos.getStopLossPrice().compareTo(breakEvenPlus) > 0) {
                            pos.setStopLossPrice(breakEvenPlus);
                            savePositionToDb(pos);
                            addLog(String.format("🛡️ [GIÁP HÒA VỐN T+0] Dời SL vị thế SHORT %s xuống %.1f (+0.4 pts dưới giá vốn). Khóa rủi ro thua lỗ 100%%!",
                                pos.getId(), breakEvenPlus.doubleValue()));
                        }
                    }
                }

                // 1.3b. CHỐT LỜI TỪNG PHẦN 2 GIAI ĐOẠN (Chốt 50% HĐ khi lãi >= +3.0 điểm = +300k/HĐ)
                if (pos.getContracts() >= 2 && diff.compareTo(BigDecimal.valueOf(3.0)) >= 0) {
                    int harvestQty = pos.getContracts() / 2;
                    int remainQty = pos.getContracts() - harvestQty;
                    BigDecimal partialGross = diff.multiply(POINT_MULTIPLIER).multiply(BigDecimal.valueOf(harvestQty)).setScale(0, RoundingMode.HALF_UP);
                    BigDecimal partialFee = ROUNDTRIP_FEE_PER_CONTRACT.multiply(BigDecimal.valueOf(harvestQty));
                    BigDecimal partialNet = partialGross.subtract(partialFee);

                    pos.setContracts(remainQty);
                    pos.setFeesVnd(ROUNDTRIP_FEE_PER_CONTRACT.multiply(BigDecimal.valueOf(remainQty)));
                    pos.setMarginUsed(pos.getEntryPrice().multiply(POINT_MULTIPLIER).multiply(MARGIN_RATE).multiply(BigDecimal.valueOf(remainQty)).setScale(0, RoundingMode.HALF_UP));

                    todayRealizedPnlVnd = todayRealizedPnlVnd.add(partialNet);
                    todayPnlPoints = todayPnlPoints.add(diff);
                    savePositionToDb(pos);

                    addLog(String.format("🌾 [GẶT HÁI TIỀN TƯƠI T+0] Đã chốt 50%% (%d HĐ %s @ %.1f) đút túi +%s đ (+%.1f pts). Giữ %d HĐ còn lại gồng tới TP %.1f!",
                        harvestQty, pos.getSide(), currentPrice.doubleValue(), String.format("%,.0f", partialNet.doubleValue()),
                        diff.doubleValue(), remainQty, pos.getTakeProfitPrice().doubleValue()));
                }

                // Kiểm tra Kích hoạt & Dời Trailing Stop
                if (diff.compareTo(trailingStopTrigger) >= 0) {
                    if (isLong) {
                        BigDecimal newTrail = currentPrice.subtract(trailingStopDistance);
                        if (pos.getTrailingStopPrice() == null || newTrail.compareTo(pos.getTrailingStopPrice()) > 0) {
                            pos.setTrailingStopPrice(VN30FuturesStrategyEngine.roundToFuturesTick(newTrail));
                            savePositionToDb(pos);
                        }
                    } else {
                        BigDecimal newTrail = currentPrice.add(trailingStopDistance);
                        if (pos.getTrailingStopPrice() == null || newTrail.compareTo(pos.getTrailingStopPrice()) < 0) {
                            pos.setTrailingStopPrice(VN30FuturesStrategyEngine.roundToFuturesTick(newTrail));
                            savePositionToDb(pos);
                        }
                    }
                }

                // Kiểm tra Chạm Trailing Stop
                if (pos.getTrailingStopPrice() != null) {
                    if ((isLong && currentPrice.compareTo(pos.getTrailingStopPrice()) <= 0) ||
                        (!isLong && currentPrice.compareTo(pos.getTrailingStopPrice()) >= 0)) {
                        closePosition(pos.getId(), currentPrice, "TRAILING_STOP_BẢO_VỆ_LÃI (" + diff + " pts)");
                        continue;
                    }
                }
            }

            // 2. Tự động tất toán trước phiên ATC (14:25 - 14:30) nếu cấu hình closeBeforeAtc = true
            LocalTime nowTime = LocalTime.now();
            if (closeBeforeAtc && nowTime.isAfter(LocalTime.of(14, 25)) && nowTime.isBefore(LocalTime.of(14, 45))) {
                if (!openPositions.isEmpty()) {
                    addLog("🛡️ [BẢO VỆ VỐN QUA ĐÊM] 14:25 chiều - Tự động tất toán toàn bộ vị thế trước giờ ATC.");
                    closeAllPositions("TẤT_TOÁN_TRƯỚC_ATC_TRÁNH_QUA_ĐÊM");
                }
                return;
            }

            // Reset thống kê ngày mới tự động
            if (!java.time.LocalDate.now().equals(currentTradingDate)) {
                currentTradingDate = java.time.LocalDate.now();
                todayRealizedPnlVnd = BigDecimal.ZERO;
                todayPnlPoints = BigDecimal.ZERO;
                todayTradesCount = 0;
                lastCloseTime = null;
                addLog("🌅 [PHIÊN MỚI PHÁI SINH T+0] Đã làm mới bộ đếm lệnh & PnL trong ngày.");
            }

            // 3. KIỂM SOÁT GIỜ KHỚP LỆNH LIÊN TỤC PHÁI SINH HNX (09:15 - 11:30 và 13:00 - 14:25 từ T2-T6)
            // LƯU Ý: Khóa mở lệnh trước 09:15 để đợi chỉ số VN30 cơ sở bước vào khớp lệnh liên tục thực sự!
            java.time.DayOfWeek dow = java.time.LocalDate.now().getDayOfWeek();
            boolean isWeekend = (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY);
            boolean isFuturesContinuousOpen = !isWeekend &&
                ((nowTime.isAfter(LocalTime.of(9, 15)) && nowTime.isBefore(LocalTime.of(11, 30))) ||
                 (nowTime.isAfter(LocalTime.of(13, 0)) && nowTime.isBefore(LocalTime.of(14, 25))));

            if (!isFuturesContinuousOpen) {
                return; // Khóa mở vị thế trong giờ nhiễu ATO (08:45-09:15) hoặc ngoài phiên!
            }

            // 4. KIỂM TRA ĐIỀU KIỆN AN TOÀN: NẾU AUTOTRADING = FALSE THÌ TUYỆT ĐỐI KHÔNG MỞ LỆNH MỚI
            if (!autoTrading) {
                return;
            }

            // 5. Kiểm tra Cầu chì ngày (-1.5M), Giới hạn 3 lệnh/ngày & Cooldown 15 phút
            if (!canOpenNewFuturesTrade()) {
                return;
            }

            // 6. Nếu chưa có vị thế mở -> Kiểm tra mở lệnh mới
            if (openPositions.isEmpty()) {
                FuturesSignalDto signal = strategyEngine.generateSignal();
                if (signal != null && signal.getConfidenceScore() >= 80) {
                    if ("LONG".equalsIgnoreCase(signal.getAction())) {
                        openPosition("LONG", maxContracts, signal.getEntryPrice(), signal.getStopLossPrice(), signal.getTakeProfit1(), signal.getRecommendationReason());
                    } else if ("SHORT".equalsIgnoreCase(signal.getAction())) {
                        openPosition("SHORT", maxContracts, signal.getEntryPrice(), signal.getStopLossPrice(), signal.getTakeProfit1(), signal.getRecommendationReason());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi vòng lặp Futures Bot: {}", e.getMessage());
        }
    }

    private void updatePositionPnL(FuturesPositionDto pos, BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) return;
        pos.setCurrentPrice(currentPrice);
        boolean isLong = "LONG".equalsIgnoreCase(pos.getSide());

        BigDecimal pnlPts = isLong
            ? currentPrice.subtract(pos.getEntryPrice())
            : pos.getEntryPrice().subtract(currentPrice);

        pos.setPnlPoints(VN30FuturesStrategyEngine.roundToFuturesTick(pnlPts));

        BigDecimal gross = pnlPts.multiply(POINT_MULTIPLIER).multiply(BigDecimal.valueOf(pos.getContracts()));
        pos.setGrossPnlVnd(gross.setScale(0, RoundingMode.HALF_UP));

        BigDecimal net = gross.subtract(pos.getFeesVnd());
        pos.setNetPnlVnd(net.setScale(0, RoundingMode.HALF_UP));
    }

    private void addLog(String message) {
        String timedMsg = String.format("[%s] %s", LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")), message);
        botLogs.add(0, timedMsg);
        if (botLogs.size() > 50) botLogs.remove(botLogs.size() - 1);
        log.info("FuturesBot: {}", message);
    }

    /**
     * Kích hoạt quét và khớp lệnh Phái sinh T+0 chủ động
     */
    public synchronized FuturesPositionDto executeTacticalSignalScan() {
        try {
            FuturesQuoteDto quote = strategyEngine.getCurrentQuote();
            for (FuturesPositionDto pos : openPositions.values()) {
                updatePositionPnL(pos, quote.getCurrentPrice());
            }
            LocalTime nowTime = LocalTime.now();
            java.time.DayOfWeek dow = java.time.LocalDate.now().getDayOfWeek();
            boolean isWeekend = (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY);
            boolean isFuturesContinuousOpen = !isWeekend &&
                ((nowTime.isAfter(LocalTime.of(9, 15)) && nowTime.isBefore(LocalTime.of(11, 30))) ||
                 (nowTime.isAfter(LocalTime.of(13, 0)) && nowTime.isBefore(LocalTime.of(14, 25))));

            // NẾU TẮT AUTOTRADING HOẶC NGOÀI KHUNG GIỜ -> TỪ CHỐI VÀO LỆNH
            if (!autoTrading || !openPositions.isEmpty() || !isFuturesContinuousOpen || !canOpenNewFuturesTrade()) {
                return openPositions.values().stream().findFirst().orElse(null);
            }
            FuturesSignalDto signal = strategyEngine.generateSignal();
            if (signal != null && signal.getConfidenceScore() >= 80) {
                if ("LONG".equalsIgnoreCase(signal.getAction()) || "SHORT".equalsIgnoreCase(signal.getAction())) {
                    return openPosition(
                        signal.getAction(),
                        maxContracts,
                        signal.getEntryPrice(),
                        signal.getStopLossPrice(),
                        signal.getTakeProfit1(),
                        "[" + signal.getStrategyName() + "] " + signal.getRecommendationReason()
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi quét chiến thuật phái sinh T+0: {}", e.getMessage());
        }
        return null;
    }

    private boolean canOpenNewFuturesTrade() {
        if (todayRealizedPnlVnd.compareTo(DAILY_FUTURES_CIRCUIT_BREAKER) <= 0) {
            return false;
        }
        if (todayTradesCount >= MAX_DAILY_FUTURES_TRADES) {
            return false;
        }
        if (lastCloseTime != null && java.time.Duration.between(lastCloseTime, LocalDateTime.now()).toMinutes() < COOLDOWN_MINUTES_AFTER_CLOSE) {
            return false;
        }
        return true;
    }

    public void resetAll() {
        lastCloseTime = null;
        currentTradingDate = java.time.LocalDate.now();
        openPositions.clear();
        positionHistory.clear();
        todayRealizedPnlVnd = BigDecimal.ZERO;
        todayPnlPoints = BigDecimal.ZERO;
        todayTradesCount = 0;
        botLogs.clear();
        try {
            futuresPositionRepository.deleteAll();
        } catch (Exception ignored) {}
        addLog("🔄 [RESET PHÁI SINH HOÀN TẤT] Vốn phái sinh 100,000,000 đ, 0 vị thế mở. Đã xóa dữ liệu DB phái sinh cũ.");
    }

    private void savePositionToDb(FuturesPositionDto dto) {
        if (dto == null || dto.getId() == null) return;
        try {
            FuturesPosition entity = FuturesPosition.builder()
                .id(dto.getId())
                .symbol(dto.getSymbol())
                .side(dto.getSide())
                .contracts(dto.getContracts())
                .entryPrice(dto.getEntryPrice())
                .currentPrice(dto.getCurrentPrice())
                .closePrice(dto.getClosePrice())
                .stopLossPrice(dto.getStopLossPrice())
                .takeProfitPrice(dto.getTakeProfitPrice())
                .trailingStopPrice(dto.getTrailingStopPrice())
                .pnlPoints(dto.getPnlPoints())
                .grossPnlVnd(dto.getGrossPnlVnd())
                .feesVnd(dto.getFeesVnd())
                .netPnlVnd(dto.getNetPnlVnd())
                .marginUsed(dto.getMarginUsed())
                .status(dto.getStatus())
                .closeReason(dto.getCloseReason())
                .openTime(dto.getOpenTime())
                .closeTime(dto.getCloseTime())
                .build();
            futuresPositionRepository.save(entity);
        } catch (Exception e) {
            log.warn("Lỗi lưu FuturesPosition vào DB: {}", e.getMessage());
        }
    }

    private FuturesPositionDto toDto(FuturesPosition entity) {
        return FuturesPositionDto.builder()
            .id(entity.getId())
            .symbol(entity.getSymbol())
            .side(entity.getSide())
            .contracts(entity.getContracts())
            .entryPrice(entity.getEntryPrice())
            .currentPrice(entity.getCurrentPrice())
            .closePrice(entity.getClosePrice())
            .stopLossPrice(entity.getStopLossPrice())
            .takeProfitPrice(entity.getTakeProfitPrice())
            .trailingStopPrice(entity.getTrailingStopPrice())
            .pnlPoints(entity.getPnlPoints())
            .grossPnlVnd(entity.getGrossPnlVnd())
            .feesVnd(entity.getFeesVnd())
            .netPnlVnd(entity.getNetPnlVnd())
            .marginUsed(entity.getMarginUsed())
            .status(entity.getStatus())
            .closeReason(entity.getCloseReason())
            .openTime(entity.getOpenTime())
            .closeTime(entity.getCloseTime())
            .build();
    }
}
