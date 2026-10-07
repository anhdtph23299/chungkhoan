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
public class StatisticalArbitragePairDto {
    private String pairName;
    private String stockA;
    private String stockB;
    private String sector;
    private BigDecimal stockAPrice;
    private BigDecimal stockBPrice;
    private BigDecimal hedgeRatioBeta;                 // Hệ số hồi quy đồng liên kết Engle-Granger (Beta)
    private BigDecimal cointegrationAdfPValue;         // P-value kiểm định tính dừng ADF (P < 0.05 là đồng liên kết)
    private BigDecimal currentSpread;                  // Chênh lệch spread hiện tại: ln(PriceA) - Beta * ln(PriceB)
    private BigDecimal spreadMean;                     // Kỳ vọng trung bình lịch sử của spread
    private BigDecimal spreadStdDev;                   // Độ lệch chuẩn của spread
    private BigDecimal spreadZScore;                   // Điểm Z-Score độ lệch chuẩn hiện tại
    private BigDecimal halfLifeDays;                   // Chu kỳ hồi quy về trung bình Ornstein-Uhlenbeck (phiên)
    private String arbitrageSignal;                    // "BUY_A_ROTATE_FROM_B", "BUY_B_ROTATE_FROM_A", "TAKE_PROFIT_CONVERGENCE", "NEUTRAL_BALANCED"
    private BigDecimal expectedNetEdgePercent;         // Lợi nhuận chênh lệch kỳ vọng sau khi trừ 0.40% thuế phí
    private boolean t25RotationFeasible;               // Khả thi thực thi theo chu kỳ thanh toán T+2.5
    private String institutionalPairVerdict;           // Khuyến nghị định lượng của quỹ
}
