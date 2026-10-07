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
public class VcpPatternDto {
    private String symbol;
    private boolean isVcpForming;           // Đang hình thành mẫu hình VCP
    private int numberOfContractions;       // Số lần thu hẹp biến độ (2T, 3T hoặc 4T)
    private List<BigDecimal> contractionPercents; // % thu hẹp của từng đợt (VD: -18%, -9%, -4%)
    private boolean volumeDryUpConfirmed;   // Khối lượng cạn kiệt ở đợt co hẹp cuối cùng
    private BigDecimal currentPrice;
    private BigDecimal pivotPrice;          // Điểm nổ Pivot tối ưu để mua
    private BigDecimal suggestedStopLoss;   // Điểm cắt lỗ chặt (ngay dưới đáy đợt thu hẹp cuối)
    private BigDecimal targetPrice;         // Mục tiêu lợi nhuận (+20% - +35%)
    private BigDecimal riskRewardRatio;     // Tỷ lệ R:R (thường từ 3.0 đến 5.0)
    private String patternStatus;           // READY_FOR_BREAKOUT, IN_BASE, BREAKOUT_TRIGGERED
    private String analysisNote;            // Phân tích hành động giá & khối lượng
}
