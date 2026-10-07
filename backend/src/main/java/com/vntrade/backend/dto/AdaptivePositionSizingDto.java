package com.vntrade.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO cho Adaptive Position Sizing Service.
 * Kết hợp Kelly Criterion + ATR Volatility Targeting + Regime Multiplier
 * để tính kích thước vị thế tối ưu thích nghi với điều kiện thị trường.
 */
@Data
@Builder
public class AdaptivePositionSizingDto {

    // ===== INPUT CONTEXT =====
    private String symbol;
    private BigDecimal accountCapital;         // Tổng vốn NAV (VND)
    private BigDecimal currentPrice;           // Giá hiện tại
    private String currentRegime;             // Regime thị trường (BULL/BEAR/SIDEWAYS)

    // ===== ATR-BASED VOLATILITY TARGETING =====
    private BigDecimal atr14;                  // ATR 14 ngày
    private BigDecimal atrPercent;             // ATR / Price (%)
    private BigDecimal targetDailyVolatility;  // Target daily vol (e.g. 1.5% NAV)
    private BigDecimal atrBasedShares;         // Shares theo ATR sizing
    private BigDecimal atrBasedAllocation;     // Tiền theo ATR sizing
    private BigDecimal atrBasedPercent;        // % NAV theo ATR

    // ===== KELLY CRITERION COMPONENT =====
    private BigDecimal kellyWinRate;           // Win rate từ backtest (%)
    private BigDecimal kellyPayoffRatio;       // Payoff ratio (R:R)
    private BigDecimal fullKellyPercent;       // Full Kelly %
    private BigDecimal halfKellyPercent;       // Half Kelly (an toàn hơn)

    // ===== REGIME MULTIPLIER =====
    private BigDecimal regimeMultiplier;       // Hệ số nhân theo regime (0.0-1.0)
    private String regimeJustification;       // Lý do hệ số nhân

    // ===== VOLATILITY REGIME ADJUSTMENT =====
    private BigDecimal volatilityPercentile;   // Percentile volatility hiện tại vs lịch sử (%)
    private BigDecimal volatilityScaleFactor;  // Hệ số scale theo volatility
    private String volatilityEnvironment;      // LOW / NORMAL / ELEVATED / EXTREME

    // ===== COMBINED ADAPTIVE SIZING =====
    private BigDecimal adaptiveAllocationPercent; // % NAV sau tất cả điều chỉnh
    private BigDecimal adaptiveAllocationMoney;   // Tiền VND
    private int adaptiveSharesToBuy;              // Số lượng cổ phiếu (làm tròn lô 100)
    private BigDecimal actualAllocationPercent;   // % thực tế sau làm tròn lô

    // ===== RISK METRICS =====
    private BigDecimal stopLossPrice;          // Giá stop loss (1.5x ATR below)
    private BigDecimal stopLossPercent;        // Stop loss %
    private BigDecimal takeProfitPrice;        // Giá take profit
    private BigDecimal takeProfitPercent;      // Take profit %
    private BigDecimal maxLossIfStopHit;       // Tiền mất nếu chạm stop
    private BigDecimal riskRewardRatio;        // R:R ratio

    // ===== POSITION SIZING COMPARISON =====
    private BigDecimal fixedPercent5Pct;       // Sizing nếu dùng fixed 5%
    private BigDecimal kellyOnlyPercent;       // Sizing nếu chỉ dùng Kelly
    private BigDecimal atrOnlyPercent;         // Sizing nếu chỉ dùng ATR
    private BigDecimal adaptiveAdvantage;      // Ưu thế so với fixed sizing (%)

    // ===== VERDICT =====
    private String sizingVerdict;              // Kết luận tổng hợp
    private List<String> sizingFactors;        // Các yếu tố ảnh hưởng sizing
    private String riskWarning;                // Cảnh báo rủi ro nếu có
}
