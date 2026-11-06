package com.vntrade.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "futures_positions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuturesPosition {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "symbol", length = 32, nullable = false)
    private String symbol;

    @Column(name = "side", length = 16, nullable = false)
    private String side; // LONG hoặc SHORT

    @Column(name = "contracts", nullable = false)
    private Integer contracts;

    @Column(name = "entry_price", precision = 12, scale = 2)
    private BigDecimal entryPrice;

    @Column(name = "current_price", precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "close_price", precision = 12, scale = 2)
    private BigDecimal closePrice;

    @Column(name = "stop_loss_price", precision = 12, scale = 2)
    private BigDecimal stopLossPrice;

    @Column(name = "take_profit_price", precision = 12, scale = 2)
    private BigDecimal takeProfitPrice;

    @Column(name = "trailing_stop_price", precision = 12, scale = 2)
    private BigDecimal trailingStopPrice;

    @Column(name = "pnl_points", precision = 10, scale = 2)
    private BigDecimal pnlPoints;

    @Column(name = "gross_pnl_vnd", precision = 18, scale = 2)
    private BigDecimal grossPnlVnd;

    @Column(name = "fees_vnd", precision = 18, scale = 2)
    private BigDecimal feesVnd;

    @Column(name = "net_pnl_vnd", precision = 18, scale = 2)
    private BigDecimal netPnlVnd;

    @Column(name = "margin_used", precision = 18, scale = 2)
    private BigDecimal marginUsed;

    @Column(name = "status", length = 32, nullable = false)
    private String status; // OPEN, CLOSED

    @Column(name = "close_reason", length = 255)
    private String closeReason;

    @Column(name = "open_time")
    private LocalDateTime openTime;

    @Column(name = "close_time")
    private LocalDateTime closeTime;
}
