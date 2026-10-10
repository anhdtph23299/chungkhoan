package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuturesTradeHistoryDto {
    private String tradeId;
    private String side;                 // LONG hoặc SHORT
    private Integer contracts;
    private BigDecimal entryPrice;
    private BigDecimal exitPrice;
    private BigDecimal pnlPoints;
    private BigDecimal netPnlVnd;
    private String exitReason;           // TP, SL, TRAILING_STOP, EOD
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
    private Integer durationMinutes;
}
