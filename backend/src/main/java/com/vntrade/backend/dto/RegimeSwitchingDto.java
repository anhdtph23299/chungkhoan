package com.vntrade.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO cho Hidden Markov Model Regime Switching Service.
 * Chứa xác suất chuyển trạng thái Markov, regime hiện tại và phân tích chi tiết.
 */
@Data
@Builder
public class RegimeSwitchingDto {

    // ===== REGIME STATE =====
    private String currentRegime;              // BULL / BEAR / SIDEWAYS
    private BigDecimal bullProbability;        // Xác suất đang ở chế độ BULL (0-1)
    private BigDecimal bearProbability;        // Xác suất đang ở chế độ BEAR (0-1)
    private BigDecimal sidewaysProbability;    // Xác suất đang ở chế độ SIDEWAYS (0-1)
    private String regimeConfidence;           // HIGH / MEDIUM / LOW

    // ===== HMM TRANSITION MATRIX =====
    private BigDecimal bullToBullProb;         // P(Bull -> Bull)
    private BigDecimal bullToBearProb;         // P(Bull -> Bear)
    private BigDecimal bullToSidewaysProb;     // P(Bull -> Sideways)
    private BigDecimal bearToBullProb;         // P(Bear -> Bull)
    private BigDecimal bearToBearProb;         // P(Bear -> Bear)
    private BigDecimal bearToSidewaysProb;     // P(Bear -> Sideways)
    private BigDecimal sidewaysToBullProb;     // P(Sideways -> Bull)
    private BigDecimal sidewaysToBearProb;     // P(Sideways -> Bear)
    private BigDecimal sidewaysToSidewaysProb; // P(Sideways -> Sideways)

    // ===== EMISSION PARAMETERS =====
    private BigDecimal observedReturnMean;     // Mean return hiện tại quan sát được
    private BigDecimal observedVolatility;     // Volatility hiện tại (20-ngày)
    private BigDecimal regimeReturnMean;       // Mean return ứng với regime hiện tại
    private BigDecimal regimeVolatility;       // Volatility ứng với regime hiện tại
    private BigDecimal logLikelihoodScore;     // Log-likelihood phù hợp model (âm)

    // ===== MARKET INDICATORS =====
    private BigDecimal vnIndexReturn20d;       // Return VN-Index 20 ngày
    private BigDecimal breadthIndicator;       // Breadth (% cp > MA20 trong VN30)
    private BigDecimal volumeRatioVsAvg;       // Volume / Average Volume
    private BigDecimal momentumScore;          // Score momentum tổng hợp (-1 đến +1)
    private BigDecimal trendStrengthAdx;       // ADX trend strength (0-100)

    // ===== FORWARD PROBABILITIES =====
    private BigDecimal probBullNext5Sessions;  // Xác suất regime Bull trong 5 phiên tới
    private BigDecimal probBearNext5Sessions;  // Xác suất regime Bear trong 5 phiên tới
    private BigDecimal expectedReturnNext5d;   // Expected return 5 ngày tới
    private BigDecimal expectedVolNext5d;      // Expected volatility 5 ngày tới

    // ===== TRADING RECOMMENDATIONS =====
    private BigDecimal recommendedExposure;    // Tỷ lệ giải ngân được đề xuất (0-100%)
    private String primaryStrategy;            // Chiến lược phù hợp regime hiện tại
    private List<String> regimeSignals;        // Các tín hiệu xác nhận regime
    private List<String> warningSignals;       // Các tín hiệu cảnh báo chuyển regime
    private Map<String, BigDecimal> sectorAllocation; // Phân bổ ngành theo regime

    // ===== VERDICT =====
    private String quantAnalystVerdict;        // Kết luận chi tiết của quant analyst
    private String regimeDurationEstimate;     // Ước tính thời gian duy trì regime
}
