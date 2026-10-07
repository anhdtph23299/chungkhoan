package com.vntrade.backend.service;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.StatisticalArbitragePairDto;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticalArbitrageService {

    private final CandleDataService candleDataService;
    private final StockPriceService stockPriceService;

    // Các cặp cổ phiếu đồng pha lịch sử truyền thống trên sàn HOSE
    private static final List<PairDefinition> DEFAULT_PAIRS = List.of(
            new PairDefinition("TCB", "MBB", "Ngân hàng TMCP (Top CASA & NIM)"),
            new PairDefinition("SSI", "VND", "Chứng khoán & Môi giới (Thanh khoản VN-Index)"),
            new PairDefinition("HPG", "HSG", "Thép & Vật liệu xây dựng (Chu kỳ HRC)"),
            new PairDefinition("FPT", "MWG", "Công nghệ & Bán lẻ bán lẻ tiêu dùng VN30"),
            new PairDefinition("CTG", "BID", "Ngân hàng Quốc doanh Big 4")
    );

    private record PairDefinition(String stockA, String stockB, String sector) {}

    /**
     * Quét và phân tích toàn bộ danh mục cặp định lượng Statistical Arbitrage
     */
    public List<StatisticalArbitragePairDto> analyzeAllArbitragePairs() {
        List<StatisticalArbitragePairDto> results = new ArrayList<>();
        for (PairDefinition def : DEFAULT_PAIRS) {
            try {
                results.add(analyzePair(def.stockA, def.stockB, def.sector));
            } catch (Exception e) {
                log.warn("Lỗi phân tích cặp StatArb {}/{}: {}", def.stockA, def.stockB, e.getMessage());
            }
        }
        return results;
    }

    /**
     * Phân tích hồi quy đồng liên kết Engle-Granger & Ornstein-Uhlenbeck cho một cặp cổ phiếu
     */
    public StatisticalArbitragePairDto analyzePair(String symbolA, String symbolB, String sector) {
        String symA = symbolA.toUpperCase().trim();
        String symB = symbolB.toUpperCase().trim();

        // 1. Lấy dữ liệu nến 60 phiên giao dịch gần nhất
        List<Candle> candlesA = candleDataService.getHistoricalCandles(symA, 60);
        List<Candle> candlesB = candleDataService.getHistoricalCandles(symB, 60);

        int sampleSize = Math.min(candlesA.size(), candlesB.size());
        if (sampleSize < 20) {
            throw new IllegalArgumentException("Không đủ mẫu dữ liệu lịch sử để kiểm định đồng liên kết (tối thiểu 20 phiên)");
        }

        // Lấy giá đóng cửa đồng pha
        double[] logPricesA = new double[sampleSize];
        double[] logPricesB = new double[sampleSize];

        for (int i = 0; i < sampleSize; i++) {
            // Lấy từ cuối về đầu để đồng bộ thời gian
            Candle cA = candlesA.get(candlesA.size() - sampleSize + i);
            Candle cB = candlesB.get(candlesB.size() - sampleSize + i);
            logPricesA[i] = Math.log(Math.max(100.0, cA.getClose().doubleValue()));
            logPricesB[i] = Math.log(Math.max(100.0, cB.getClose().doubleValue()));
        }

        // 2. Tính hồi quy OLS: ln(P_A) = alpha + beta * ln(P_B)
        double meanA = Arrays.stream(logPricesA).average().orElse(0.0);
        double meanB = Arrays.stream(logPricesB).average().orElse(0.0);

        double cov = 0.0;
        double varB = 0.0;
        for (int i = 0; i < sampleSize; i++) {
            cov += (logPricesB[i] - meanB) * (logPricesA[i] - meanA);
            varB += Math.pow(logPricesB[i] - meanB, 2);
        }
        double beta = varB > 0.0 ? cov / varB : 1.0;
        double alpha = meanA - beta * meanB;

        // 3. Chuỗi phần dư (Spread Residuals): S_t = ln(P_A) - beta * ln(P_B) - alpha
        double[] spreads = new double[sampleSize];
        double spreadSum = 0.0;
        for (int i = 0; i < sampleSize; i++) {
            spreads[i] = logPricesA[i] - (beta * logPricesB[i] + alpha);
            spreadSum += spreads[i];
        }
        double spreadMean = spreadSum / sampleSize;

        double sumSqDiff = 0.0;
        for (double s : spreads) {
            sumSqDiff += Math.pow(s - spreadMean, 2);
        }
        double spreadStdDev = Math.sqrt(sumSqDiff / (sampleSize - 1));
        if (spreadStdDev < 0.0001) spreadStdDev = 0.0001;

        double currentSpread = spreads[sampleSize - 1];
        double currentZScore = (currentSpread - spreadMean) / spreadStdDev;

        // 4. Kiểm định tính dừng Augmented Dickey-Fuller (ADF) trên Spread: Delta(S_t) = gamma * S_{t-1} + e_t
        double sumSPrevDeltaS = 0.0;
        double sumSPrevSq = 0.0;
        double[] deltaS = new double[sampleSize - 1];
        for (int i = 1; i < sampleSize; i++) {
            deltaS[i - 1] = spreads[i] - spreads[i - 1];
            double sPrev = spreads[i - 1];
            sumSPrevDeltaS += sPrev * deltaS[i - 1];
            sumSPrevSq += sPrev * sPrev;
        }

        double gamma = sumSPrevSq > 0.0 ? sumSPrevDeltaS / sumSPrevSq : -0.1;
        // Tính Standard Error của gamma
        double sumResidualSq = 0.0;
        for (int i = 0; i < sampleSize - 1; i++) {
            double res = deltaS[i] - gamma * spreads[i];
            sumResidualSq += res * res;
        }
        double seGamma = Math.sqrt((sumResidualSq / Math.max(1, sampleSize - 3)) / Math.max(1e-8, sumSPrevSq));
        double adfTStat = seGamma > 0.0 ? gamma / seGamma : -2.0;

        // Ước lượng P-value ADF MacKinnon cho chuỗi thời gian n~50
        double adfPValue;
        if (adfTStat <= -3.50) {
            adfPValue = 0.008; // P < 1%
        } else if (adfTStat <= -2.88) {
            adfPValue = 0.045; // P < 5% (Đạt chuẩn đồng liên kết)
        } else if (adfTStat <= -2.57) {
            adfPValue = 0.095; // P < 10%
        } else {
            adfPValue = Math.min(0.85, 0.10 + Math.abs(adfTStat + 2.57) * 0.15);
        }

        // 5. Chu kỳ hồi quy về trung bình Ornstein-Uhlenbeck: dS = theta*(mu - S)dt
        // S_t = (1 + gamma) * S_{t-1} => lambda = -ln(1 + gamma)
        double lambda;
        if (gamma < 0.0 && (1.0 + gamma) > 0.0) {
            lambda = -Math.log(1.0 + gamma);
        } else if (gamma < 0.0) {
            lambda = 0.693; // 1 phiên
        } else {
            lambda = 0.001; // Phân kỳ, không hồi quy
        }
        double halfLifeDays = Math.min(90.0, Math.log(2.0) / lambda);

        // 6. Tín hiệu Arbitrage & Biên an toàn lợi nhuận (Trừ thuế phí 0.40%)
        String signal;
        if (currentZScore <= -1.8) {
            signal = "BUY_A_ROTATE_FROM_B"; // Cổ phiếu A đang bị định giá rẻ bất thường so với B
        } else if (currentZScore >= 1.8) {
            signal = "BUY_B_ROTATE_FROM_A"; // Cổ phiếu B đang bị định giá rẻ bất thường so với A
        } else if (Math.abs(currentZScore) <= 0.4) {
            signal = "TAKE_PROFIT_CONVERGENCE"; // Spread đã hội tụ về trung bình, chốt lời cặp
        } else {
            signal = "NEUTRAL_BALANCED";
        }

        // Lợi nhuận chênh lệch kỳ vọng (%) khi spread hội tụ về 0
        double grossEdgePercent = Math.abs(currentZScore) * spreadStdDev * 100.0;
        double statutoryRoundTripTaxFee = 0.40; // 0.15% phí mua + 0.15% phí bán + 0.10% thuế TNCN
        double expectedNetEdge = Math.max(0.0, grossEdgePercent - statutoryRoundTripTaxFee);

        // 7. Khả thi thực thi T+2.5:
        // Cần halfLife >= 2.0 phiên (đủ thời gian qua chu kỳ T+2.5 mà không bị đảo chiều giật cục)
        // và Net Edge > 0.50% để bù trượt giá spread
        boolean t25Feasible = (halfLifeDays >= 2.0 && halfLifeDays <= 45.0 && expectedNetEdge > 0.50);

        // Giá hiện tại
        StockQuote qA = stockPriceService.getQuote(symA);
        StockQuote qB = stockPriceService.getQuote(symB);
        BigDecimal priceA = qA != null ? qA.getPrice() : candlesA.get(candlesA.size() - 1).getClose();
        BigDecimal priceB = qB != null ? qB.getPrice() : candlesB.get(candlesB.size() - 1).getClose();

        // 8. Nhận định định lượng
        String verdict;
        if (adfPValue < 0.05) {
            if ("BUY_A_ROTATE_FROM_B".equals(signal)) {
                verdict = String.format("Cặp đồng liên kết mạnh (P-value=%.3f, Beta=%.2f). %s đang phân kỳ giảm sâu (Z-Score=%.2f). Khuyến nghị Mua %s và Đảo tỷ trọng từ %s. Chu kỳ hồi quy O-U: %.1f phiên, Lợi nhuận ròng kỳ vọng sau thuế phí: +%.2f%%.",
                        adfPValue, beta, symA, currentZScore, symA, symB, halfLifeDays, expectedNetEdge);
            } else if ("BUY_B_ROTATE_FROM_A".equals(signal)) {
                verdict = String.format("Cặp đồng liên kết mạnh (P-value=%.3f, Beta=%.2f). %s đang tăng quá mức so với %s (Z-Score=%.2f). Khuyến nghị Chốt bớt %s chuyển sang Mua tích lũy %s. Chu kỳ hồi quy O-U: %.1f phiên.",
                        adfPValue, beta, symA, symB, currentZScore, symA, symB, halfLifeDays);
            } else if ("TAKE_PROFIT_CONVERGENCE".equals(signal)) {
                verdict = String.format("Spread đã hội tụ hoàn toàn về trạng thái cân bằng (Z-Score=%.2f). Hiện thực hóa lợi nhuận chênh lệch, đưa tỷ trọng 2 mã về trạng thái trung lập.", currentZScore);
            } else {
                verdict = String.format("Spread đang dao động trong kênh phân phối bình thường (Z-Score=%.2f). Tiếp tục giám sát ngưỡng kích hoạt |Z| >= 1.8.", currentZScore);
            }
        } else {
            verdict = String.format("Cặp %s/%s chưa đạt mức đồng liên kết chặt chẽ (P-value=%.3f > 0.05). Nguy cơ phân kỳ ngẫu nhiên dài hạn, không khuyến nghị giao dịch Statistical Arbitrage đòn bẩy.", symA, symB, adfPValue);
        }

        return StatisticalArbitragePairDto.builder()
                .pairName(symA + "/" + symB)
                .stockA(symA)
                .stockB(symB)
                .sector(sector)
                .stockAPrice(priceA)
                .stockBPrice(priceB)
                .hedgeRatioBeta(BigDecimal.valueOf(beta).setScale(4, RoundingMode.HALF_UP))
                .cointegrationAdfPValue(BigDecimal.valueOf(adfPValue).setScale(4, RoundingMode.HALF_UP))
                .currentSpread(BigDecimal.valueOf(currentSpread).setScale(4, RoundingMode.HALF_UP))
                .spreadMean(BigDecimal.valueOf(spreadMean).setScale(4, RoundingMode.HALF_UP))
                .spreadStdDev(BigDecimal.valueOf(spreadStdDev).setScale(4, RoundingMode.HALF_UP))
                .spreadZScore(BigDecimal.valueOf(currentZScore).setScale(2, RoundingMode.HALF_UP))
                .halfLifeDays(BigDecimal.valueOf(halfLifeDays).setScale(1, RoundingMode.HALF_UP))
                .arbitrageSignal(signal)
                .expectedNetEdgePercent(BigDecimal.valueOf(expectedNetEdge).setScale(2, RoundingMode.HALF_UP))
                .t25RotationFeasible(t25Feasible)
                .institutionalPairVerdict(verdict)
                .build();
    }
}
