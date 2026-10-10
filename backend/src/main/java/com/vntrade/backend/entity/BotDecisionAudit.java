package com.vntrade.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bot_decision_audits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotDecisionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false, length = 15)
    private String symbol;

    @Column(nullable = false, length = 30)
    private String decision; // "BUY_EXECUTED", "REJECTED_FILTER", "HOLDING_EXISTS"

    @Column(precision = 15, scale = 2)
    private BigDecimal priceAtDecision;

    @Column(length = 60)
    private String rejectedByFilter; // "CANSLIM", "CONFLUENCE_MTF", "RRG_LAGGING", "DEFCON_CRASH", "OBI_ASK_WALL", "FII_FLOW_TRAP", "SPOOFING", "KALMAN_VELOCITY", "ADAPTIVE_SIZING", "LIQUIDITY_TRAP", "DATA_STALE"

    @Column(length = 1000)
    private String rejectionReason;

    private Integer confidenceScore;

    @Column(precision = 15, scale = 2)
    private BigDecimal priceAfter5Sessions;

    @Column(precision = 15, scale = 2)
    private BigDecimal priceAfter10Sessions;

    @Column(precision = 8, scale = 2)
    private BigDecimal return5SessionsPct;

    @Column(precision = 8, scale = 2)
    private BigDecimal return10SessionsPct;

    @Column(length = 40)
    private String auditVerdict; // "PENDING", "CORRECT_REJECTION_SAVED_CAPITAL", "MISSED_OPPORTUNITY_FALSE_NEGATIVE"
}
