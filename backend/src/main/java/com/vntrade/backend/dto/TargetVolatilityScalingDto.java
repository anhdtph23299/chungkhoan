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
public class TargetVolatilityScalingDto {
    private BigDecimal portfolioNav;
    private BigDecimal targetAnnualizedVolatilityPercent;  // Mục tiêu biến động năm của quỹ (thường 12% - 15%)
    private BigDecimal currentRealizedVolatilityPercent; // Biến động thực tế 20 phiên gần nhất (Annualized)
    private BigDecimal optimalEquityWeightPercent;       // Tỷ trọng cổ phiếu mục tiêu: w* = min(100%, vol_target / vol_realized)
    private BigDecimal optimalCashWeightPercent;         // Tỷ trọng tiền mặt mục tiêu: 100% - w*
    private BigDecimal targetEquityAmount;               // Giá trị cổ phiếu mục tiêu (VND)
    private BigDecimal targetCashAmount;                 // Giá trị tiền mặt mục tiêu (VND)
    private BigDecimal currentEquityAmount;              // Giá trị cổ phiếu hiện tại (VND)
    private BigDecimal currentCashAmount;                // Giá trị tiền mặt hiện tại (VND)
    private BigDecimal lockedT25EquityAmount;            // Giá trị cổ phiếu đang kẹt chu kỳ T+2.5 chưa về tài khoản
    private BigDecimal liquidEquityAmount;               // Giá trị cổ phiếu khả dụng có thể bán ngay
    private String rebalanceAction;                      // "SCALE_DOWN_DERISK", "SCALE_UP_ACCUMULATE", "HOLD_BALANCED"
    private BigDecimal rebalanceValueVnd;                // Số tiền cần mua/bán để đưa danh mục về mức biến động mục tiêu
    private boolean t25LiquidityConstraintActive;        // Cờ cảnh báo: Ràng buộc T+2.5 ngăn cản hạ tỷ trọng khẩn cấp
    private String institutionalVolTargetVerdict;        // Phân tích định lượng của Risk Manager
}
