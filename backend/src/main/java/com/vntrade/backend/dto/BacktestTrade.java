package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestTrade {
    private LocalDate entryDate;
    private BigDecimal entryPrice;
    private LocalDate exitDate;
    private BigDecimal exitPrice;
    private Integer quantity;
    private BigDecimal pnl;
    private BigDecimal pnlPercent;
    private String exitReason; // STOP_LOSS, TAKE_PROFIT, END_OF_DATA
}
