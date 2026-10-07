package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KalmanFilterTrendDto {
    private String symbol;
    private BigDecimal marketPrice;
    private BigDecimal kalmanFilteredPrice;             // Giá lọc tối ưu không trễ (Zero-lag Kalman State)
    private BigDecimal priceVelocity;                  // Vận tốc xu hướng tức thời (VNĐ/phiên)
    private BigDecimal velocityPercent;                // Tỷ lệ phần trăm vận tốc xu hướng (%)
    private BigDecimal kalmanGain;                     // Hệ số khuếch đại thích nghi Kalman Gain (K)
    private BigDecimal measurementNoiseVariance;       // Phương sai nhiễu đo lường R
    private BigDecimal processNoiseVariance;           // Phương sai quá trình Q
    private String trendRegime;                        // Trạng thái xu hướng lọc nhiễu
    private String zeroLagSignal;                      // Tín hiệu đảo chiều sớm không độ trễ
    private BigDecimal sma20LagDifference;             // Chênh lệch giá giữa Kalman Filter vs SMA20 truyền thống (báo trễ)
    private String t25ExecutionVerdict;                // Khuyến nghị quỹ định lượng theo chu kỳ T+2.5
    private List<KalmanDataPoint> historicalPoints;    // Chuỗi điểm so sánh giá thực, Kalman và vận tốc

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KalmanDataPoint {
        private LocalDate date;
        private BigDecimal actualClose;
        private BigDecimal kalmanEstimate;
        private BigDecimal velocity;
        private BigDecimal sma20Reference;
    }
}
