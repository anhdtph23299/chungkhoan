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
    private BigDecimal averagePrice;          // Giá bình quân gia quyền phiên (VWAP thật)
    private BigDecimal ceilingPrice;          // Giá trần (+7% HOSE)
    private BigDecimal floorPrice;            // Giá sàn (-7% HOSE)
    private BigDecimal referencePrice;        // Giá tham chiếu
    private long totalMatchedVolume;          // Tổng khối lượng khớp lệnh (CP)
    private long foreignBuyVolume;            // Khối ngoại mua trong phiên (CP)
    private long foreignSellVolume;           // Khối ngoại bán trong phiên (CP)
    private long foreignNetVolume;            // Khối ngoại mua/bán ròng (CP)
    private long foreignRoomRemaining;        // Room ngoại còn lại (CP)
    private boolean realOrderBookAvailable;   // True nếu có độ sâu sổ lệnh thật (trong/sau phiên)
    private String dataSource;                // 24HMONEY_REAL, PRE_MARKET_NO_DEPTH, FALLBACK_NO_DEPTH
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
