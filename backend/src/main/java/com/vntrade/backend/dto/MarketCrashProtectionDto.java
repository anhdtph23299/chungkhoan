package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketCrashProtectionDto {
    private String defenseStatus;          // NORMAL_DEFENSE, CAUTION_DEFENSE, DEFCON_1_STORM_LOCKOUT
    private int defenseLevel;              // 0 (Bình thường), 1 (Cẩn trọng), 2 (Báo động đỏ chống bão)
    private BigDecimal vnIndexChangePoints;// Điểm số biến động VN-Index
    private BigDecimal vnIndexChangePercent;// % biến động VN-Index
    private int vn30FloorHitsCount;        // Số mã nằm sàn trong rổ VN30
    private boolean allowNewPurchases;     // Cho phép giải ngân mở vị thế mới hay không
    private BigDecimal maxAccountExposure; // Tỷ trọng giải ngân tối đa cho phép (% NAV)
    private String circuitBreakerMessage;  // Thông báo hành động khẩn cấp
    private String actionProtocol;         // Giao thức an toàn đang kích hoạt
}
