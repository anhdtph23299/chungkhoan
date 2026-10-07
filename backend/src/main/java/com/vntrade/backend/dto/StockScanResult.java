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
public class StockScanResult {
    private String symbol;
    private String name;
    private String exchange;
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private Long volume;
    private BigDecimal volumeRatio; // Tỷ lệ volume so với TB 20 phiên
    private BigDecimal rsi;
    private BigDecimal ma20;
    private BigDecimal ma50;
    private BigDecimal ma200;
    private String signalType;      // BREAKOUT_VOL, PULLBACK_MA20, RSI_OVERSOLD, GOLDEN_CROSS, WARNING_BEAR
    private String signalTitle;
    private String signalDescription;
    private String buyZone;
    private BigDecimal stopLoss;
    private BigDecimal targetPrice;
    private Integer confidenceScore; // Điểm tin cậy kỹ thuật (0 - 100)
    private String action;          // STRONG_BUY, BUY, WATCH, AVOID
}
