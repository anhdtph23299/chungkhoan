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
public class Vn30BacktestMatrixDto {
    private String matrixName;
    private String executionRule; // "T+2.5 Mandatory Holding Lock + 0.40% Roundtrip Tax/Fees"
    private int testedStocksCount;
    private String recommendedTopPick;
    private BigDecimal averageWinRatePercent;
    private BigDecimal averageReturnPercent;
    private BigDecimal averageSharpeRatio;
    private List<Vn30BacktestSummaryItem> rankings;
    private String institutionalAuditSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vn30BacktestSummaryItem {
        private int rank;
        private String symbol;
        private String sector;
        private BigDecimal totalReturnPercent;
        private BigDecimal winRatePercent;
        private BigDecimal profitFactor;
        private BigDecimal maxDrawdownPercent;
        private BigDecimal sharpeRatio;
        private BigDecimal walkForwardEfficiencyPercent;
        private String overfittingRisk;
        private String allocationRecommendation; // "OVERWEIGHT_ALLOCATE", "NEUTRAL_ALLOCATE", "UNDERWEIGHT"
    }
}
