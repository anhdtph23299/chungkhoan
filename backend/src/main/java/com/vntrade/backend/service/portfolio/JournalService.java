package com.vntrade.backend.service.portfolio;

import com.vntrade.backend.dto.TradeAnalytics;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JournalService {

    private final TradeRepository tradeRepository;

    /**
     * Phân tích định lượng hiệu suất đầu tư và kiểm tra tính toán học
     */
    public TradeAnalytics computeAnalytics() {
        List<Trade> closedTrades = tradeRepository.findByStatusOrderByTradeDateDesc("closed");

        if (closedTrades.isEmpty()) {
            return TradeAnalytics.builder()
                .totalTrades(0)
                .winningTrades(0)
                .losingTrades(0)
                .winRate(BigDecimal.ZERO)
                .totalGrossProfit(BigDecimal.ZERO)
                .totalGrossLoss(BigDecimal.ZERO)
                .netProfit(BigDecimal.ZERO)
                .profitFactor(BigDecimal.ZERO)
                .averageWin(BigDecimal.ZERO)
                .averageWinPercent(BigDecimal.ZERO)
                .averageLoss(BigDecimal.ZERO)
                .averageLossPercent(BigDecimal.ZERO)
                .winLossRatio(BigDecimal.ZERO)
                .mathematicalExpectancy(BigDecimal.ZERO)
                .isReadyForRealMoney(false)
                .readinessMessage("Chưa có lệnh đóng nào. Cần thực hiện ít nhất 30-50 lệnh demo tuân thủ kỷ luật.")
                .largestWin(BigDecimal.ZERO)
                .largestLoss(BigDecimal.ZERO)
                .strategyCounts(Map.of())
                .build();
        }

        int total = closedTrades.size();
        int winCount = 0;
        int lossCount = 0;

        BigDecimal totalWin = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;
        BigDecimal totalWinPct = BigDecimal.ZERO;
        BigDecimal totalLossPct = BigDecimal.ZERO;

        BigDecimal largestWin = BigDecimal.ZERO;
        BigDecimal largestLoss = BigDecimal.ZERO;

        Map<String, Integer> strategyCounts = new HashMap<>();

        for (Trade t : closedTrades) {
            BigDecimal pnl = t.getPnl() != null ? t.getPnl() : BigDecimal.ZERO;
            BigDecimal pnlPct = t.getPnlPercent() != null ? t.getPnlPercent() : BigDecimal.ZERO;

            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                winCount++;
                totalWin = totalWin.add(pnl);
                totalWinPct = totalWinPct.add(pnlPct);
                if (pnl.compareTo(largestWin) > 0) largestWin = pnl;
            } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
                lossCount++;
                totalLoss = totalLoss.add(pnl.abs());
                totalLossPct = totalLossPct.add(pnlPct.abs());
                if (pnl.abs().compareTo(largestLoss) > 0) largestLoss = pnl.abs();
            }

            String strat = t.getStrategy() != null ? t.getStrategy() : "Không xác định";
            strategyCounts.put(strat, strategyCounts.getOrDefault(strat, 0) + 1);
        }

        BigDecimal winRate = total > 0
            ? BigDecimal.valueOf(winCount).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        BigDecimal netProfit = totalWin.subtract(totalLoss);

        BigDecimal profitFactor = totalLoss.compareTo(BigDecimal.ZERO) > 0
            ? totalWin.divide(totalLoss, 2, RoundingMode.HALF_UP)
            : (totalWin.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(99.0) : BigDecimal.ZERO);

        BigDecimal avgWin = winCount > 0
            ? totalWin.divide(BigDecimal.valueOf(winCount), 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal avgWinPct = winCount > 0
            ? totalWinPct.divide(BigDecimal.valueOf(winCount), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal avgLoss = lossCount > 0
            ? totalLoss.divide(BigDecimal.valueOf(lossCount), 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal avgLossPct = lossCount > 0
            ? totalLossPct.divide(BigDecimal.valueOf(lossCount), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal winLossRatio = avgLoss.compareTo(BigDecimal.ZERO) > 0
            ? avgWin.divide(avgLoss, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Kỳ vọng toán học (Expectancy per Trade) = (WinRate * AvgWin) - (LossRate * AvgLoss)
        BigDecimal winRateDec = BigDecimal.valueOf(winCount).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        BigDecimal lossRateDec = BigDecimal.valueOf(lossCount).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        BigDecimal expectancy = winRateDec.multiply(avgWin).subtract(lossRateDec.multiply(avgLoss));

        // Đánh giá điều kiện sẵn sàng giao dịch tiền thật
        boolean ready = false;
        String message;

        if (total < 20) {
            message = "Số lượng lệnh chưa đủ thống kê (" + total + "/20 lệnh). Hãy tiếp tục rèn luyện kỷ luật trên tài khoản demo.";
        } else if (expectancy.compareTo(BigDecimal.ZERO) <= 0) {
            message = "CẢNH BÁO: Kỳ vọng toán học đang âm hoặc hòa vốn. Cần tối ưu lại điểm cắt lỗ (tối đa 7%) và gồng lãi dài hơn (ít nhất 15%) trước khi chơi tiền thật!";
        } else if (winLossRatio.compareTo(BigDecimal.valueOf(1.8)) < 0) {
            message = "Tỷ lệ Lãi TB / Lỗ TB chưa đạt chuẩn 2:1. Đang chốt non hoặc cắt lỗ trễ.";
        } else {
            ready = true;
            message = "CHÚC MỪNG! Hệ số Profit Factor = " + profitFactor + ", Kỳ vọng toán học dương (+" + expectancy.setScale(0, RoundingMode.HALF_UP) + " đ/lệnh). Bạn đã đủ điều kiện tâm lý và kỷ luật để bắt đầu giao dịch TIỀN THẬT với số vốn nhỏ (10-20 triệu VND)!";
        }

        BigDecimal totalMfe = BigDecimal.ZERO;
        BigDecimal totalMae = BigDecimal.ZERO;
        for (Trade t : closedTrades) {
            BigDecimal mfe = t.getMfePercent() != null ? t.getMfePercent()
                : (t.getPnlPercent() != null ? t.getPnlPercent().max(BigDecimal.ZERO) : BigDecimal.ZERO);
            BigDecimal mae = t.getMaePercent() != null ? t.getMaePercent()
                : (t.getPnlPercent() != null ? t.getPnlPercent().min(BigDecimal.ZERO) : BigDecimal.ZERO);
            totalMfe = totalMfe.add(mfe);
            totalMae = totalMae.add(mae);
        }

        BigDecimal avgMfe = total > 0 ? totalMfe.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgMae = total > 0 ? totalMae.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal exitEfficiency = totalMfe.compareTo(BigDecimal.ZERO) > 0
            ? totalWinPct.divide(totalMfe, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).min(BigDecimal.valueOf(100))
            : BigDecimal.valueOf(85.0);

        return TradeAnalytics.builder()
            .totalTrades(total)
            .winningTrades(winCount)
            .losingTrades(lossCount)
            .winRate(winRate)
            .totalGrossProfit(totalWin)
            .totalGrossLoss(totalLoss)
            .netProfit(netProfit)
            .profitFactor(profitFactor)
            .averageWin(avgWin)
            .averageWinPercent(avgWinPct)
            .averageLoss(avgLoss)
            .averageLossPercent(avgLossPct)
            .winLossRatio(winLossRatio)
            .mathematicalExpectancy(expectancy)
            .isReadyForRealMoney(ready)
            .readinessMessage(message)
            .largestWin(largestWin)
            .largestLoss(largestLoss)
            .averageMfePercent(avgMfe)
            .averageMaePercent(avgMae)
            .exitEfficiencyPercent(exitEfficiency)
            .strategyCounts(strategyCounts)
            .build();
    }
}
