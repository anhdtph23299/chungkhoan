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
public class CanslimRatingDto {
    private String symbol;
    private String companyName;
    private String sector;
    private BigDecimal currentQuarterEpsGrowthPercent;   // C: Current Quarterly EPS >= 20%
    private BigDecimal annualEarningsGrowthPercent;       // A: Annual Earnings Growth >= 20%
    private String newFactorCatalyst;                    // N: New Product / 52-Week High / New Catalyst
    private Long averageDailyVolume;                     // S: Supply & Demand
    private boolean isSectorLeader;                      // L: Leader vs Laggard
    private String institutionalSponsorship;             // I: Institutional Ownership (Dragon Capital, VinaCapital, etc.)
    private String marketDirectionStatus;                // M: Market Direction (Confirmed Uptrend)
    private int canslimScore;                            // 0 - 100
    private String canslimGrade;                         // A+, A, B, C, F
    private boolean institutionalGrade;                  // true if score >= 75
    private List<String> canslimHighlights;
    private String institutionalVerdict;
}
