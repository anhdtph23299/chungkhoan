package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.AlmgrenChrissExecutionDto;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import com.vntrade.backend.service.marketdata.OrderBookService;
import com.vntrade.backend.service.marketdata.StockPriceService;

/**
 * Mô hình Khớp lệnh Tối ưu Almgren-Chriss (2000) - "Optimal Execution of Portfolio Transactions"
 * Tích hợp chuẩn giao dịch sàn HOSE/HNX:
 *
 * 1. Phân rã tác động thị trường thành Permanent Impact (Vĩnh viễn) và Temporary Impact (Tạm thời).
 * 2. Cân bằng giữa Rủi ro Trượt giá (Market Impact) và Rủi ro Biến động giá bất lợi theo thời gian (Timing Risk).
 * 3. Tuân thủ nghiêm ngặt quy tắc bước giá (Tick Size: 10đ / 50đ / 100đ) và lô chẵn 100 cp của Việt Nam.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlmgrenChrissExecutionService {

    private final StockPriceService stockPriceService;
    private final OrderBookService orderBookService;

    public AlmgrenChrissExecutionDto planOptimalExecution(
            String symbol,
            int totalShares,
            Double riskAversion) {

        String sym = symbol != null ? symbol.toUpperCase() : "FPT";
        int shares = Math.max(100, (totalShares / 100) * 100);
        double lambda = riskAversion != null && riskAversion >= 0 ? riskAversion : 0.5; // Mức ngại rủi ro chuẩn

        StockQuote quote = stockPriceService.getQuote(sym);
        BigDecimal marketPrice = quote != null && quote.getPrice() != null
            ? quote.getPrice()
            : BigDecimal.valueOf(135000);

        long adv = quote != null && quote.getVolume() != null && quote.getVolume() > 100_000
            ? quote.getVolume()
            : 3_500_000L;

        // 1. Tỷ lệ tham gia thị trường (Participation Rate: Q / ADV)
        double participationRate = (double) shares / adv;
        BigDecimal participationPct = BigDecimal.valueOf(participationRate * 100.0).setScale(2, RoundingMode.HALF_UP);

        // 2. Hệ số tác động thị trường chuẩn sàn HOSE
        double dailyVol = 0.018; // Độ biến động ngày 1.8%
        double priceDbl = marketPrice.doubleValue();
        double gamma = 0.05 * (dailyVol * priceDbl / adv); // Permanent impact coefficient
        double eta = 0.15 * (dailyVol * priceDbl / adv);   // Temporary impact coefficient

        // 3. Hệ số tốc độ thanh lý tối ưu Almgren-Chriss (Kappa)
        // kappa ~ sqrt(lambda * sigma^2 / eta)
        double kappa = Math.sqrt(Math.max(1e-5, (lambda * Math.pow(dailyVol * priceDbl, 2)) / Math.max(1e-6, eta)));
        kappa = Math.max(0.2, Math.min(2.5, kappa)); // Chuẩn hóa trong miền thực tế

        // 4. Chia 6 khung giờ giao dịch chuẩn TTCK Việt Nam
        String[] timeSlots = {
            "09:15 - 09:45 (Khởi động khớp lệnh liên tục)",
            "09:45 - 10:30 (Sóng giao dịch phiên sáng)",
            "10:30 - 11:30 (Tích lũy trước giờ nghỉ trưa)",
            "13:00 - 13:45 (Mở phiên chiều hấp thụ T+2.5)",
            "13:45 - 14:30 (Khung giờ dòng tiền tạo lập bùng nổ)",
            "14:30 - 14:45 (Phiên đóng cửa định kỳ ATC)"
        };
        int numTranches = timeSlots.length;

        List<AlmgrenChrissExecutionDto.OptimalTrancheStep> tranches = new ArrayList<>();
        int accumulatedShares = 0;
        double totalT = 1.0; // Tổng thời gian chuẩn hóa = 1 ngày giao dịch

        // Sinh quỹ đạo thanh lý Hyperbolic: x_j = Q * sinh(kappa * (T - t)) / sinh(kappa * T)
        double sinhKT = Math.sinh(kappa * totalT);

        for (int j = 1; j <= numTranches; j++) {
            double t = (double) j / numTranches;
            double remainingIdeal = j == numTranches
                ? 0.0
                : shares * (Math.sinh(kappa * (totalT - t)) / sinhKT);

            int targetCum = j == numTranches
                ? shares
                : (int) Math.round((shares - remainingIdeal) / 100.0) * 100;

            int trancheShares = Math.max(0, targetCum - accumulatedShares);
            if (j == numTranches) {
                trancheShares = shares - accumulatedShares; // Tròn toàn bộ số lượng lệnh
            }
            accumulatedShares += trancheShares;

            // Tính giá Limit tối ưu khớp lệnh tuân thủ Tick Size
            BigDecimal tick = orderBookService.getTickSize(marketPrice);
            double tempImpactPct = 0.0005 * j * (1.0 + participationRate * 2.0);
            BigDecimal rawLimit = marketPrice.multiply(BigDecimal.valueOf(1.0 + tempImpactPct));
            BigDecimal targetLimitPrice = roundToTickSize(rawLimit, tick);

            double remPct = Math.max(0.0, ((double) (shares - accumulatedShares) / shares) * 100.0);

            tranches.add(AlmgrenChrissExecutionDto.OptimalTrancheStep.builder()
                .stepIndex(j)
                .timeSlot(timeSlots[j - 1])
                .trancheShares(trancheShares)
                .cumulativeShares(accumulatedShares)
                .remainingSharesPercent(BigDecimal.valueOf(remPct).setScale(1, RoundingMode.HALF_UP))
                .targetLimitPrice(targetLimitPrice)
                .trancheValueVnd(targetLimitPrice.multiply(BigDecimal.valueOf(trancheShares)))
                .tickSizeCompliance("Bước giá HOSE: " + tick.toPlainString() + " đ")
                .build());
        }

        // 5. Tính toán chi phí tác động thị trường (Market Impact Cost)
        // Permanent impact: 0.5 * gamma * Q^2
        double permCostVnd = 0.5 * gamma * Math.pow(shares, 2);
        // Temporary impact: sum(eta * (n_j / tau)^2 * tau)
        double tempCostVnd = 0.0;
        double tau = totalT / numTranches;
        for (var tr : tranches) {
            tempCostVnd += eta * Math.pow(tr.getTrancheShares() / tau, 2) * tau * 1e-6;
        }

        double totalImpactVnd = permCostVnd + tempCostVnd;
        double grossValue = priceDbl * shares;

        BigDecimal permBps = BigDecimal.valueOf((permCostVnd / grossValue) * 10000.0).setScale(1, RoundingMode.HALF_UP);
        BigDecimal tempBps = BigDecimal.valueOf((tempCostVnd / grossValue) * 10000.0).setScale(1, RoundingMode.HALF_UP);
        BigDecimal totalBps = permBps.add(tempBps);

        BigDecimal impactVnd = BigDecimal.valueOf(totalImpactVnd).setScale(0, RoundingMode.HALF_UP);
        BigDecimal statutoryFees = BigDecimal.valueOf(grossValue * 0.0025).setScale(0, RoundingMode.HALF_UP); // 0.15% phí + 0.10% thuế

        // Điểm số hiệu quả khớp lệnh (Efficiency Score 0 - 100)
        double efficiencyScore = Math.max(60.0, 98.0 - totalBps.doubleValue() * 0.8);

        // Cảnh báo thanh khoản định chế
        String warning = participationRate > 0.05
            ? String.format("CẢNH BÁO THANH KHOẢN CAO: Khối lượng đặt chiếm %.2f%% ADV (> 5%%). Khuyến nghị chia nhỏ lệnh sang 2 phiên để tránh bị ăn trượt giá nặng!", participationPct.doubleValue())
            : "Mức độ hấp thụ thanh khoản an toàn (< 5% ADV).";

        String summary = String.format(
            "KẾ HOẠCH KHỚP LỆNH TỐI ƯU ALMGREN-CHRISS CHO %d CP %s: Tổng tác động thị trường ước tính %s bps (~%s đ). " +
            "Thuật toán chia lệnh thành %d đợt gom thông minh theo hàm hyperbolic (Lambda = %.2f), bảo đảm 100%% tuân thủ lô chẵn 100 cp và bước giá sàn HOSE. " +
            "Điểm số hiệu năng khớp lệnh: %.1f/100.",
            shares, sym, totalBps.toPlainString(), impactVnd.toPlainString(), tranches.size(), lambda, efficiencyScore
        );

        return AlmgrenChrissExecutionDto.builder()
            .symbol(sym)
            .totalOrderShares(shares)
            .currentMarketPrice(marketPrice)
            .averageDailyVolume(adv)
            .marketParticipationRatePercent(participationPct)
            .riskAversionParameter(lambda)
            .expectedPermanentImpactBps(permBps)
            .expectedTemporaryImpactBps(tempBps)
            .totalExecutionDragBps(totalBps)
            .totalExecutionDragVnd(impactVnd)
            .statutoryExchangeTaxesAndFees(statutoryFees)
            .netExecutionEfficiencyScore(BigDecimal.valueOf(efficiencyScore).setScale(1, RoundingMode.HALF_UP))
            .optimalTrajectory(tranches)
            .liquidityWarning(warning)
            .institutionalExecutionSummary(summary)
            .build();
    }

    private BigDecimal roundToTickSize(BigDecimal price, BigDecimal tick) {
        if (tick == null || tick.compareTo(BigDecimal.ZERO) <= 0) return price.setScale(0, RoundingMode.HALF_UP);
        BigDecimal divided = price.divide(tick, 0, RoundingMode.HALF_UP);
        return divided.multiply(tick);
    }
}