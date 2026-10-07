package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeAnalytics {
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private BigDecimal winRate;                 // Tỷ lệ thắng (%)
    private BigDecimal totalGrossProfit;        // Tổng tiền lãi (VND)
    private BigDecimal totalGrossLoss;          // Tổng tiền lỗ (VND)
    private BigDecimal netProfit;               // Lợi nhuận ròng sau thuế phí (VND)
    private BigDecimal profitFactor;            // Hệ số Lãi / Lỗ (> 2.0 là xuất sắc)
    private BigDecimal averageWin;              // Lãi trung bình mỗi lệnh thắng (VND)
    private BigDecimal averageWinPercent;       // % Lãi trung bình
    private BigDecimal averageLoss;             // Lỗ trung bình mỗi lệnh thua (VND)
    private BigDecimal averageLossPercent;      // % Lỗ trung bình (cần khống chế < 7%)
    private BigDecimal winLossRatio;            // Tỷ lệ Lãi TB / Lỗ TB
    private BigDecimal mathematicalExpectancy;  // Kỳ vọng toán học trên mỗi lệnh (VND)
    private boolean isReadyForRealMoney;        // Đã đủ điều kiện chơi tiền thật chưa?
    private String readinessMessage;            // Lời khuyên chuyển sang tiền thật
    private BigDecimal largestWin;              // Lệnh lãi lớn nhất (VND)
    private BigDecimal largestLoss;             // Lệnh lỗ lớn nhất (VND)
    private BigDecimal averageMfePercent;       // MFE trung bình (% lãi đỉnh chạm tới)
    private BigDecimal averageMaePercent;       // MAE trung bình (% drawdown sâu nhất chịu đựng)
    private BigDecimal exitEfficiencyPercent;   // Hiệu suất chốt lời đỉnh (% Realized / MFE)
    private Map<String, Integer> strategyCounts;
}
