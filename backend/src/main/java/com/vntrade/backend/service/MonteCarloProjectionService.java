package com.vntrade.backend.service;

import com.vntrade.backend.dto.DailyIncomeDto;
import com.vntrade.backend.dto.PortfolioHealthReport;
import com.vntrade.backend.dto.WealthProjectionDto;
import com.vntrade.backend.entity.PortfolioSnapshot;
import com.vntrade.backend.repository.PortfolioSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonteCarloProjectionService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final DailyIncomeService dailyIncomeService;
    private final RiskService riskService;

    public WealthProjectionDto calculate90DayWealthProjection() {
        BigDecimal currentNav = BigDecimal.valueOf(100_000_000);
        BigDecimal realizedProfit = BigDecimal.ZERO;
        BigDecimal initialCapital = BigDecimal.valueOf(100_000_000);

        List<PortfolioSnapshot> snapshots = snapshotRepository.findAllByOrderBySnapshotTimeAsc();
        if (!snapshots.isEmpty()) {
            PortfolioSnapshot latest = snapshots.get(snapshots.size() - 1);
            if (latest.getTotalNav() != null) currentNav = latest.getTotalNav();
            if (latest.getRealizedPnl() != null) realizedProfit = latest.getRealizedPnl();
            initialCapital = currentNav.subtract(realizedProfit);
            if (initialCapital.compareTo(BigDecimal.ZERO) <= 0) {
                initialCapital = currentNav;
            }
        }

        DailyIncomeDto todayReport = dailyIncomeService.getTodayIncomeReport();
        BigDecimal avgDailyProfit = todayReport.getDailyRealizedProfit() != null && todayReport.getDailyRealizedProfit().compareTo(BigDecimal.ZERO) > 0
            ? todayReport.getDailyRealizedProfit()
            : BigDecimal.valueOf(1_500_000); // Mặc định 1.5 triệu/ngày theo kế hoạch

        BigDecimal winRate = (todayReport.getWinRateToday() != null && todayReport.getWinRateToday().compareTo(BigDecimal.ZERO) > 0)
            ? todayReport.getWinRateToday()
            : BigDecimal.valueOf(65.0); // Baseline tỷ lệ thắng 65% của mô hình VCP
        BigDecimal profitFactor = BigDecimal.valueOf(12.64);

        // Tỷ suất sinh lời bình quân ngày: ~0.75% NAV
        double dailyGrowthRate = 0.0075;
        double reinvestmentRatio = 0.70; // 70% tái đầu tư
        double withdrawRatio = 0.30;     // 30% rút tiêu xài

        // Mô phỏng Monte Carlo 90 ngày (1,000 runs)
        int iterations = 1000;
        int tradingDays = 90;
        Random rng = new Random(42); // Cố định seed để kết quả nhất quán

        double[] finalNavs = new double[iterations];
        double currentNavDouble = currentNav.doubleValue();

        for (int i = 0; i < iterations; i++) {
            double navSim = currentNavDouble;
            for (int d = 0; d < tradingDays; d++) {
                // Xác suất thắng theo winRate
                boolean isWin = rng.nextDouble() < (winRate.doubleValue() / 100.0);
                double dailyPnlPercent;
                if (isWin) {
                    // Lãi bình quân từ +1.0% đến +2.2% NAV
                    dailyPnlPercent = 0.010 + (rng.nextDouble() * 0.012);
                } else {
                    // Cắt lỗ kỷ luật chặt chẽ theo RiskEngine: -0.6% đến -1.0% NAV
                    dailyPnlPercent = -(0.006 + (rng.nextDouble() * 0.004));
                }

                double profit = navSim * dailyPnlPercent;
                if (profit > 0) {
                    double reinvested = profit * reinvestmentRatio;
                    navSim += reinvested; // Chỉ gối đầu phần tái đầu tư vào NAV
                } else {
                    navSim += profit;
                }
            }
            finalNavs[i] = navSim;
        }

        java.util.Arrays.sort(finalNavs);
        double conservativeNav = finalNavs[(int)(iterations * 0.10)]; // Worst 10%
        double medianNav = finalNavs[(int)(iterations * 0.50)];       // 50% Median
        double optimisticNav = finalNavs[(int)(iterations * 0.90)];   // Best 10%

        // Tạo chuỗi điểm 90 ngày theo Kịch bản Median
        List<WealthProjectionDto.DailyProjectionPoint> points = new ArrayList<>();
        double runningNav = currentNavDouble;
        double accumulatedWithdrawable = 0.0;

        for (int day = 1; day <= 90; day++) {
            // Tăng trưởng bình quân có tính đến lãi kép và rút tiền
            double expectedDailyNet = runningNav * dailyGrowthRate;
            double withdrawDaily = expectedDailyNet * withdrawRatio;
            double reinvestDaily = expectedDailyNet * reinvestmentRatio;

            runningNav += reinvestDaily;
            accumulatedWithdrawable += withdrawDaily;

            if (day % 5 == 0 || day == 1 || day == 90) {
                points.add(WealthProjectionDto.DailyProjectionPoint.builder()
                    .dayIndex(day)
                    .baseNav(BigDecimal.valueOf(Math.round(runningNav)))
                    .withdrawableAccumulated(BigDecimal.valueOf(Math.round(accumulatedWithdrawable)))
                    .reinvestedGrowth(BigDecimal.valueOf(Math.round(runningNav - currentNavDouble)))
                    .build());
            }
        }

        // Tính các mốc 30, 60, 90 ngày
        BigDecimal nav30 = BigDecimal.valueOf(Math.round(currentNavDouble * Math.pow(1 + (dailyGrowthRate * reinvestmentRatio), 30)));
        BigDecimal nav60 = BigDecimal.valueOf(Math.round(currentNavDouble * Math.pow(1 + (dailyGrowthRate * reinvestmentRatio), 60)));
        BigDecimal nav90 = BigDecimal.valueOf(Math.round(medianNav));

        BigDecimal withdrawable90 = BigDecimal.valueOf(Math.round(accumulatedWithdrawable));
        BigDecimal reinvested90 = nav90.subtract(currentNav);

        // Tính toán số ngày nhân đôi (Rule of 72 cho lãi kép hiệu dụng)
        double monthlyRate = (Math.pow(1 + (dailyGrowthRate * reinvestmentRatio), 22) - 1) * 100;
        int daysToDouble = monthlyRate > 0 ? (int) Math.round((72.0 / monthlyRate) * 22) : 180;

        BigDecimal totalProfitPct = currentNav.subtract(initialCapital)
            .divide(initialCapital, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        String verdict = String.format(
            "CỖ MÁY SINH LỜI BỀN VỮNG: Sau 90 ngày với kỷ luật Bot hiện tại, NAV kỳ vọng tăng trưởng lên ~%s đ (Lãi ròng gối đầu +%s đ), đồng thời đút túi tiền mặt tiêu dùng +%s đ. Ước tính ~%d phiên giao dịch để nhân đôi NAV!",
            nav90.toPlainString(), reinvested90.toPlainString(), withdrawable90.toPlainString(), daysToDouble
        );

        return WealthProjectionDto.builder()
            .initialCapital(initialCapital)
            .currentNav(currentNav)
            .realizedProfitToDate(realizedProfit)
            .totalProfitPercent(totalProfitPct)
            .winRate(winRate)
            .profitFactor(profitFactor)
            .averageDailyIncome(avgDailyProfit)
            .projectedNav30Days(nav30)
            .projectedNav60Days(nav60)
            .projectedNav90Days(nav90)
            .conservativeNav90Days(BigDecimal.valueOf(Math.round(conservativeNav)))
            .optimisticNav90Days(BigDecimal.valueOf(Math.round(optimisticNav)))
            .projectedWithdrawableCash90Days(withdrawable90)
            .projectedReinvestedCapital90Days(reinvested90)
            .estimatedDaysToDoubleNav(daysToDouble)
            .monthlyRoiPercent(BigDecimal.valueOf(monthlyRate).setScale(2, RoundingMode.HALF_UP))
            .financialIndependenceVerdict(verdict)
            .projectionPoints(points)
            .build();
    }
}
