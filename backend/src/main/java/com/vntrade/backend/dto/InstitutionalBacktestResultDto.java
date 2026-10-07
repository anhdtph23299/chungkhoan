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
public class InstitutionalBacktestResultDto {
    private String symbol;
    private String strategyName;
    private int testedCandlesCount;
    private BigDecimal initialCapital;
    private BigDecimal finalCapital;
    private BigDecimal netProfit;
    private BigDecimal totalReturnPercent;
    private BigDecimal annualizedCagrPercent;
    private BigDecimal winRatePercent;
    private BigDecimal profitFactor;
    private BigDecimal maxDrawdownPercent;
    private BigDecimal sharpeRatio;
    private BigDecimal sortinoRatio;
    private BigDecimal calmarRatio;
    private BigDecimal totalFeesAndTaxesPaid;
    private boolean t25Enforced;                      // Ràng buộc chu kỳ T+2.5 bắt buộc
    private int t25TrappedSessionsAvoidedCount;       // Số phiên tránh được bẫy kẹt hàng T+1
    private BigDecimal inSampleReturnPercent;         // Lợi nhuận mẫu kiểm thử quá khứ (70% data)
    private BigDecimal outOfSampleReturnPercent;      // Lợi nhuận dữ liệu chưa từng thấy (30% unseen data)
    private BigDecimal walkForwardEfficiencyPercent;  // Tỷ số Walk-Forward Efficiency (WFE)
    private String overfittingRisk;                   // LOW, ACCEPTABLE, HIGH_OVERFIT
    private String institutionalAuditVerdict;         // Nhận định định lượng của quỹ
    private List<BacktestTrade> tradesHistory;
}
