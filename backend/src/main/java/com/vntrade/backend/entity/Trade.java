package com.vntrade.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;  // VNM, HPG, ...

    @Column(length = 10)
    private String exchange; // HOSE, HNX, UPCOM

    @Column(nullable = false, length = 4)
    private String type; // "buy" | "sell"

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    @Column(precision = 15, scale = 2)
    private BigDecimal fee;

    @Column(length = 50)
    private String strategy; // Swing Trade, Breakout, Pullback MA

    @Column(precision = 15, scale = 2)
    private BigDecimal stopLoss;

    @Column(precision = 15, scale = 2)
    private BigDecimal takeProfit;

    @Column(length = 50)
    private String sector;

    @Column(length = 500)
    private String reason; // Lý do vào lệnh

    @Column(length = 500)
    private String notes; // Ghi chú / bài học

    // Thông tin lệnh bán (nếu đã đóng vị thế)
    private LocalDate closeDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal closePrice;

    private Integer closeQuantity;

    @Column(length = 10)
    @Builder.Default
    private String status = "open"; // "open" | "closed"

    @Column(precision = 15, scale = 2)
    private BigDecimal pnl; // Lãi/lỗ thực tế (tính sau khi đóng)

    @Column(precision = 8, scale = 4)
    private BigDecimal pnlPercent;

    // Quản trị vị thế từng phần & Đo lường chất lượng lệnh (MFE / MAE)
    private Integer partialClosedQuantity;

    @Column(precision = 15, scale = 2)
    private BigDecimal partialClosePrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal partialRealizedPnl;

    @Column(precision = 8, scale = 4)
    private BigDecimal mfePercent; // Maximum Favorable Excursion (% lãi đỉnh)

    @Column(precision = 8, scale = 4)
    private BigDecimal maePercent; // Maximum Adverse Excursion (% lỗ sâu nhất)

    @Column(precision = 8, scale = 4)
    private BigDecimal efficiencyScore; // Hiệu suất chốt lời / MFE

    @Column(updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
