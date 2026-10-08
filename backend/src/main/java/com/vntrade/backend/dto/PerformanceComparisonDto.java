package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceComparisonDto {

    private LocalDate asOfDate;
    private BigDecimal currentNav;
    private BigDecimal initialCapital;
    private BigDecimal totalProfitVnd;
    private BigDecimal totalProfitPercent;
    private BigDecimal currentWinRate;
    private int currentTotalTrades;
    private int openPositionsCount;

    // Period Comparison Points
    private PeriodComparisonItem oneDayAgo;      // DoD (Day-over-Day: 1 ngày trước)
    private PeriodComparisonItem oneWeekAgo;     // WoW (Week-over-Week: 7 ngày trước)
    private PeriodComparisonItem oneMonthAgo;    // MoM (Month-over-Month: 30 ngày trước)
    private PeriodComparisonItem threeMonthsAgo; // QoQ (90 ngày trước)

    // Monthly breakdown (Lịch sử theo từng tháng)
    private List<MonthlyPerformanceItem> monthlyBreakdown;

    // Quantitative metrics summary
    private BigDecimal sharpeRatio;
    private BigDecimal maxDrawdownPercent;
    private BigDecimal profitFactor;
    private String hedgeFundRating;
    private String executiveSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodComparisonItem {
        private String periodLabel;
        private LocalDate baselineDate;
        private BigDecimal nav;
        private BigDecimal deltaNavVnd;
        private BigDecimal deltaNavPercent;
        private BigDecimal winRate;
        private int totalTrades;
        private String performanceVerdict;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyPerformanceItem {
        private String monthLabel;
        private BigDecimal startNav;
        private BigDecimal endNav;
        private BigDecimal netProfitVnd;
        private BigDecimal returnPercent;
        private BigDecimal winRate;
        private int tradesCount;
        private String statusBadge;
    }
}
