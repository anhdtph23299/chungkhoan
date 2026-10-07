package com.vntrade.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSummary {
    private BigDecimal totalInvested;       // Tổng tiền đang đầu tư (mua chưa bán)
    private BigDecimal currentValue;        // Giá trị hiện tại (theo giá HT)
    private BigDecimal totalPnl;            // Tổng lãi/lỗ (cả mở và đóng)
    private BigDecimal totalPnlPercent;     // % lãi/lỗ
    private long openPositions;             // Số vị thế đang mở
    private long totalTrades;              // Tổng số giao dịch đã đóng
    private long winningTrades;            // Số lệnh thắng
    private BigDecimal winRate;             // Tỷ lệ thắng (%)
}
