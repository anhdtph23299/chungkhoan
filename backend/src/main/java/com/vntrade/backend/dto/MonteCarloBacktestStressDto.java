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
public class MonteCarloBacktestStressDto {
    private String symbol;
    private String strategyName;
    private int simulationsCount;
    private int tradesPerPath;
    private BigDecimal initialCapital;
    private BigDecimal medianTerminalNav;
    private BigDecimal percentile5thNav;          // Kịch bản bi quan (5th percentile)
    private BigDecimal percentile95thNav;         // Kịch bản lạc quan (95th percentile)
    private BigDecimal medianMaxDrawdownPercent;
    private BigDecimal worstCaseDrawdown99thPercent; // Drawdown xấu nhất ở phân vị 99%
    private BigDecimal ruinProbabilityPercent;    // Xác suất chạm ngưỡng sụt giảm vốn nghiêm trọng (>10%)
    private int maxLosingStreak95thPercentile;    // Chuỗi thua liên tiếp dài nhất ở phân vị 95%
    private BigDecimal requiredCapitalReserve;   // Quỹ đệm vốn dự phòng an toàn (VND)
    private String stressTestTier;                // "INSTITUTIONAL_GOLD", "INSTITUTIONAL_SILVER", "HIGH_RISK"
    private String quantAuditVerdict;
}
