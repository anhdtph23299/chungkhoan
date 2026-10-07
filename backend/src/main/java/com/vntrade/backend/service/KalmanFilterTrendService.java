package com.vntrade.backend.service;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.KalmanFilterTrendDto;
import com.vntrade.backend.dto.KalmanFilterTrendDto.KalmanDataPoint;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class KalmanFilterTrendService {

    private final CandleDataService candleDataService;
    private final StockPriceService stockPriceService;

    /**
     * Phân tích bộ lọc trạng thái Kalman (State-Space 2-D: Giá & Vận tốc) cho một mã cổ phiếu
     */
    public KalmanFilterTrendDto analyzeKalmanTrend(String symbol) {
        String sym = symbol != null ? symbol.toUpperCase().trim() : "FPT";

        // Lấy 60 phiên giao dịch gần nhất
        List<Candle> candles = candleDataService.getHistoricalCandles(sym, 60);
        if (candles == null || candles.size() < 25) {
            throw new IllegalArgumentException("Không đủ dữ liệu nến lịch sử cho bộ lọc Kalman (tối thiểu 25 phiên)");
        }

        // Lấy giá thị trường hiện tại
        StockQuote quote = stockPriceService.getQuote(sym);
        BigDecimal currentMarketPrice = quote != null ? quote.getPrice() : candles.get(candles.size() - 1).getClose();

        // 1. Khởi tạo ma trận và tham số ban đầu của bộ lọc Kalman 2 trạng thái [p, v]^T
        int n = candles.size();
        double[] prices = new double[n];
        for (int i = 0; i < n; i++) {
            prices[i] = candles.get(i).getClose().doubleValue();
        }

        // Ước lượng nhiễu đo lường R (Measurement Noise) dựa trên trung bình biên độ nến (High - Low)
        double sumRange = 0.0;
        for (Candle c : candles) {
            double range = c.getHigh().doubleValue() - c.getLow().doubleValue();
            sumRange += range;
        }
        double avgRange = sumRange / n;
        double rNoise = Math.max(100.0, Math.pow(avgRange * 0.45, 2)); // Phương sai R

        // Ước lượng nhiễu quá trình Q (Process Noise)
        double qAccelVar = Math.pow(avgRange * 0.15, 2);
        double q00 = 0.25 * qAccelVar;
        double q01 = 0.50 * qAccelVar;
        double q10 = 0.50 * qAccelVar;
        double q11 = 1.00 * qAccelVar;

        // Trạng thái ban đầu x = [p0, 0]^T
        double pEst = prices[0];
        double vEst = 0.0;

        // Ma trận hiệp phương sai sai số ước lượng P
        double p00 = rNoise;
        double p01 = 0.0;
        double p10 = 0.0;
        double p11 = 100.0;

        // Tính SMA20 để so sánh độ trễ
        double[] sma20 = calculateSMA20(prices);

        List<KalmanDataPoint> history = new ArrayList<>();
        double lastKalmanGain = 0.5;

        // Chạy vòng lặp đệ quy Kalman Filter qua toàn bộ chuỗi nến
        for (int t = 0; t < n; t++) {
            double z = prices[t]; // Đo lường giá quan sát được

            // Bước 1: Dự báo (Predict)
            // x_pred = F * x
            double pPred = pEst + vEst;
            double vPred = vEst;

            // P_pred = F * P * F^T + Q
            double pPred00 = p00 + p10 + p01 + p11 + q00;
            double pPred01 = p01 + p11 + q01;
            double pPred10 = p10 + p11 + q10;
            double pPred11 = p11 + q11;

            // Bước 2: Cập nhật (Update)
            // Innovation y = z - H * x_pred
            double y = z - pPred;

            // Innovation covariance S = H * P_pred * H^T + R = P_pred00 + R
            double sCov = pPred00 + rNoise;

            // Kalman Gain K = P_pred * H^T / S
            double k0 = pPred00 / sCov;
            double k1 = pPred10 / sCov;
            lastKalmanGain = k0;

            // Cập nhật trạng thái x = x_pred + K * y
            pEst = pPred + k0 * y;
            vEst = vPred + k1 * y;

            // Cập nhật ma trận hiệp phương sai P = (I - K * H) * P_pred
            p00 = (1.0 - k0) * pPred00;
            p01 = (1.0 - k0) * pPred01;
            p10 = pPred10 - k1 * pPred00;
            p11 = pPred11 - k1 * pPred01;

            // Lưu điểm dữ liệu
            LocalDate date = candles.get(t).getDate();
            history.add(KalmanDataPoint.builder()
                    .date(date)
                    .actualClose(BigDecimal.valueOf(z).setScale(0, RoundingMode.HALF_UP))
                    .kalmanEstimate(BigDecimal.valueOf(pEst).setScale(1, RoundingMode.HALF_UP))
                    .velocity(BigDecimal.valueOf(vEst).setScale(2, RoundingMode.HALF_UP))
                    .sma20Reference(BigDecimal.valueOf(sma20[t]).setScale(1, RoundingMode.HALF_UP))
                    .build());
        }

        // Lấy 25 điểm gần nhất cho trực quan hóa
        List<KalmanDataPoint> recentHistory = history.subList(Math.max(0, history.size() - 25), history.size());

        // Đánh giá trạng thái và vận tốc tức thời
        double currentClose = prices[n - 1];
        double velocityPercent = (vEst / Math.max(100.0, pEst)) * 100.0;
        double sma20Last = sma20[n - 1];
        double sma20Diff = pEst - sma20Last;

        // Nhận diện Regime xu hướng
        String regime;
        String signal;
        if (velocityPercent >= 1.0) {
            regime = "STRONG_ACCELERATION";
            signal = "ACCUMULATE_BEFORE_BREAKOUT";
        } else if (velocityPercent >= 0.25) {
            regime = "MODERATE_UPTREND";
            signal = "HOLD_MOMENTUM";
        } else if (velocityPercent > -0.25 && velocityPercent < 0.25) {
            regime = "CONSOLIDATION";
            signal = "WAIT_STABILIZATION";
        } else if (velocityPercent > -1.0) {
            regime = "EXHAUSTION_WARNING";
            signal = "TAKE_PROFIT_EXHAUSTION";
        } else {
            regime = "BEARISH_DOWNWARD";
            signal = "EXIT_DOWNWARD_DRIFT";
        }

        // Nhận định thực thi theo chu kỳ T+2.5
        String verdict;
        if ("STRONG_ACCELERATION".equals(regime)) {
            verdict = String.format("Bộ lọc Kalman phát hiện gia tốc tăng cực mạnh (+%.2f%%/phiên). Giá lọc khử trễ (%.0f đ) đang dẫn trước đường SMA20 truyền thống %+d đ. Vào lệnh sớm tại đây giúp cổ phiếu kịp về tài khoản (T+2.5) trước khi đám đông bùng nổ.",
                    velocityPercent, pEst, (long) sma20Diff);
        } else if ("MODERATE_UPTREND".equals(regime)) {
            verdict = String.format("Xu hướng tăng ổn định với vận tốc +%.1f đ/phiên (+%.2f%%). Khuyến nghị duy trì vị thế, đặt trailing stop theo giá lọc Kalman ở mức %.0f đ.",
                    vEst, velocityPercent, pEst - (avgRange * 0.8));
        } else if ("EXHAUSTION_WARNING".equals(regime)) {
            verdict = String.format("Cảnh báo suy kiệt động lượng: Vận tốc bắt đầu trôi âm (%.2f%%/phiên) dù giá có thể chưa gãy MA20. Chủ động chốt lời bảo vệ thành quả trước chu kỳ thanh toán T+2.5.",
                    velocityPercent);
        } else if ("BEARISH_DOWNWARD".equals(regime)) {
            verdict = String.format("Gia tốc giảm mạnh (-%.2f%%/phiên). Bộ lọc Kalman khuyến nghị đóng vị thế hoặc đứng ngoài, không bắt dao rơi cho đến khi vận tốc hồi phục về mức dương.",
                    Math.abs(velocityPercent));
        } else {
            verdict = String.format("Cổ phiếu đang tích lũy biên độ hẹp (Vận tốc biến thiên %.2f%%/phiên). Chờ đợi tín hiệu bứt phá rõ nét.",
                    velocityPercent);
        }

        return KalmanFilterTrendDto.builder()
                .symbol(sym)
                .marketPrice(currentMarketPrice)
                .kalmanFilteredPrice(BigDecimal.valueOf(pEst).setScale(1, RoundingMode.HALF_UP))
                .priceVelocity(BigDecimal.valueOf(vEst).setScale(2, RoundingMode.HALF_UP))
                .velocityPercent(BigDecimal.valueOf(velocityPercent).setScale(2, RoundingMode.HALF_UP))
                .kalmanGain(BigDecimal.valueOf(lastKalmanGain).setScale(4, RoundingMode.HALF_UP))
                .measurementNoiseVariance(BigDecimal.valueOf(rNoise).setScale(2, RoundingMode.HALF_UP))
                .processNoiseVariance(BigDecimal.valueOf(q00).setScale(2, RoundingMode.HALF_UP))
                .trendRegime(regime)
                .zeroLagSignal(signal)
                .sma20LagDifference(BigDecimal.valueOf(sma20Diff).setScale(1, RoundingMode.HALF_UP))
                .t25ExecutionVerdict(verdict)
                .historicalPoints(recentHistory)
                .build();
    }

    private double[] calculateSMA20(double[] prices) {
        int n = prices.length;
        double[] sma = new double[n];
        for (int i = 0; i < n; i++) {
            if (i < 19) {
                // Với các phiên đầu chưa đủ 20 phiên, tính trung bình từ 0 đến i
                double sum = 0.0;
                for (int j = 0; j <= i; j++) sum += prices[j];
                sma[i] = sum / (i + 1);
            } else {
                double sum = 0.0;
                for (int j = i - 19; j <= i; j++) sum += prices[j];
                sma[i] = sum / 20.0;
            }
        }
        return sma;
    }
}
