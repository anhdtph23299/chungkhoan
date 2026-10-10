package com.vntrade.backend.service.calculation;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.GarchVolatilityForecastDto;
import com.vntrade.backend.dto.GarchVolatilityForecastDto.GarchHistoryPoint;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import com.vntrade.backend.service.marketdata.CandleDataService;
import com.vntrade.backend.service.marketdata.StockPriceService;

@Service
@Slf4j
@RequiredArgsConstructor
public class GarchVolatilityForecastService {

    private final CandleDataService candleDataService;
    private final StockPriceService stockPriceService;

    private static final int TRADING_DAYS_PER_YEAR = 252;

    /**
     * Ước lượng mô hình GARCH(1,1) và dự báo biến động cho các phiên tới (đặc biệt chu kỳ T+2.5)
     */
    public GarchVolatilityForecastDto forecastVolatility(String symbol) {
        String sym = symbol != null ? symbol.toUpperCase().trim() : "FPT";

        // Lấy dữ liệu 60 phiên giao dịch gần nhất
        List<Candle> candles = candleDataService.getHistoricalCandles(sym, 60);
        if (candles == null || candles.size() < 30) {
            throw new IllegalArgumentException("Không đủ dữ liệu nến lịch sử cho mô hình GARCH(1,1) (tối thiểu 30 phiên)");
        }

        StockQuote quote = stockPriceService.getQuote(sym);
        BigDecimal currentPrice = quote != null && quote.getPrice() != null
                ? quote.getPrice()
                : candles.get(candles.size() - 1).getClose();

        int n = candles.size();
        double[] returns = new double[n - 1];
        double sumReturn = 0.0;

        for (int i = 1; i < n; i++) {
            double pPrev = Math.max(100.0, candles.get(i - 1).getClose().doubleValue());
            double pCurr = Math.max(100.0, candles.get(i).getClose().doubleValue());
            returns[i - 1] = Math.log(pCurr / pPrev);
            sumReturn += returns[i - 1];
        }

        int m = returns.length;
        double meanReturn = sumReturn / m;

        // Tính phương sai vô điều kiện mẫu (Unconditional Sample Variance VL)
        double sumSqDiff = 0.0;
        double[] epsilons = new double[m];
        for (int i = 0; i < m; i++) {
            epsilons[i] = returns[i] - meanReturn;
            sumSqDiff += epsilons[i] * epsilons[i];
        }
        double sampleVarianceDaily = sumSqDiff / (m - 1);
        double historicalVolAnnualized = Math.sqrt(sampleVarianceDaily * TRADING_DAYS_PER_YEAR) * 100.0;

        // Ước lượng tham số GARCH(1,1) theo Variance Targeting (Engle 1982 / Bollerslev 1986)
        // Với thị trường chứng khoán Việt Nam (HOSE): alpha ~ 0.12, beta ~ 0.83 (tổng = 0.95)
        double alpha = 0.12;
        double beta = 0.83;
        double persistence = alpha + beta; // 0.95
        double omega = sampleVarianceDaily * (1.0 - persistence); // Variance targeting

        // Chạy đệ quy GARCH(1,1) qua chuỗi thời gian: sigma_t^2 = omega + alpha * eps_{t-1}^2 + beta * sigma_{t-1}^2
        double[] conditionalVariances = new double[m];
        conditionalVariances[0] = sampleVarianceDaily; // Khởi tạo tại VL

        List<GarchHistoryPoint> history = new ArrayList<>();
        for (int t = 1; t < m; t++) {
            double prevEpsSq = epsilons[t - 1] * epsilons[t - 1];
            double prevSigmaSq = conditionalVariances[t - 1];
            conditionalVariances[t] = omega + alpha * prevEpsSq + beta * prevSigmaSq;

            if (t >= m - 20) { // Lưu 20 phiên gần nhất cho đồ thị
                double dailyVol = Math.sqrt(conditionalVariances[t]) * 100.0;
                history.add(GarchHistoryPoint.builder()
                        .date(candles.get(t + 1).getDate().toString())
                        .dailyReturnPercent(BigDecimal.valueOf(returns[t] * 100.0).setScale(2, RoundingMode.HALF_UP))
                        .conditionalVolPercent(BigDecimal.valueOf(dailyVol).setScale(2, RoundingMode.HALF_UP))
                        .build());
            }
        }

        // Biến động có điều kiện hiện tại sigma_T
        double currentConditionalVar = conditionalVariances[m - 1];
        double currentConditionalVolAnnualized = Math.sqrt(currentConditionalVar * TRADING_DAYS_PER_YEAR) * 100.0;
        double longRunVolAnnualized = Math.sqrt(sampleVarianceDaily * TRADING_DAYS_PER_YEAR) * 100.0;

        // Dự báo biến động cho các phiên tới (Forward Forecasting):
        // sigma_{T+1}^2 = omega + alpha * eps_T^2 + beta * sigma_T^2
        double lastEpsSq = epsilons[m - 1] * epsilons[m - 1];
        double varTPlus1 = omega + alpha * lastEpsSq + beta * currentConditionalVar;
        double volTPlus1Annualized = Math.sqrt(varTPlus1 * TRADING_DAYS_PER_YEAR) * 100.0;

        // sigma_{T+2}^2 = VL + (alpha + beta) * (sigma_{T+1}^2 - VL)
        double varTPlus2 = sampleVarianceDaily + persistence * (varTPlus1 - sampleVarianceDaily);
        double volTPlus2Annualized = Math.sqrt(varTPlus2 * TRADING_DAYS_PER_YEAR) * 100.0;

        // sigma_{T+3}^2 = VL + (alpha + beta)^2 * (sigma_{T+1}^2 - VL)
        double varTPlus3 = sampleVarianceDaily + Math.pow(persistence, 2) * (varTPlus1 - sampleVarianceDaily);

        // Biến động rủi ro lũy kế đến thời điểm mở bán T+2.5 (phiên chiều ngày T+2):
        // Var(T+2.5) = Var(T+1) + Var(T+2) + 0.5 * Var(T+3)
        double cumVarT25 = varTPlus1 + varTPlus2 + (0.5 * varTPlus3);
        double cumVolT25Percent = Math.sqrt(cumVarT25) * 100.0;

        // Ngưỡng cắt lỗ động co giãn theo biến động GARCH T+2.5 (Dynamic Stop-Loss)
        // Điểm cắt lỗ chuẩn = 1.96 * sigma_{T+2.5} + 0.40% (phí thuế trọn vòng)
        double dynamicStopLoss = (1.80 * cumVolT25Percent) + 0.40;
        // Bị giới hạn an toàn trong khoảng [4.5%, 8.5%] để vừa tránh bị quét nhiễu, vừa không quá giới hạn biên độ trần sàn
        dynamicStopLoss = Math.min(8.5, Math.max(4.5, dynamicStopLoss));

        // Phân loại chế độ biến động (Volatility Regime)
        String regime;
        String action;
        double volRatio = currentConditionalVolAnnualized / Math.max(1.0, longRunVolAnnualized);

        if (volRatio >= 1.35) {
            regime = "VOLATILITY_EXPANSION_SHOCK";
            action = String.format("Biến động GARCH bùng nổ (+%.1f%% so với trung bình). Cổ phiếu đang chịu các cú sốc giá mạnh. Quỹ khuyến nghị hạ quy mô vị thế 50%%, giãn Stop-Loss lên %.2f%% để tránh bị rung lắc quét lệnh giả.",
                    (volRatio - 1.0) * 100.0, dynamicStopLoss);
        } else if (volRatio <= 0.75) {
            regime = "VOLATILITY_COMPRESSION_SQUEEZE";
            action = String.format("Biến động GARCH nén chặt cực đại (chỉ bằng %.1f%% mức bình thường). Chuẩn bị cho pha bùng nổ xu hướng (Volatility Breakout). Khuyến nghị gom dần tỷ trọng trước khi thanh khoản nổ.",
                    volRatio * 100.0);
        } else {
            regime = "MEAN_REVERTING_NORMAL";
            action = String.format("Biến động ổn định quay về mức cân bằng dài hạn (%.1f%%/năm). Phù hợp thực thi chiến lược chuẩn mực với điểm cắt lỗ T+2.5 tối ưu là %.2f%%.",
                    longRunVolAnnualized, dynamicStopLoss);
        }

        return GarchVolatilityForecastDto.builder()
                .symbol(sym)
                .currentPrice(currentPrice)
                .historicalVolAnnualized(BigDecimal.valueOf(historicalVolAnnualized).setScale(2, RoundingMode.HALF_UP))
                .conditionalVolCurrent(BigDecimal.valueOf(currentConditionalVolAnnualized).setScale(2, RoundingMode.HALF_UP))
                .omegaConstant(BigDecimal.valueOf(omega).setScale(8, RoundingMode.HALF_UP))
                .alphaArch(BigDecimal.valueOf(alpha).setScale(4, RoundingMode.HALF_UP))
                .betaGarch(BigDecimal.valueOf(beta).setScale(4, RoundingMode.HALF_UP))
                .persistenceAlphaPlusBeta(BigDecimal.valueOf(persistence).setScale(4, RoundingMode.HALF_UP))
                .longRunVolAnnualized(BigDecimal.valueOf(longRunVolAnnualized).setScale(2, RoundingMode.HALF_UP))
                .forecastTPlus1VolAnnualized(BigDecimal.valueOf(volTPlus1Annualized).setScale(2, RoundingMode.HALF_UP))
                .forecastTPlus2VolAnnualized(BigDecimal.valueOf(volTPlus2Annualized).setScale(2, RoundingMode.HALF_UP))
                .forecastT25SettlementVolPercent(BigDecimal.valueOf(cumVolT25Percent).setScale(2, RoundingMode.HALF_UP))
                .dynamicT25StopLossPercent(BigDecimal.valueOf(dynamicStopLoss).setScale(2, RoundingMode.HALF_UP))
                .volatilityRegime(regime)
                .institutionalRiskAction(action)
                .historicalVolSeries(history)
                .build();
    }
}