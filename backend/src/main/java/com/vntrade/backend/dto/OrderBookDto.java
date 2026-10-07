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
public class OrderBookDto {

    private String symbol;
    private BigDecimal currentPrice;
    private BigDecimal ceilingPrice;          // Giá trần (+7% HOSE)
    private BigDecimal floorPrice;            // Giá sàn (-7% HOSE)
    private BigDecimal referencePrice;        // Giá tham chiếu
    private long totalMatchedVolume;          // Tổng khối lượng khớp lệnh
    private BigDecimal estimatedSlippagePercent; // Độ trượt giá ước tính (%)
    private String liquidityGrade;            // Xếp hạng thanh khoản (AAA, AA, A, B)
    private List<OrderBookLevel> bidLevels;   // 3 mức giá dư mua tốt nhất
    private List<OrderBookLevel> askLevels;   // 3 mức giá dư bán tốt nhất

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderBookLevel {
        private int level;                    // 1, 2, 3
        private BigDecimal price;             // Mức giá
        private long volume;                  // Khối lượng chờ
    }
}
