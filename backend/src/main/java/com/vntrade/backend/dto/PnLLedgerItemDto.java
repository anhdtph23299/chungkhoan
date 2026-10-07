package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PnLLedgerItemDto {
    private Long tradeId;
    private String symbol;
    private String exchange;
    private LocalDate entryDate;
    private LocalDate exitDate;
    private BigDecimal entryPrice;
    private BigDecimal exitPrice;
    private int quantity;
    private BigDecimal grossProfit;      // Lãi gộp
    private BigDecimal feeDeducted;      // Phí giao dịch 0.15%
    private BigDecimal taxDeducted;      // Thuế TNCN 0.10%
    private BigDecimal netProfit;        // Lãi ròng thực nhận về tài khoản
    private BigDecimal returnPercent;    // Tỷ suất sinh lời thực tế (%)
    private String strategy;
    private String executionType;        // FULL_CLOSE, PARTIAL_HARVEST_50, TRAILING_STOP
    private String statusMessage;
}
