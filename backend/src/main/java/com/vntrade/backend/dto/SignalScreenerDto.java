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
public class SignalScreenerDto {

    private String symbol;
    private String companyName;
    private String sector;
    private int compositeScore;               // Điểm định lượng (0 - 100)
    private String recommendation;            // MUA MẠNH, MUA, NẮM GIỮ, QUAN SÁT
    private BigDecimal currentPrice;          // Thị giá hiện tại
    private BigDecimal entryPrice;            // Vùng giá mở mua tối ưu
    private BigDecimal stopLossPrice;         // Mức cắt lỗ kỷ luật (-7%)
    private BigDecimal target1DailyProfit;    // Mục tiêu 1: Gặt hái 50% tiền mặt (+10%)
    private BigDecimal target2TrendRide;      // Mục tiêu 2: Chốt toàn bộ vị thế (+18%)
    private double riskRewardRatio;           // Tỷ lệ Lợi nhuận / Rủi ro (R:R)
    private double winProbabilityPercent;     // Xác suất thắng định lượng (%)
    private String technicalSetup;            // Mẫu hình kỹ thuật (Breakout nền giá, Pocket Pivot, MA Pullback)
    private String tradeThesis;               // Luận điểm đầu tư & kiếm tiền
}
