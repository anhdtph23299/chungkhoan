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
public class FuturesBotConfigDto {
    private Boolean autoTrading;             // Bật/tắt chế độ bot tự động bắn lệnh phái sinh
    private String mode;                     // SIMULATION, LIVE_PAPER, REAL_BROKER
    private BigDecimal capital;              // Vốn phân bổ cho phái sinh (ví dụ 100,000,000 đ)
    private Integer maxContracts;            // Tối đa số HĐ mở cùng lúc (ví dụ 2 hoặc 3 HĐ)
    private BigDecimal stopLossPoints;       // Biên độ cắt lỗ (mặc định 2.5 điểm)
    private BigDecimal takeProfitPoints;     // Biên độ chốt lời (mặc định 5.0 điểm)
    private BigDecimal trailingStopTrigger;  // Khi lãi đạt mức này (ví dụ 3.0 điểm) -> kích hoạt Trailing Stop
    private BigDecimal trailingStopDistance; // Khoảng cách bám sau giá (ví dụ 1.5 điểm)
    private Boolean closeBeforeAtc;          // Tự động tất toán lúc 14:25 để không ôm qua đêm
    private String preferredStrategy;        // HYBRID_ALPHA, MOMENTUM_TREND, BASIS_REVERSION
    private BigDecimal todayRealizedPnlVnd;  // Tổng lãi/lỗ thực tế trong ngày
    private BigDecimal todayPnlPoints;       // Tổng điểm lãi/lỗ trong ngày
    private Integer todayTradesCount;        // Số lượt giao dịch trong ngày
}
