package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WealthProjectionDto {
    private BigDecimal initialCapital;
    private BigDecimal currentNav;
    private BigDecimal realizedProfitToDate;
    private BigDecimal totalProfitPercent;
    private BigDecimal winRate;
    private BigDecimal profitFactor;
    private BigDecimal averageDailyIncome;

    // Dự phóng các mốc tương lai (Kịch bản Base Case)
    private BigDecimal projectedNav30Days;
    private BigDecimal projectedNav60Days;
    private BigDecimal projectedNav90Days;

    // Kịch bản thận trọng (Worst 10% Monte Carlo)
    private BigDecimal conservativeNav90Days;

    // Kịch bản đột phá (Best 10% Monte Carlo)
    private BigDecimal optimisticNav90Days;

    // Dòng tiền rút chi tiêu & Tái đầu tư lãi kép
    private BigDecimal projectedWithdrawableCash90Days; // 30% tiền mặt rút tiêu dùng
    private BigDecimal projectedReinvestedCapital90Days; // 70% tiếp tục sinh lời kép

    // Thời gian nhân đôi tài khoản (Rule of 72)
    private int estimatedDaysToDoubleNav;
    private BigDecimal monthlyRoiPercent;
    private String financialIndependenceVerdict;

    // Chuỗi điểm mô phỏng đường cong tăng trưởng vốn (Equity curve simulation)
    private List<DailyProjectionPoint> projectionPoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyProjectionPoint {
        private int dayIndex;
        private BigDecimal baseNav;
        private BigDecimal withdrawableAccumulated;
        private BigDecimal reinvestedGrowth;
    }
}
