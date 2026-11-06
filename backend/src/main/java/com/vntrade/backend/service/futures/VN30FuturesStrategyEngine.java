package com.vntrade.backend.service.futures;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.FuturesQuoteDto;
import com.vntrade.backend.dto.FuturesSignalDto;
import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.service.marketdata.StockPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VN30FuturesStrategyEngine {

    private final VN30FuturesCandleService candleService;
    private final StockPriceService stockPriceService;

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
     * Tạo báo giá phái sinh thời gian thực & Tính toán độ lệch Basis trung thực 100%
     */
    public FuturesQuoteDto getCurrentQuote() {
        List<Candle> fCandles = candleService.getFutures5mCandles(2);
        List<Candle> dailyCandles = candleService.getFuturesDailyCandles(5);
        List<Candle> idxCandles = candleService.getVN30Index5mCandles(2);

        BigDecimal fPrice = null;
        BigDecimal fPrev = null;
        Long volume = null;

        if (!dailyCandles.isEmpty()) {
            Candle latestDaily = dailyCandles.get(dailyCandles.size() - 1);
            fPrice = latestDaily.getClose();
            volume = latestDaily.getVolume();
            if (dailyCandles.size() >= 2) {
                fPrev = dailyCandles.get(dailyCandles.size() - 2).getClose();
            }
        }

        if (!fCandles.isEmpty()) {
            Candle latest5m = fCandles.get(fCandles.size() - 1);
            fPrice = latest5m.getClose();
        }

        // Lấy giá cơ sở VN30 thực tế từ sàn
        BigDecimal idxPrice = null;
        try {
            StockQuote vn30Quote = stockPriceService.getQuote("VN30");
            if (vn30Quote != null && vn30Quote.getPrice() != null && vn30Quote.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                idxPrice = vn30Quote.getPrice();
            }
        } catch (Exception ignored) {}

        if (idxPrice == null && !idxCandles.isEmpty()) {
            idxPrice = idxCandles.get(idxCandles.size() - 1).getClose();
        }

        // Nếu hoàn toàn không có dữ liệu nến thật từ sàn, báo mất kết nối thay vì bịa giá 1818
        if (fPrice == null || fPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return FuturesQuoteDto.builder()
                .symbol("VN30F1M")
                .currentPrice(BigDecimal.ZERO)
                .change(BigDecimal.ZERO)
                .pctChange(BigDecimal.ZERO)
                .vn30IndexPrice(idxPrice != null ? idxPrice : BigDecimal.ZERO)
                .basis(BigDecimal.ZERO)
                .basisStatus("MẤT_KẾT_NỐI_FEED")
                .volume(0L)
                .openInterest(null)
                .sessionTime(determineSessionTime())
                .timestamp(LocalDateTime.now())
                .build();
        }

        BigDecimal change = fPrev != null ? roundToFuturesTick(fPrice.subtract(fPrev)) : BigDecimal.ZERO;
        BigDecimal pctChange = (fPrev != null && fPrev.compareTo(BigDecimal.ZERO) > 0)
            ? change.divide(fPrev, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal basis = idxPrice != null ? roundToFuturesTick(fPrice.subtract(idxPrice)) : BigDecimal.ZERO;

        String basisStatus = "CÂN_BẰNG";
        if (idxPrice == null) {
            basisStatus = "CHỜ_DỮ_LIỆU_VN30";
        } else if (basis.compareTo(BigDecimal.valueOf(6.0)) > 0) {
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
            .vn30IndexPrice(idxPrice != null ? idxPrice : BigDecimal.ZERO)
            .basis(basis)
            .basisStatus(basisStatus)
            .volume(volume != null ? volume : 0L)
            .openInterest(null)
            .sessionTime(determineSessionTime())
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Thuật toán phân tích định lượng & Tạo tín hiệu LONG / SHORT
     */
    public FuturesSignalDto generateSignal() {
        FuturesQuoteDto quote = getCurrentQuote();

        if (quote.getCurrentPrice() == null || quote.getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return buildStandbySignal(quote, "Mất nguồn giá phái sinh thời gian thực. Tạm dừng phát tín hiệu!");
        }

        List<Candle> candles = candleService.getFutures5mCandles(3);

        if (candles.size() < 25) {
            return buildStandbySignal(quote, "Chưa đủ dữ liệu nến 5m (yêu cầu tối thiểu 25 nến).");
        }

        Candle latest = candles.get(candles.size() - 1);
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();

        // 1. KIỂM TRA ĐỘ MỚI CỦA DỮ LIỆU (DATA FRESHNESS GUARD):
        // Trong phiên giao dịch (sau 09:10), nến cuối cùng bắt buộc phải thuộc ngày hôm nay!
        // Nếu API sàn chưa trả về nến ngày hôm nay, TUYỆT ĐỐI KHÔNG DÙNG NẾN HÔM QUA ĐỂ VÀO LỆNH
        if (nowTime.isAfter(LocalTime.of(9, 10)) && nowTime.isBefore(LocalTime.of(15, 0)) && !today.equals(latest.getDate())) {
            return buildStandbySignal(quote, String.format("DỮ LIỆU CŨ: Nến phái sinh chưa cập nhật ngày hôm nay %s (Nến cuối từ %s). Chặn mở lệnh!",
                today, latest.getDate()));
        }

        // 2. KHÓA TÍN HIỆU PHỤ THUỘC BASIS TRƯỚC 09:15:
        // Trước 09:15, VN30 cơ sở chưa mở phiên khớp lệnh liên tục nên Basis hoàn toàn là nhiễu ATO.
        boolean isContinuousIndexOpen = nowTime.isAfter(LocalTime.of(9, 15)) && nowTime.isBefore(LocalTime.of(14, 30));

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
            if (basis.compareTo(BigDecimal.valueOf(8.0)) < 0) {
                BigDecimal entry = roundToFuturesTick(currentPrice);
                BigDecimal stopLoss = roundToFuturesTick(currentPrice - 2.5);
                BigDecimal tp1 = roundToFuturesTick(currentPrice + 4.0);
                BigDecimal tp2 = roundToFuturesTick(currentPrice + 8.5);
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
            if (basis.compareTo(BigDecimal.valueOf(-8.0)) > 0) {
                BigDecimal entry = roundToFuturesTick(currentPrice);
                BigDecimal stopLoss = roundToFuturesTick(currentPrice + 2.5);
                BigDecimal tp1 = roundToFuturesTick(currentPrice - 4.0);
                BigDecimal tp2 = roundToFuturesTick(currentPrice - 8.5);
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
                    .recommendationReason(String.format("EMA(9) < EMA(21), Giá (%.1f) dưới VWAP (%.1f). RSI=%.1f động lượng suy yếu, MACD Hist=%.2f, Basis=%.1f điểm.",
                        currentPrice, vwap, rsi, macdHist, basis.doubleValue()))
                    .rsi(rsi)
                    .macdHist(macdHist)
                    .vwap(roundToFuturesTick(vwap))
                    .basis(basis)
                    .generatedAt(LocalDateTime.now())
                    .build();
            }
        }

        // CHIẾN THUẬT 2: BASIS MEAN REVERSION SHORT (CHỈ KÍCH HOẠT KHI ĐÃ QUA 09:15 VÀ VN30 ĐANG KHỚP LIÊN TỤC)
        if (isContinuousIndexOpen && basis.compareTo(BigDecimal.valueOf(7.5)) > 0 && rsi >= 75) {
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
                .recommendationReason(String.format("Sau 09:15, Basis dương cao hưng phấn (%.1f điểm) kết hợp RSI quá mua (%.1f). Cơ hội Short ép giá hồi quy.",
                    basis.doubleValue(), rsi))
                .rsi(rsi)
                .macdHist(macdHist)
                .vwap(roundToFuturesTick(vwap))
                .basis(basis)
                .generatedAt(LocalDateTime.now())
                .build();
        }

        // CHIẾN THUẬT 3: SPRING V-REVERSAL LONG
        double recentLow = currentPrice;
        int lookbackBars = Math.min(18, n);
        for (int i = n - lookbackBars; i < n; i++) {
            if (lows[i] < recentLow) recentLow = lows[i];
        }
        double bounceFromLow = currentPrice - recentLow;
        double prevMacdHist = calculateMacdHistSlice(closes, n - 2);
        boolean macdTurningUp = macdHist > prevMacdHist;

        if (isContinuousIndexOpen && bounceFromLow >= 3.5 && currentPrice >= ema9 && macdTurningUp && rsi >= 38 && rsi <= 65 && basis.compareTo(BigDecimal.valueOf(-2.0)) >= 0) {
            BigDecimal entry = roundToFuturesTick(currentPrice);
            BigDecimal stopLoss = roundToFuturesTick(Math.max(recentLow - 0.5, currentPrice - 2.5));
            BigDecimal tp1 = roundToFuturesTick(currentPrice + 4.5);
            BigDecimal tp2 = roundToFuturesTick(currentPrice + 9.0);
            int score = 82 + (basis.compareTo(BigDecimal.ZERO) > 0 ? 6 : 0) + (currentPrice > vwap ? 5 : 0);

            return FuturesSignalDto.builder()
                .symbol("VN30F1M")
                .action("LONG")
                .currentPrice(entry)
                .entryPrice(entry)
                .stopLossPrice(stopLoss)
                .takeProfit1(tp1)
                .takeProfit2(tp2)
                .trailingStopPoints(BigDecimal.valueOf(1.8))
                .confidenceScore(Math.min(score, 96))
                .strategyName("SPRING_V_REVERSAL_LONG")
                .trendStatus("V_BOUNCE_RECOVERY")
                .recommendationReason(String.format("Sau 09:15, rút chân chữ V mạnh +%.1f điểm từ đáy (%.1f), giá vượt lên EMA(9) (%.1f), MACD Hist đảo chiều tăng (%.2f -> %.2f), Basis=%+.1f điểm.",
                    bounceFromLow, recentLow, ema9, prevMacdHist, macdHist, basis.doubleValue()))
                .rsi(rsi)
                .macdHist(macdHist)
                .vwap(roundToFuturesTick(vwap))
                .basis(basis)
                .generatedAt(LocalDateTime.now())
                .build();
        }

        // CHIẾN THUẬT 4: VWAP BULL-TRAP REJECTION SHORT
        double recentHigh = currentPrice;
        for (int i = n - lookbackBars; i < n; i++) {
            if (highs[i] > recentHigh) recentHigh = highs[i];
        }
        double dropFromHigh = recentHigh - currentPrice;
        if (dropFromHigh >= 3.0 && currentPrice < ema9 && currentPrice < vwap && !macdTurningUp && rsi <= 52 && rsi >= 32) {
            BigDecimal entry = roundToFuturesTick(currentPrice);
            BigDecimal stopLoss = roundToFuturesTick(currentPrice + 2.5);
            BigDecimal tp1 = roundToFuturesTick(currentPrice - 4.5);
            BigDecimal tp2 = roundToFuturesTick(currentPrice - 9.0);

            return FuturesSignalDto.builder()
                .symbol("VN30F1M")
                .action("SHORT")
                .currentPrice(entry)
                .entryPrice(entry)
                .stopLossPrice(stopLoss)
                .takeProfit1(tp1)
                .takeProfit2(tp2)
                .trailingStopPoints(BigDecimal.valueOf(1.8))
                .confidenceScore(84)
                .strategyName("VWAP_REJECTION_SHORT")
                .trendStatus("BEARISH_REJECTION")
                .recommendationReason(String.format("Nhịp hồi suy yếu bị từ chối dưới cản VWAP (%.1f) & EMA(9) (%.1f), MACD Hist suy giảm (%.2f). Kích hoạt Short T+0 thuận xu hướng.",
                    vwap, ema9, macdHist))
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
        return calculateMacdHistSlice(closes, closes.length - 1);
    }

    private double calculateMacdHistSlice(double[] closes, int endIdx) {
        if (endIdx < 26 || closes.length < 26) return 0.0;
        int startIdx = Math.max(0, endIdx - 35);
        int len = endIdx - startIdx + 1;
        double[] macdSeries = new double[len];
        for (int i = 0; i < len; i++) {
            int idx = startIdx + i;
            double e12 = calculateEmaSlice(closes, 12, idx);
            double e26 = calculateEmaSlice(closes, 26, idx);
            macdSeries[i] = e12 - e26;
        }
        double currentMacd = macdSeries[len - 1];
        double signalLine = calculateEmaSlice(macdSeries, 9, len - 1);
        return Math.round((currentMacd - signalLine) * 100.0) / 100.0;
    }
}
