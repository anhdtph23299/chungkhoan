package com.vntrade.backend.service;

import com.vntrade.backend.dto.*;
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

    // Hằng số tài chính phái sinh HNX
    public static final BigDecimal POINT_MULTIPLIER = BigDecimal.valueOf(100_000); // 100.000 đ/điểm
    public static final BigDecimal MARGIN_RATE = BigDecimal.valueOf(0.175);        // 17.5% ký quỹ VSDC
    public static final BigDecimal ROUNDTRIP_FEE_PER_CONTRACT = BigDecimal.valueOf(9_400); // Phí sở + môi giới 2 chiều

    private final AtomicLong positionIdGen = new AtomicLong(1000);
    private final Map<String, FuturesPositionDto> openPositions = new ConcurrentHashMap<>();
    private final List<FuturesPositionDto> positionHistory = new CopyOnWriteArrayList<>();

    // Cấu hình Bot phái sinh
    private boolean autoTrading = true;
    private String mode = "LIVE_PAPER";
    private BigDecimal capital = BigDecimal.valueOf(100_000_000); // Vốn 100M VND
    private int maxContracts = 2;                                // Mặc định 2 HĐ (an toàn trong 100M)
    private BigDecimal stopLossPoints = BigDecimal.valueOf(2.5);  // Cắt lỗ 2.5 điểm
    private BigDecimal takeProfitPoints = BigDecimal.valueOf(5.0);// Chốt lời 5.0 điểm
    private BigDecimal trailingStopTrigger = BigDecimal.valueOf(3.0); // Kích hoạt khi lãi >= 3.0 điểm
    private BigDecimal trailingStopDistance = BigDecimal.valueOf(1.5); // Bám sau 1.5 điểm
    private boolean closeBeforeAtc = true;

    // Nhật ký hoạt động trong ngày
    private BigDecimal todayRealizedPnlVnd = BigDecimal.ZERO;
    private BigDecimal todayPnlPoints = BigDecimal.ZERO;
    private int todayTradesCount = 0;
    private final List<String> botLogs = new CopyOnWriteArrayList<>();

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
        addLog("Robot Phái Sinh VN30F: " + (enabled ? "KÍCH HOẠT TỰ ĐỘNG SĂN LỆNH" : "TẠM DỪNG VÀO LỆNH MỚI"));
    }

    public List<FuturesPositionDto> getOpenPositions() {
        // Cập nhật giá thị trường và PnL trước khi trả về
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
        positionHistory.add(0, pos);

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

                // Kiểm tra Kích hoạt & Dời Trailing Stop
                if (diff.compareTo(trailingStopTrigger) >= 0) {
                    if (isLong) {
                        BigDecimal newTrail = currentPrice.subtract(trailingStopDistance);
                        if (pos.getTrailingStopPrice() == null || newTrail.compareTo(pos.getTrailingStopPrice()) > 0) {
                            pos.setTrailingStopPrice(VN30FuturesStrategyEngine.roundToFuturesTick(newTrail));
                            log.debug("Trailing Stop LONG dời lên: {}", pos.getTrailingStopPrice());
                        }
                    } else {
                        BigDecimal newTrail = currentPrice.add(trailingStopDistance);
                        if (pos.getTrailingStopPrice() == null || newTrail.compareTo(pos.getTrailingStopPrice()) < 0) {
                            pos.setTrailingStopPrice(VN30FuturesStrategyEngine.roundToFuturesTick(newTrail));
                            log.debug("Trailing Stop SHORT dời xuống: {}", pos.getTrailingStopPrice());
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

            // 3. Nếu Bot Auto đang bật và chưa có vị thế mở -> Kiểm tra mở lệnh mới
            if (autoTrading && openPositions.isEmpty()) {
                FuturesSignalDto signal = strategyEngine.generateSignal();
                if (signal != null && signal.getConfidenceScore() >= 75) {
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
}
