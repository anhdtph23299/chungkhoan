package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OversoldBounceDto {

    private LocalDateTime scanTime;
    private int totalSymbolsScanned;
    private int oversoldCandidatesCount;
    private String marketPanicStatus; // "EXTREME_PANIC", "MODERATE_OVERSOLD", "NORMAL_CORRECTION"
    private String macroVerdict;

    private List<OversoldCandidate> candidates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OversoldCandidate {
        private String symbol;
        private String sector;
        private BigDecimal currentPrice;
        private BigDecimal changePercent;
        private BigDecimal rsi14;
        private BigDecimal bollingerLower;
        private BigDecimal sma200;
        private BigDecimal distanceToSma200Percent;
        private String divergenceSignal; // "BULLISH_DIVERGENCE", "VOLUME_CLIMAX", "PINBAR_REVERSAL"
        private int bounceScore; // 0 - 100
        private BigDecimal suggestedTacticalAllocationPercent; // 5% - 10% NAV
        private BigDecimal tacticalStopLoss;
        private BigDecimal tacticalTargetPrice;
        private double riskRewardRatio;
        private String executionTactic;
    }
}
