package com.vntrade.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Column(precision = 19, scale = 2)
    private BigDecimal cashBalance;

    @Column(precision = 19, scale = 2)
    private BigDecimal investedValue;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalNav;

    @Column(precision = 19, scale = 2)
    private BigDecimal realizedPnl;

    @Column(precision = 19, scale = 2)
    private BigDecimal unrealizedPnl;

    @Column(precision = 8, scale = 4)
    private BigDecimal totalProfitPercent;

    @Column(precision = 8, scale = 2)
    private BigDecimal winRate;

    private Integer totalTrades;

    private Integer openPositionsCount;
}
