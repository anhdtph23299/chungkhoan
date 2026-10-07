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
public class ForeignFlowRiskDto {
    private String symbol;
    private BigDecimal foreignBuyVolume;              // Khối lượng khối ngoại mua
    private BigDecimal foreignSellVolume;             // Khối lượng khối ngoại bán
    private BigDecimal foreignNetValueVnd;            // Giá trị ròng khối ngoại (VND)
    private BigDecimal foreignOwnershipPercent;       // Tỷ lệ sở hữu nước ngoài hiện tại (%)
    private BigDecimal foreignRoomRemainingPercent;   // Tỷ lệ Room ngoại còn lại (%)
    private int consecutiveNetSellDays;               // Số ngày bán ròng liên tiếp
    private int liquidityHealthScore;                 // Điểm số sức khỏe thanh khoản (0 - 100)
    private String slippageRiskIndex;                 // LOW, MODERATE, HIGH_LIQUIDITY_TRAP
    private String fiiFlowVerdict;                    // Tín hiệu hành động cho Bot
    private List<String> earlyWarningAlerts;          // Danh sách cảnh báo sớm
    private String institutionalRecommendation;       // Khuyến nghị định chế
}
