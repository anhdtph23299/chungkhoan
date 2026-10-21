package com.vntrade.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "signal_validation_trades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignalValidationTrade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false)
    private LocalDate signalDate; // Ngày phát tín hiệu

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal entryPrice; // Giá tại thời điểm tín hiệu

    @Column(precision = 15, scale = 2)
    private BigDecimal stopLoss;

    @Column(precision = 15, scale = 2)
    private BigDecimal takeProfit;

    @Column(nullable = false)
    private Integer confidenceScore; // Điểm tín hiệu 0-100

    @Column(length = 250)
    private String signalReason; // Lý do tín hiệu

    // Tracking outcome
    @Column(length = 20)
    @Builder.Default
    private String outcome = "PENDING"; // PENDING, WIN, LOSS, STOPPED_OUT, TIMEOUT

    @Column(precision = 8, scale = 4)
    private BigDecimal currentPnlPercent; // PnL % hiện tại

    @Column(precision = 15, scale = 2)
    private BigDecimal currentPrice; // Giá hiện tại

    @Column(precision = 8, scale = 4)
    private BigDecimal mfePercent; // Đỉnh lãi cao nhất

    @Column(precision = 8, scale = 4)
    private BigDecimal maePercent; // Đáy lỗ sâu nhất

    private Integer daysTracked; // Số ngày đã theo dõi

    private LocalDate closedDate; // Ngày kết thúc tracking

    @Column(updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
