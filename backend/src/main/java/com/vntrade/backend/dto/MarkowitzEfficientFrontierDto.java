package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkowitzEfficientFrontierDto {
    private String modelName;                       // Markowitz Mean-Variance Modern Portfolio Theory
    private BigDecimal currentPortfolioNav;          // Tổng giá trị NAV danh mục
    private BigDecimal expectedAnnualReturnPercent;  // Tỷ suất sinh lời kỳ vọng năm E(Rp)
    private BigDecimal annualizedVolatilityPercent;  // Độ biến động kỳ vọng năm (Standard Deviation)
    private BigDecimal maxSharpeRatio;               // Tỷ số Sharpe tối ưu
    private Map<String, BigDecimal> optimalWeights;  // Tỷ trọng phân bổ tối ưu từng mã (% NAV)
    private List<FrontierPoint> efficientFrontierCurve; // Tọa độ đường cong biên hiệu quả (Risk - Return)
    private String rebalancingStrategyRecommendation;// Khuyến nghị tái cân bằng danh mục
    private String quantitativeVerdict;              // Đánh giá định lượng từ mô hình

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrontierPoint {
        private BigDecimal riskVolatilityPercent;   // Trục hoành: Rủi ro biến động (%)
        private BigDecimal expectedReturnPercent;  // Trục tung: Lợi nhuận kỳ vọng (%)
        private String portfolioType;              // MIN_VARIANCE, TANGENCY_MAX_SHARPE, AGGRESSIVE
    }
}
