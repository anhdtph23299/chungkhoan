package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionSizingRequest {
    private String symbol;
    private BigDecimal accountCapital;   // Tổng vốn NAV (VND)
    private BigDecimal maxRiskPercent;   // % Rủi ro tối đa cho deal này (VD: 1.5 hoặc 2.0)
    private BigDecimal entryPrice;       // Giá mua dự kiến
    private BigDecimal stopLossPrice;    // Giá cắt lỗ
    private BigDecimal takeProfitPrice;  // Giá chốt lời
}
