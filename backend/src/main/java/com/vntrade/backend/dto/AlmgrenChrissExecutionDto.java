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
public class AlmgrenChrissExecutionDto {
    private String symbol;
    private int totalOrderShares;
    private BigDecimal currentMarketPrice;
    private long averageDailyVolume;                   // Khối lượng khớp lệnh bình quân ngày (ADV)
    private BigDecimal marketParticipationRatePercent; // Tỷ lệ chiếm dụng thanh khoản (Q / ADV %)
    private double riskAversionParameter;              // Tham số ngại rủi ro của quỹ (Lambda)
    private BigDecimal expectedPermanentImpactBps;     // Tác động giá vĩnh viễn (Permanent Impact bps)
    private BigDecimal expectedTemporaryImpactBps;     // Tác động giá tạm thời (Temporary Impact bps)
    private BigDecimal totalExecutionDragBps;          // Tổng chi phí trượt giá và tác động thị trường (bps)
    private BigDecimal totalExecutionDragVnd;          // Tổng chi phí trượt giá bằng tiền mặt (VND)
    private BigDecimal statutoryExchangeTaxesAndFees;  // Thuế phí giao dịch luật định (0.15% phí + 0.10% thuế nếu bán)
    private BigDecimal netExecutionEfficiencyScore;    // Điểm số tối ưu khớp lệnh định chế (0 - 100)
    private List<OptimalTrancheStep> optimalTrajectory;// Lộ trình chẻ nhỏ lệnh tối ưu Almgren-Chriss
    private String liquidityWarning;                   // Cảnh báo thanh khoản nếu chiếm > 5% ADV
    private String institutionalExecutionSummary;      // Báo cáo kiểm định chiến lược khớp lệnh

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimalTrancheStep {
        private int stepIndex;
        private String timeSlot;
        private int trancheShares;
        private int cumulativeShares;
        private BigDecimal remainingSharesPercent;
        private BigDecimal targetLimitPrice;
        private BigDecimal trancheValueVnd;
        private String tickSizeCompliance;
    }
}
