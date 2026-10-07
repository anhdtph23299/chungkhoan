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
public class TradingRatiosDto {
    private BigDecimal sharpeRatio;        // Tỷ số Sharpe (> 1.5 là quỹ xuất sắc, > 2.0 là đẳng cấp thế giới)
    private BigDecimal sortinoRatio;       // Tỷ số Sortino (chỉ phạt rủi ro sụt giảm, triệt tiêu thiên vị lệnh lãi lớn)
    private BigDecimal calmarRatio;        // Tỷ số Calmar (Lợi nhuận ròng hàng năm / Max Drawdown)
    private BigDecimal maxDrawdownPercent; // Mức sụt giảm tài sản lớn nhất (%)
    private BigDecimal winLossRatio;       // Tỷ số Lãi TB / Lỗ TB
    private BigDecimal profitFactor;       // Tổng lãi / Tổng lỗ
    private BigDecimal winRate;            // Tỷ lệ thắng (%)
    private BigDecimal averageReturnPerTradePercent; // Lợi nhuận bình quân mỗi lệnh (%)
    private int totalTradesAnalyzed;       // Tổng số lệnh trong mẫu
    private String hedgeFundRating;        // Xếp hạng hiệu quả theo chuẩn quỹ định lượng (AAA / AA / A)
    private String analyticalSummary;      // Đánh giá tổng quan
}
