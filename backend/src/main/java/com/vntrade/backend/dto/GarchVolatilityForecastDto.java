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
public class GarchVolatilityForecastDto {
    private String symbol;
    private BigDecimal currentPrice;
    private BigDecimal historicalVolAnnualized;         // Độ biến động lịch sử tĩnh 60 phiên (%/năm)
    private BigDecimal conditionalVolCurrent;           // Độ biến động có điều kiện hiện tại sigma_t GARCH (%/năm)
    private BigDecimal omegaConstant;                   // Hằng số nền omega
    private BigDecimal alphaArch;                       // Hệ số nhạy với cú sốc giá gần nhất alpha
    private BigDecimal betaGarch;                       // Hệ số quán tính biến động quá khứ beta
    private BigDecimal persistenceAlphaPlusBeta;        // Độ bền vững biến động (alpha + beta < 1.0)
    private BigDecimal longRunVolAnnualized;            // Biến động cân bằng vô điều kiện dài hạn (%/năm)
    private BigDecimal forecastTPlus1VolAnnualized;     // Biến động dự báo phiên T+1 (%/năm)
    private BigDecimal forecastTPlus2VolAnnualized;     // Biến động dự báo phiên T+2 (%/năm)
    private BigDecimal forecastT25SettlementVolPercent; // Độ lệch rủi ro lũy kế đến khi cổ phiếu về tài khoản T+2.5 (%)
    private BigDecimal dynamicT25StopLossPercent;       // Ngưỡng cắt lỗ tối ưu co giãn theo GARCH T+2.5 (%)
    private String volatilityRegime;                    // "VOLATILITY_EXPANSION_SHOCK", "VOLATILITY_COMPRESSION_SQUEEZE", "MEAN_REVERTING_NORMAL"
    private String institutionalRiskAction;             // Khuyến nghị phân bổ vốn & kiểm soát rủi ro của quỹ
    private List<GarchHistoryPoint> historicalVolSeries;// Chuỗi biến động lịch sử và có điều kiện
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GarchHistoryPoint {
        private String date;
        private BigDecimal dailyReturnPercent;
        private BigDecimal conditionalVolPercent;
    }
}
