package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestRequest {
    private String symbol;
    private String strategy; // BREAKOUT_VOL, MA_PULLBACK, RSI_OVERSOLD
    private BigDecimal initialCapital;
    private Integer candlesCount;
    private Double stopLossPercent;
    private Double takeProfitPercent;
    private Double riskPerTradePercent;
}
