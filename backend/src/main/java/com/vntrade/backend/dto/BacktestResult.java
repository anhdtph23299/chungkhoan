package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestResult {
    private String symbol;
    private String strategy;
    private BigDecimal initialCapital;
    private BigDecimal finalCapital;
    private BigDecimal netProfit;
    private BigDecimal totalReturnPercent;
    private Integer totalTrades;
    private Integer winningTrades;
    private Integer losingTrades;
    private BigDecimal winRate;
    private BigDecimal profitFactor;
    private BigDecimal maxDrawdownPercent;
    private BigDecimal sharpeRatio;
    private List<BacktestTrade> tradesHistory;
    private String conclusion;
}
