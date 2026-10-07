package com.vntrade.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false, length = 50)
    private String alertType; // STOP_LOSS_WARNING, TAKE_PROFIT_TRIGGERED, BREAKOUT_SIGNAL, OVERSOLD_REVERSAL, VOLUME_SPIKE

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String message;

    @Column(precision = 15, scale = 2)
    private BigDecimal priceAtAlert;

    @Column(length = 20)
    @Builder.Default
    private String severity = "INFO"; // INFO, WARNING, CRITICAL

    @Builder.Default
    private boolean isRead = false;

    @Column(updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
