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
public class OrderBookImbalanceDto {
    private String symbol;
    private BigDecimal currentPrice;
    private BigDecimal midPrice;
    private BigDecimal microPrice;
    private BigDecimal microPricePremiumPercent; // (MicroPrice - MidPrice) / MidPrice * 100%

    private Long totalBidVolume;
    private Long totalAskVolume;
    private BigDecimal orderBookImbalanceRatio; // OBI: (WeightedBid - WeightedAsk) / (WeightedBid + WeightedAsk) [-1.0, +1.0]

    private String wallDetected; // "NONE", "BID_WALL_SUPPORT", "ASK_WALL_RESISTANCE", "DOUBLE_WALL"
    private BigDecimal wallPrice;
    private Long wallVolume;
    private BigDecimal wallProportionPercent;

    private String optimalExecutionStrategy; // "PASSIVE_LIMIT_POST_ONLY", "TWAP_ICEBERG_ACCUMULATE", "IMMEDIATE_CROSS_SPREAD"
    private BigDecimal estimatedSlippageBps; // Basis points (1 bps = 0.01%)

    private String microstructureSignal; // "BULLISH_AGGRESSIVE_FLOW", "STEALTH_ACCUMULATION", "BALANCED", "BEARISH_PRESSURE", "DUMPING"
    private String institutionalVerdict;
}
