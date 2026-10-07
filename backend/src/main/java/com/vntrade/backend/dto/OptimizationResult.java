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
public class OptimizationResult {
    private String symbol;
    private String optimalStrategy;
    private Double optimalStopLossPercent;
    private Double optimalTakeProfitPercent;
    private BigDecimal maxNetProfit;
    private BigDecimal optimalWinRate;
    private BigDecimal optimalProfitFactor;
    private BigDecimal minDrawdownPercent;
    private String recommendation;
    private List<BacktestResult> topPerformingConfigurations;
}
