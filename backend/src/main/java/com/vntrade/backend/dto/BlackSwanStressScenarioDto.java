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
public class BlackSwanStressScenarioDto {
    private BigDecimal portfolioCapital;                // Tổng quy mô vốn danh mục (VNĐ)
    private int overallResilienceScore;                 // Điểm khả năng chống chịu Thiên nga đen (0 - 100)
    private BigDecimal worstCaseDrawdownPercent;        // Mức sụt giảm tối đa kịch bản xấu nhất (%)
    private BigDecimal worstCaseLossAmount;             // Số tiền lỗ lớn nhất (VNĐ)
    private boolean marginCallTriggered;                // Cảnh báo nguy cơ Call Margin / Bán giải chấp
    private BigDecimal recommendedCashBufferPercent;    // Tỷ lệ tiền mặt tối thiểu cần duy trì phòng vệ (%)
    private Integer recommendedVn30FuturesHedgeContracts; // Số hợp đồng tương lai VN30F1M cần Short phòng hộ
    private String institutionalStressVerdict;          // Kết luận kiểm tra sức chịu đựng của quỹ
    private List<StressScenarioDetail> scenarioResults; // Chi tiết từng kịch bản khủng hoảng

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StressScenarioDetail {
        private String scenarioId;
        private String scenarioName;
        private String historicalEventDescription;
        private BigDecimal marketShockPercent;          // Mức sập của VN-Index (%)
        private BigDecimal portfolioLossPercent;        // Mức tổn thất danh mục (%)
        private BigDecimal portfolioLossVnd;            // Số tiền tổn thất (VNĐ)
        private BigDecimal capitalAfterShock;           // Vốn còn lại sau cú sốc (VNĐ)
        private String t25LiquidityFreezeImpact;        // Tác động của kẹt thanh khoản T+2.5
        private String survivalStatus;                  // "SURVIVED_HEALTHY", "SURVIVED_DRAWDOWN", "MARGIN_CALL_RISK", "INSOLVENCY_CRITICAL"
    }
}
