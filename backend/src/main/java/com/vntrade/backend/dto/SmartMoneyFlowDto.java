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
public class SmartMoneyFlowDto {
    private String symbol;
    private BigDecimal currentPrice;
    private long totalVolume;
    private long buyActiveVolume;       // Khối lượng mua chủ động (lệnh MP khớp thẳng dư bán)
    private long sellActiveVolume;      // Khối lượng bán chủ động (lệnh MP táng thẳng dư mua)
    private BigDecimal buyActiveRatio;  // Tỷ lệ mua chủ động (%)
    private BigDecimal foreignNetBuy;   // Giá trị khối ngoại mua ròng (VND)
    private BigDecimal propNetBuy;      // Giá trị tự doanh CTCK mua ròng (VND)
    private int accumulationScore;      // Điểm số gom hàng cá mập (0 - 100)
    private String moneyFlowStatus;     // DÒNG TIỀN MẠNH (ACCUMULATION), BÌNH THƯỜNG (NEUTRAL), PHÂN PHỐI (DISTRIBUTION)
    private String institutionalVerdict;// Lời bình định lượng
}
