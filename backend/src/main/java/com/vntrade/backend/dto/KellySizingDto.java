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
public class KellySizingDto {
    private String symbol;
    private BigDecimal accountCapital;
    private BigDecimal winRatePercent;        // Tỷ lệ thắng p (%)
    private BigDecimal lossRatePercent;       // Tỷ lệ thua q (%)
    private BigDecimal winLossPayoffRatio;    // Tỷ số Lãi/Lỗ b (R:R)

    // Tỷ lệ Kelly tính toán
    private BigDecimal fullKellyPercent;       // Full Kelly (%)
    private BigDecimal halfKellyPercent;       // Half-Kelly tối ưu an toàn (%)
    private BigDecimal quarterKellyPercent;    // Quarter-Kelly phòng thủ (%)

    // Phân bổ giải ngân thực tế (Áp dụng Half-Kelly chuẩn quỹ)
    private BigDecimal recommendedAllocationMoney; // Số tiền giải ngân tối ưu (VND)
    private int recommendedSharesToBuy;           // Số cổ phiếu làm tròn lô 100
    private BigDecimal recommendedAllocationPercent; // % NAV thực tế giải ngân
    private BigDecimal probabilityOfRuin;         // Xác suất cháy tài khoản (0.000%)
    private String mathematicalVerdict;          // Lời bình toán học
}
