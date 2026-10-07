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
public class DeflatedSharpeAuditDto {
    private String symbol;
    private String strategyName;
    private int sampleCandles;
    private BigDecimal standardSharpeRatio;
    private BigDecimal deflatedSharpeRatio;            // Tỷ số Sharpe đã hiệu chỉnh chống Overfitting (DSR)
    private BigDecimal probabilityOfBacktestOverfittingPercent; // PBO: Xác suất kết quả backtest đến từ ngẫu nhiên / data-mining bias (%)
    private int minimumTrackRecordLengthDays;          // MinTRL: Số phiên giao dịch tối thiểu cần thiết để đạt mức tin cậy 95%
    private BigDecimal returnsSkewness;                 // Độ bất đối xứng của phân phối lợi nhuận
    private BigDecimal returnsKurtosis;                 // Độ nhọn phân phối lợi nhuận (Fat Tails)
    private int independentTrialsCount;                // Số lần thử nghiệm tối ưu hóa tham số (Multiple testing trials)
    private String statisticalConfidenceGrade;         // "STATISTICALLY_SIGNIFICANT_EDGE", "BORDERLINE_EDGE", "OVERFITTED_LUCK_SUSPECTED"
    private String institutionalAuditSummary;
}
