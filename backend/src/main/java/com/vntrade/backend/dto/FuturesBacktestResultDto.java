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
public class FuturesBacktestResultDto {
    private String symbol;                   // VN30F1M
    private String resolution;               // 5m
    private Integer testDays;
    private Integer totalTrades;
    private Integer winningTrades;
    private Integer losingTrades;
    private Double winRatePercent;
    private BigDecimal totalPnlPoints;       // Tổng điểm lãi/lỗ (+35.4 pts)
    private BigDecimal totalNetPnlVnd;       // Tổng tiền lãi sau trừ thuế phí (+3,540,000 đ)
    private BigDecimal maxDrawdownPoints;    // Mức sụt giảm tối đa theo điểm
    private BigDecimal maxDrawdownVnd;       // Sụt giảm tối đa theo tiền
    private Double profitFactor;             // Tỷ số Lãi gộp / Lỗ gộp
    private Double sharpeRatio;              // Tỷ số Sharpe định lượng
    private Integer longTrades;
    private Integer shortTrades;
    private Double avgPointsPerTrade;
    private List<FuturesTradeHistoryDto> recentTrades;
}
