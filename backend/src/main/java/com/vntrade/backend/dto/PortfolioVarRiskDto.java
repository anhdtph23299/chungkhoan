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
public class PortfolioVarRiskDto {
    private BigDecimal currentPortfolioNav;          // Tổng giá trị tài sản ròng (NAV)
    private BigDecimal portfolioBeta;                 // Độ nhạy Beta danh mục so với VN-Index
    private BigDecimal dailyVolatilityPercent;        // Độ biến động hàng ngày (Daily Std Dev %)
    private BigDecimal parametricVar95Amount;         // Giá trị chịu rủi ro 1 ngày tại độ tin cậy 95% (VND)
    private BigDecimal parametricVar95Percent;        // % VaR 95% trên tổng NAV
    private BigDecimal cornishFisherVar95Amount;      // VaR 95% hiệu chỉnh độ lệch & độ nhọn đuôi béo Cornish-Fisher (VND)
    private BigDecimal cornishFisherVar95Percent;     // % Cornish-Fisher VaR 95% trên tổng NAV
    private BigDecimal t25MultiDayHoldingVaR95Amount; // Rủi ro chịu đựng trong kỳ hạn khóa T+2.5 (VND, scale sqrt(2.5))
    private BigDecimal t25MultiDayHoldingVaR95Percent;// % Rủi ro kỳ hạn khóa T+2.5 trên tổng NAV
    private BigDecimal tailRiskStressFactor;          // Hệ số khuyếch đại rủi ro đuôi béo (CF-VaR / Gaussian-VaR)
    private BigDecimal t25LiquidityReserveRequired;   // Quỹ đệm thanh khoản tiền mặt dự phòng bắt buộc (VND)
    private BigDecimal conditionalVar99Amount;        // CVaR (Expected Shortfall) 99% (VND)
    private BigDecimal conditionalVar99Percent;       // % CVaR 99% trên tổng NAV
    private BigDecimal historicalVaR95Percent;        // VaR mô phỏng lịch sử 95%
    private BigDecimal maxHistoricalDrawdown;         // Mức sụt giảm tối đa lịch sử (%)
    private String riskRatingLevel;                   // LOW, MODERATE, ELEVATED, EXTREME
    private Map<String, BigDecimal> assetAllocationWeights; // Tỷ trọng phân bổ từng mã (% NAV)
    private List<CorrelationPair> correlationMatrix;  // Ma trận tương quan giữa các cặp tài sản
    private String riskOfficerVerdict;                // Kết luận phê duyệt rủi ro của quỹ

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CorrelationPair {
        private String symbolA;
        private String symbolB;
        private BigDecimal correlationCoefficient;   // Hệ số tương quan Pearson (-1.0 đến +1.0)
        private String correlationLevel;             // HIGH_CORRELATION, MODERATE, DIVERSIFIED
    }
}
