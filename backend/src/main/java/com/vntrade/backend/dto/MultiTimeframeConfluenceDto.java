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
public class MultiTimeframeConfluenceDto {
    private String symbol;
    private BigDecimal currentPrice;

    // Khung Tuần (W1) - Xu hướng vĩ mô (Macro Trend)
    private String weeklyTrend;          // STRONG_UPTREND, SIDEWAYS, DOWNTREND
    private boolean weeklyEmaAlignment;  // EMA20 > EMA50 tuần
    private BigDecimal weeklyRsi;

    // Khung Ngày (D1) - Cấu trúc giá & Dòng tiền (Structure & Flow)
    private String dailyStructure;       // VCP_BASE, BREAKOUT_PIVOT, PULLBACK_MA20, DISTRIBUTION
    private boolean dailyVolumeConfirmed;// Khối lượng bùng nổ vượt 1.5x MA20
    private BigDecimal dailyRsi;
    private BigDecimal dailyMacdHistogram;

    // Khung Giờ (H1) - Điểm vào tối ưu trong ngày (Execution Timing)
    private String hourlyTrigger;        // OVERSOLD_BOUNCE, BULLISH_CROSSOVER, EXTENDED
    private BigDecimal hourlyRsi;
    private BigDecimal hourlyOptimalEntryPrice; // Giá vào lệnh tối ưu nhất trong phiên

    // Đánh giá Tổng thể Hội tụ (Confluence Score)
    private int confluenceScore;         // 0 - 100
    private boolean tripleGreenAlignment;// Cả 3 khung W1-D1-H1 đều đồng thuận xanh
    private String recommendationVerdict;// Lời bình định lượng
    private String actionSignal;         // STRONG_BUY_EXECUTE, WAIT_FOR_HOURLY_PULLBACK, REJECT_UNALIGNED
}
