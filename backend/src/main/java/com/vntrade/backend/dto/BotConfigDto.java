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
public class BotConfigDto {

    private boolean isRunning;
    private String mode;                                // SIMULATION, LIVE_PAPER_MONEY, REAL_READY
    private BigDecimal accountCapital;                  // Vốn tài khoản (200.000.000 đ)
    private BigDecimal dailyProfitTarget;               // Mục tiêu lãi ngày (1.500.000 - 3.000.000 đ)
    private BigDecimal dailyMaxLossLimit;               // Cầu chì dừng lỗ ngày (4.000.000 đ)
    private BigDecimal maxRiskPerTradePercent;          // Rủi ro mỗi lệnh (1.5% - 2.0% NAV)
    private BigDecimal stopLossPercent;                 // Mức cắt lỗ chuẩn (-7.0%)
    private BigDecimal partialProfitThresholdPercent;   // Ngưỡng gặt hái 50% tiền mặt (+10.0%)
    private BigDecimal fullTakeProfitPercent;           // Ngưỡng chốt lời toàn phần (+15.0%)
    private BigDecimal trailingStopThresholdPercent;    // Ngưỡng dời Stop Loss về hòa vốn (+7.0%)
    private BigDecimal sectorCapPercent;                // Trần tỷ trọng mỗi ngành (35.0%)
    private int maxConcurrentPositions;                 // Tối đa số mã nắm giữ đồng thời (4 mã)
    private int scanIntervalSeconds;                    // Chu kỳ quét tín hiệu (30s)
}
