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
public class WalkForwardOptimizationDto {
    private String symbol;
    private String strategyName;
    private int totalHistoricalCandles;
    private int totalWalkForwardWindows;
    private BigDecimal averageInSampleReturnPercent;
    private BigDecimal averageOutOfSampleReturnPercent;
    private BigDecimal overallWalkForwardEfficiencyPercent; // WFE = (Avg OOS Return / Avg IS Return) * 100%
    private String robustnessGrade;                         // "INSTITUTIONAL_ALPHA_GRADE_A", "ACCEPTABLE_GRADE_B", "OVERFITTED_HIGH_DECAY"
    private boolean t25SettlementEnforced;                  // Tuân thủ bắt buộc khóa vị thế T+2.5
    private BigDecimal totalRoundtripTaxAndFeesPercent;     // 0.40% (0.15% mua + 0.15% bán + 0.10% thuế TNCN)
    private List<WalkForwardWindowDetail> windows;          // Chi tiết từng cửa sổ Walk-Forward kiểm định mù
    private String institutionalAuditVerdict;               // Kết luận thẩm định của hội đồng đầu tư định lượng

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalkForwardWindowDetail {
        private int windowIndex;
        private String inSamplePeriod;                      // Giai đoạn Calibration (Tối ưu hóa tham số)
        private String outOfSamplePeriod;                   // Giai đoạn Out-of-Sample (Kiểm định mù độc lập)
        private BigDecimal inSampleReturnPercent;
        private BigDecimal outOfSampleReturnPercent;
        private BigDecimal inSampleSharpeRatio;
        private BigDecimal outOfSampleSharpeRatio;
        private BigDecimal windowWfePercent;
        private int t25HoldingLockEnforcedTrades;
        private String windowVerdict;
    }
}
