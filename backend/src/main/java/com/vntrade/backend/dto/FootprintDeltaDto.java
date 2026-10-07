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
public class FootprintDeltaDto {
    private String symbol;
    private long totalActiveBuyVolume;        // Khối lượng lệnh chủ động mua (Khớp giá dư bán)
    private long totalActiveSellVolume;       // Khối lượng lệnh chủ động bán (Khớp giá dư mua)
    private long netDeltaVolume;              // Net Delta = Active Buy - Active Sell
    private BigDecimal deltaVolumeRatio;      // Tỷ lệ Delta / Tổng khối lượng (%)
    private String orderFlowDivergence;       // BULLISH_ABSORPTION, BEARISH_EXHAUSTION, BALANCED
    private int whaleOrdersCount;             // Số lượng lệnh cá mập (> 50,000 cp)
    private BigDecimal whaleNetFlowMoney;     // Giá trị ròng của các lệnh cá mập (VND)
    private String institutionalActionSignal; // Tín hiệu hành vi dòng tiền tổ chức
    private int backtestConfidenceScore;      // Điểm số tin cậy cho Backtest (0 - 100)
    private List<WhaleOrderCluster> whaleClusters; // Danh sách chi tiết các lệnh gom/xả tay to
    private String footprintSummaryVerdict;   // Kết luận đọc vị dòng tiền từ Tape Reading

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WhaleOrderCluster {
        private String time;
        private String orderType;             // ACTIVE_BUY, ACTIVE_SELL
        private BigDecimal price;
        private int quantity;
        private BigDecimal totalValue;
        private String impact;                // AGGRESSIVE_LIFT, DUMP_ON_BID
    }
}
