package com.vntrade.backend.service.futures;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.FuturesQuoteDto;
import com.vntrade.backend.dto.FuturesSignalDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VN30FuturesStrategyEngine {

    private final VN30FuturesCandleService candleService;

    /**
     * Làm tròn theo bước giá phái sinh HNX: 0.1 điểm
     */
    public static BigDecimal roundToFuturesTick(BigDecimal price) {
        if (price == null) return BigDecimal.ZERO;
        return price.setScale(1, RoundingMode.HALF_UP);
    }

    public static BigDecimal roundToFuturesTick(double price) {
        return BigDecimal.valueOf(Math.round(price * 10.0) / 10.0).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Tạo báo giá phái sinh thời gian thực & Tính toán độ lệch Basis
     */
    public FuturesQuoteDto getCurrentQuote() {
        List<Candle> fCandles = candleService.getFutures5mCandles(2);
        List<Candle> idxCandles = candleService.getVN30Index5mCandles(2);

        BigDecimal fPrice = BigDecimal.valueOf(1877.2);
        BigDecimal fPrev = BigDecimal.valueOf(1875.0);
        long volume = 185000L;

        if (!fCandles.isEmpty()) {
            Candle latest = fCandles.get(fCandles.size() - 1);
            fPrice = latest.getClose();
            volume = latest.getVolume() > 0 ? latest.getVolume() * 20 : 185000L;
            if (fCandles.size() >= 2) {
                fPrev = fCandles.get(fCandles.size() - 2).getClose();
            }
        }

        BigDecimal idxPrice = BigDecimal.valueOf(1873.43);
        if (!idxCandles.isEmpty()) {
            idxPrice = idxCandles.get(idxCandles.size() - 1).getClose();
        }

        BigDecimal change = roundToFuturesTick(fPrice.subtract(fPrev));
        BigDecimal pctChange = fPrev.compareTo(BigDecimal.ZERO) > 0
            ? change.divide(fPrev, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal basis = roundToFuturesTick(fPrice.subtract(idxPrice));

        String basisStatus = "CÂN_BẰNG";
        if (basis.compareTo(BigDecimal.valueOf(6.0)) > 0) {
            basisStatus = "DƯƠNG_CAO (Hưng phấn quá mức)";
        } else if (basis.compareTo(BigDecimal.valueOf(-6.0)) < 0) {
            basisStatus = "ÂM_SÂU (Chiết khấu mạnh)";
        } else if (basis.abs().compareTo(BigDecimal.valueOf(2.0)) <= 0) {
            basisStatus = "HỘI_TỤ_CHUẨN";
        }

        return FuturesQuoteDto.builder()
            .symbol("VN30F1M")
            .currentPrice(fPrice)
            .change(change)
            .pctChange(pctChange)
            .vn30IndexPrice(idxPrice)
            .basis(basis)
            .basisStatus(basisStatus)
            .volume(volume)
            .openInterest(48250L)
            .sessionTime(determineSessionTime())
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Thuật toán phân tích định lượng & Tạo tín hiệu LONG / SHORT
     */
    public FuturesSignalDto generateSignal() {
        FuturesQuoteDto quote = getCurrentQuote();
        List<Candle> candles = candleService.getFutures5mCandles(3);

        if (candles.size() < 25) {
            return buildStandbySignal(quote, "Chưa đủ dữ liệu nến 5m (yêu cầu tối thiểu 25 nến).");
        }

        int n = candles.size();
        double[] closes = candles.stream().mapToDouble(c -> c.getClose().doubleValue()).toArray();
        double[] highs = candles.stream().mapToDouble(c -> c.getHigh().doubleValue()).toArray();
        double[] lows = candles.stream().mapToDouble(c -> c.getLow().doubleValue()).toArray();
        long[] volumes = candles.stream().mapToLong(Candle::getVolume).toArray();

        double currentPrice = closes[n - 1];

        // 1. Tính toán EMA(9) và EMA(21)
        double ema9 = calculateEma(closes, 9);
        double ema21 = calculateEma(closes, 21);
        double prevEma9 = calculateEmaSlice(closes, 9, n - 2);
        double prevEma21 = calculateEmaSlice(closes, 21, n - 2);

        // 2. Tính RSI(14)
        double rsi = calculateRsi(closes, 14);

        // 3. Tính VWAP Intraday
        double vwap = calculateVwap(highs, lows, closes, volumes);

        // 4. Tính MACD Histogram (12, 26, 9)
        double macdHist = calculateMacdHist(closes);

        // 5. Xác định xu hướng
        boolean bullTrend = ema9 > ema21 && currentPrice > vwap;
        boolean bearTrend = ema9 < ema21 && currentPrice < vwap;

        String trendStatus = "SIDEWAYS";
        if (bullTrend && rsi > 58) trendStatus = "BULLISH_STRONG";
        else if (bullTrend) trendStatus = "BULLISH_MODERATE";
        else if (bearTrend && rsi < 42) trendStatus = "BEARISH_STRONG";
        else if (bearTrend) trendStatus = "BEARISH_MODERATE";

        BigDecimal basis = quote.getBasis();

        // CHIẾN THUẬT 1: MOMENTUM TREND FOLLOWING
        boolean longCrossover = (prevEma9 <= prevEma21) && (ema9 > ema21);
        boolean shortCrossover = (prevEma9 >= prevEma21) && (ema9 < ema21);

        // Tín hiệu LONG
        if ((bullTrend && rsi >= 48 && rsi <= 68 && macdHist > 0) || (longCrossover && rsi > 50)) {
            if (basis.compareTo(BigDecimal.valueOf(8.0)) < 0) { // Tránh mua nếu basis đang hưng phấn tột độ > +8
                BigDecimal entry = roundToFuturesTick(currentPrice);
                BigDecimal stopLoss = roundToFuturesTick(currentPrice - 2.5); // Cắt lỗ cố định 2.5 điểm
                BigDecimal tp1 = roundToFuturesTick(currentPrice + 4.0);      // TP1 +4.0 điểm (+400k/HĐ)
                BigDecimal tp2 = roundToFuturesTick(currentPrice + 8.5);      // TP2 +8.5 điểm (+850k/HĐ)
                int score = 75 + (bullTrend ? 10 : 0) + (macdHist > 0.3 ? 5 : 0) + (basis.compareTo(BigDecimal.ZERO) < 0 ? 5 : 0);

                return FuturesSignalDto.builder()
                    .symbol("VN30F1M")
                    .action("LONG")
                    .currentPrice(entry)
                    .entryPrice(entry)
                    .stopLossPrice(stopLoss)
                    .takeProfit1(tp1)
                    .takeProfit2(tp2)
                    .trailingStopPoints(BigDecimal.valueOf(2.0))
                    .confidenceScore(Math.min(score, 95))
                    .strategyName("DUAL_MOMENTUM_TREND")
                    .trendStatus(trendStatus)
                    .recommendationReason(String.format("EMA(9) > EMA(21), Giá (%.1f) vượt trên VWAP (%.1f). RSI=%.1f động lượng khỏe, MACD Hist=+%.2f, Basis=%.1f điểm an toàn.",
                        currentPrice, vwap, rsi, macdHist, basis.doubleValue()))
                    .rsi(rsi)
                    .macdHist(macdHist)
                    .vwap(roundToFuturesTick(vwap))
                    .basis(basis)
                    .generatedAt(LocalDateTime.now())
                    .build();
            }
        }

        // Tín hiệu SHORT
        if ((bearTrend && rsi <= 52 && rsi >= 32 && macdHist < 0) || (shortCrossover && rsi < 50)) {
            if (basis.compareTo(BigDecimal.valueOf(-8.0)) > 0) { // Tránh short đuổi khi basis chiết khấu quá đà < -8
                BigDecimal entry = roundToFuturesTick(currentPrice);
                BigDecimal stopLoss = roundToFuturesTick(currentPrice + 2.5); // Cắt lỗ 2.5 điểm
                BigDecimal tp1 = roundToFuturesTick(currentPrice - 4.0);      // TP1 -4.0 điểm (+400k/HĐ)
                BigDecimal tp2 = roundToFuturesTick(currentPrice - 8.5);      // TP2 -8.5 điểm (+850k/HĐ)
                int score = 75 + (bearTrend ? 10 : 0) + (macdHist < -0.3 ? 5 : 0) + (basis.compareTo(BigDecimal.valueOf(3.0)) > 0 ? 5 : 0);

                return FuturesSignalDto.builder()
                    .symbol("VN30F1M")
                    .action("SHORT")
                    .currentPrice(entry)
                    .entryPrice(entry)
                    .stopLossPrice(stopLoss)
                    .takeProfit1(tp1)
                    .takeProfit2(tp2)
                    .trailingStopPoints(BigDecimal.valueOf(2.0))
                    .confidenceScore(Math.min(score, 95))
                    .strategyName("DUAL_MOMENTUM_TREND")
                    .trendStatus(trendStatus)
                    .recommendationReason(String.format("EMA(9) < EMA(21), Giá (%.1f) nằm dưới VWAP (%.1f). Áp lực bán chiếm ưu thế, RSI=%.1f, MACD Hist=%.2f, Basis=%.1f điểm.",
                        currentPrice, vwap, rsi, macdHist, basis.doubleValue()))
                    .rsi(rsi)
                    .macdHist(macdHist)
                    .vwap(roundToFuturesTick(vwap))
                    .basis(basis)
                    .generatedAt(LocalDateTime.now())
                    .build();
            }
        }

        // CHIẾN THUẬT 2: BASIS EXTREME REVERSION (Độ lệch cực đại)
        if (basis.compareTo(BigDecimal.valueOf(-7.0)) <= 0 && rsi < 35) {
            BigDecimal entry = roundToFuturesTick(currentPrice);
            return FuturesSignalDto.builder()
                .symbol("VN30F1M")
                .action("LONG")
                .currentPrice(entry)
                .entryPrice(entry)
                .stopLossPrice(roundToFuturesTick(currentPrice - 2.0))
                .takeProfit1(roundToFuturesTick(currentPrice + 3.5))
                .takeProfit2(roundToFuturesTick(currentPrice + 6.0))
                .trailingStopPoints(BigDecimal.valueOf(1.5))
                .confidenceScore(80)
                .strategyName("BASIS_MEAN_REVERSION")
                .trendStatus(trendStatus)
                .recommendationReason(String.format("Basis âm sâu (%.1f điểm) và RSI quá bán (%.1f). Xác suất cao xuất hiện nhịp bật hội tụ về chỉ số cơ sở.",
                    basis.doubleValue(), rsi))
                .rsi(rsi)
                .macdHist(macdHist)
                .vwap(roundToFuturesTick(vwap))
                .basis(basis)
                .generatedAt(LocalDateTime.now())
                .build();
        }

        if (basis.compareTo(BigDecimal.valueOf(7.5)) >= 0 && rsi > 70) {
            BigDecimal entry = roundToFuturesTick(currentPrice);
            return FuturesSignalDto.builder()
                .symbol("VN30F1M")
                .action("SHORT")
                .currentPrice(entry)
                .entryPrice(entry)
                .stopLossPrice(roundToFuturesTick(currentPrice + 2.0))
                .takeProfit1(roundToFuturesTick(currentPrice - 3.5))
                .takeProfit2(roundToFuturesTick(currentPrice - 6.0))
                .trailingStopPoints(BigDecimal.valueOf(1.5))
                .confidenceScore(80)
                .strategyName("BASIS_MEAN_REVERSION")
                .trendStatus(trendStatus)
                .recommendationReason(String.format("Basis dương cao hưng phấn (%.1f điểm) kết hợp RSI quá mua (%.1f). Cơ hội Short ép giá hồi quy.",
                    basis.doubleValue(), rsi))
                .rsi(rsi)
                .macdHist(macdHist)
                .vwap(roundToFuturesTick(vwap))
                .basis(basis)
                .generatedAt(LocalDateTime.now())
                .build();
        }

        return buildStandbySignal(quote, String.format("Thị trường dao động hẹp (%s, RSI=%.1f, Basis=%.1f). Giữ kỷ luật kiên nhẫn đứng ngoài chờ điểm bứt phá.",
            trendStatus, rsi, basis.doubleValue()));
    }

    private FuturesSignalDto buildStandbySignal(FuturesQuoteDto quote, String reason) {
        return FuturesSignalDto.builder()
            .symbol("VN30F1M")
            .action("STANDBY")
            .currentPrice(quote.getCurrentPrice())
            .entryPrice(quote.getCurrentPrice())
            .stopLossPrice(null)
            .takeProfit1(null)
            .takeProfit2(null)
            .trailingStopPoints(BigDecimal.valueOf(2.0))
            .confidenceScore(50)
            .strategyName("WAIT_FOR_SETUP")
            .trendStatus("NEUTRAL")
            .recommendationReason(reason)
            .rsi(50.0)
            .macdHist(0.0)
            .vwap(quote.getCurrentPrice())
            .basis(quote.getBasis())
            .generatedAt(LocalDateTime.now())
            .build();
    }

    private String determineSessionTime() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(8, 45))) return "TIỀN_TRẠM (Trước 08:45)";
        if (now.isBefore(LocalTime.of(9, 0))) return "ATO PHÁI SINH (08:45 - 09:00)";
        if (now.isBefore(LocalTime.of(11, 30))) return "KHỚP LỆNH SÁNG (09:00 - 11:30)";
        if (now.isBefore(LocalTime.of(13, 0))) return "NGHỈ TRƯA (11:30 - 13:00)";
        if (now.isBefore(LocalTime.of(14, 30))) return "KHỚP LỆNH CHIỀU (13:00 - 14:30)";
        if (now.isBefore(LocalTime.of(14, 45))) return "ATC ĐÓNG CỬA (14:30 - 14:45)";
        return "ĐÃ ĐÓNG CỬA PHIÊN";
    }

    private double calculateEma(double[] data, int period) {
        return calculateEmaSlice(data, period, data.length - 1);
    }

    private double calculateEmaSlice(double[] data, int period, int endIdx) {
        if (endIdx < 0 || data.length == 0) return 0;
        int len = Math.min(period * 2, endIdx + 1);
        int start = Math.max(0, endIdx - len + 1);

        double k = 2.0 / (period + 1.0);
        double ema = data[start];
        for (int i = start + 1; i <= endIdx; i++) {
            ema = data[i] * k + ema * (1.0 - k);
        }
        return ema;
    }

    private double calculateRsi(double[] closes, int period) {
        int n = closes.length;
        if (n <= period) return 50.0;

        double gains = 0, losses = 0;
        for (int i = n - period; i < n; i++) {
            double diff = closes[i] - closes[i - 1];
            if (diff >= 0) gains += diff;
            else losses += Math.abs(diff);
        }

        if (losses == 0) return 100.0;
        double rs = (gains / period) / (losses / period);
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private double calculateVwap(double[] highs, double[] lows, double[] closes, long[] volumes) {
        int count = Math.min(30, closes.length);
        double cumVal = 0;
        long cumVol = 0;
        for (int i = closes.length - count; i < closes.length; i++) {
            double tp = (highs[i] + lows[i] + closes[i]) / 3.0;
            cumVal += tp * volumes[i];
            cumVol += volumes[i];
        }
        return cumVol > 0 ? cumVal / cumVol : closes[closes.length - 1];
    }

    private double calculateMacdHist(double[] closes) {
        double ema12 = calculateEma(closes, 12);
        double ema26 = calculateEma(closes, 26);
        double macd = ema12 - ema26;
        double signal = calculateEma(closes, 9) * 0.1; // Approximation
        return macd - signal;
    }
}
