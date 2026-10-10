package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuturesSignalDto {
    private String symbol;               // VN30F1M
    private String action;               // LONG, SHORT, CLOSE_LONG, CLOSE_SHORT, STANDBY
    private BigDecimal currentPrice;     // Giá hiện tại
    private BigDecimal entryPrice;       // Giá kích hoạt lệnh
    private BigDecimal stopLossPrice;    // Giá cắt lỗ chặt chẽ (làm tròn 0.1)
    private BigDecimal takeProfit1;      // Mục tiêu chốt lời 1 (TP1 +3.5 đến +5.0 pts)
    private BigDecimal takeProfit2;      // Mục tiêu chốt lời 2 (TP2 +8.0 đến +15.0 pts)
    private BigDecimal trailingStopPoints; // Khoảng dời Trailing Stop (ví dụ 2.0 pts)
    private Integer confidenceScore;     // Độ tin cậy (0 - 100)
    private String strategyName;         // DUAL_MOMENTUM_TREND, BASIS_MEAN_REVERSION, PANIC_REVERSAL, HYBRID_ALPHA
    private String trendStatus;          // BULLISH_STRONG, BULLISH_MODERATE, SIDEWAYS, BEARISH_MODERATE, BEARISH_STRONG
    private String recommendationReason; // Phân tích định lượng chi tiết
    private Double rsi;                  // RSI(14) khung M5
    private Double macdHist;             // MACD Histogram
    private BigDecimal vwap;             // Khối lượng bình quân gia quyền intraday
    private BigDecimal basis;            // Độ lệch Basis hiện tại
    private LocalDateTime generatedAt;
}
