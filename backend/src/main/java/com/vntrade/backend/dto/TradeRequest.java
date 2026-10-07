package com.vntrade.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeRequest {
    private String symbol;
    private String exchange;
    private String type;
    private LocalDate tradeDate;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal fee;
    private String strategy;
    private BigDecimal stopLoss;
    private BigDecimal takeProfit;
    private String sector;
    private String reason;
    private String notes;

    // Khi đóng lệnh (bán)
    private LocalDate closeDate;
    private BigDecimal closePrice;
    private Integer closeQuantity;
}
