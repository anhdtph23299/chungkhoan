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
public class PortfolioHealthReport {
    private String status;                   // HEALTHY, CAUTION, DEFENSIVE
    private BigDecimal currentNav;
    private BigDecimal cashPercent;
    private BigDecimal investedPercent;
    private int openPositionsCount;
    private int maxAllowedPositions;
    private Map<String, BigDecimal> sectorAllocations; // Sector -> Percentage
    private List<String> sectorWarnings;
    private String streakStatus;              // WIN_STREAK, LOSS_STREAK, NEUTRAL
    private int streakCount;
    private BigDecimal recommendedRiskPercent; // 1.0% to 2.0% NAV based on anti-martingale
    private boolean allowNewPurchases;
    private List<String> riskChecklist;
}
