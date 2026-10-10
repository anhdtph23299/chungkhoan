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
public class FuturesQuoteDto {
    private String symbol;             // VN30F1M
    private BigDecimal currentPrice;   // Giá HĐTL hiện tại (làm tròn 0.1)
    private BigDecimal change;         // Tăng/giảm điểm
    private BigDecimal pctChange;      // % thay đổi
    private BigDecimal vn30IndexPrice; // Chỉ số cơ sở VN30
    private BigDecimal basis;          // Độ lệch Basis = Futures - VN30
    private String basisStatus;        // DƯƠNG_CAO, ÂM_SÂU, CÂN_BẰNG, HỘI_TỤ
    private Long volume;               // Khối lượng hợp đồng khớp trong phiên
    private Long openInterest;         // OI - Khối lượng vị thế mở qua đêm
    private String sessionTime;        // ATO, KHỚP_LỆNH_SÁNG, NGHỈ_TRƯA, KHỚP_LỆNH_CHIỀU, ATC, ĐÓNG_CỬA
    private LocalDateTime timestamp;
}
