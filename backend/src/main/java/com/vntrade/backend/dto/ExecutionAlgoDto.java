package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionAlgoDto {
    private String symbol;
    private int totalOrderQuantity;     // Tổng số lượng muốn mua (VD: 20.000 cp)
    private BigDecimal marketPrice;      // Giá thị trường hiện tại
    private String algorithmType;        // VWAP_SMART_SLICING, TWAP_STEALTH, ICEBERG
    private BigDecimal estimatedSlippageNoAlgo;   // Trượt giá nếu mua MP một cục (VD: +1.45%)
    private BigDecimal estimatedSlippageWithAlgo; // Trượt giá khi dùng thuật toán chẻ lệnh (chỉ +0.08%)
    private BigDecimal estimatedCostSavings;      // Số tiền tiết kiệm được nhờ chẻ lệnh (VND)
    private List<OrderTranche> scheduledTranches; // Lịch trình các lệnh nhỏ được phân bổ
    private String executionStrategyNote;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderTranche {
        private int trancheIndex;
        private String timeWindow;      // Khung giờ thực hiện (VD: 09:15 - 09:45)
        private int trancheQuantity;    // Số lượng cổ phiếu khớp trong đợt
        private BigDecimal targetPriceLimit; // Giá trần tối đa cho phép khớp
        private BigDecimal volumeWeightPercent; // Tỷ trọng khối lượng (%)
        private String stealthReason;   // Mục đích (Tránh lộ lệnh, Hấp thụ dư bán, Đón sóng ATC)
    }
}
