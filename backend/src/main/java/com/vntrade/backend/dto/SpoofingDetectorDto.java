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
public class SpoofingDetectorDto {
    private String symbol;
    private BigDecimal currentPrice;
    private int spoofingRiskScore;                       // Điểm nguy cơ thao túng kê lệnh ảo (0 - 100)
    private String layeringPattern;                      // "PHANTOM_BID_SUPPORT_LAYER", "PHANTOM_ASK_RESISTANCE_LAYER", "GENUINE_ORGANIC_DEPTH"
    private boolean isSafeToBuy;                         // Cờ an toàn cho bot tự động (false nếu phát hiện Bull Trap kê mua ảo)
    private BigDecimal level3ToTotalRatio;               // Tỷ lệ khối lượng tầng 3 so với tổng book (%)
    private Long suspectedPhantomVolume;                 // Khối lượng lệnh ảo bị nghi vấn
    private BigDecimal suspectedPhantomPrice;            // Mức giá kê lệnh ảo
    private BigDecimal cancelHazardProbability;          // Xác suất hủy lệnh bất ngờ trước giờ khớp (%)
    private BigDecimal microPriceDislocationPercent;     // Độ lệch giá vi mô so với mid-price (%)
    private List<DepthLevelInfo> bidLevels;              // Chi tiết 3 tầng giá mua
    private List<DepthLevelInfo> askLevels;              // Chi tiết 3 tầng giá bán
    private String institutionalActionVerdict;           // Khuyến nghị hành động đối phó thao túng

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepthLevelInfo {
        private int level;
        private BigDecimal price;
        private Long volume;
        private BigDecimal proportionPercent;
    }
}
