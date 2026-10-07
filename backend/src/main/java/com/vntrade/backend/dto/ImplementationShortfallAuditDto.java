package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImplementationShortfallAuditDto {
    private String symbol;
    private String algorithmName; // "VWAP_SMART_SLICING", "TWAP_STEALTH", "ICEBERG_PEG_MID"
    private int totalOrderQuantity;
    private BigDecimal decisionBenchmarkPrice;
    private BigDecimal arrivalPrice;
    private BigDecimal averageFillPrice;
    private BigDecimal totalTradedValueVnd;

    private BigDecimal delayCostBps;
    private BigDecimal priceImpactBps;
    private BigDecimal feeAndTaxBps;
    private BigDecimal totalImplementationShortfallBps;

    private BigDecimal costSavingsVsMarketOrderVnd;
    private BigDecimal executionAlphaBps;
    private String executionQualityGrade; // "AAA_PRIME_EXECUTION", "AA_OPTIMAL", "ACCEPTABLE"
    private String institutionalAuditVerdict;
}
