package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntradayVwapTwapExecutionDto {
    private String symbol;
    private String orderSide;                          // "BUY" hoặc "SELL"
    private int totalOrderShares;
    private BigDecimal currentMarketPrice;
    private String benchmarkExecutionAlgo;              // "VWAP_VOLUME_WEIGHTED", "TWAP_TIME_WEIGHTED", "POV_PERCENT_OF_VOLUME"
    private BigDecimal expectedVwapPrice;              // Giá khớp kỳ vọng theo đường cong khối lượng VWAP
    private BigDecimal expectedTwapPrice;              // Giá khớp kỳ vọng theo thời gian phẳng TWAP
    private BigDecimal estimatedCostSavingsVnd;        // Số tiền tiết kiệm được so với lệnh MP thị trường thô bạo (VND)
    private BigDecimal slippageSavingsBps;             // Mức trượt giá tiết kiệm được (bps)
    private String t25MandatorySettlementDeadline;     // Mốc thời gian hoàn tất chuyển giao cổ phiếu T+2.5 (ví dụ: Thứ Tư 13:00)
    private List<IntradayTrancheSchedule> trancheSchedules; // Lịch trình chẻ lệnh chi tiết
    private String institutionalComplianceVerdict;    // Kết luận tuân thủ pháp lý và tối ưu chi phí

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntradayTrancheSchedule {
        private int trancheNumber;
        private String timeInterval;                   // 09:15 - 09:45, 09:45 - 10:45,...
        private String marketSessionPhase;             // "MORNING_OPENING_RUSH", "MID_DAY_ACCUMULATION", "T25_ABSORPTION", "ATC_CLOSING"
        private BigDecimal historicalVolumeWeightPct;  // Trọng số thanh khoản lịch sử theo đường cong nụ cười U-shape (%)
        private int recommendedTrancheShares;          // Số lượng cổ phiếu phân bổ (lô 100 cp)
        private BigDecimal targetLimitPrice;           // Giá Limit tương ứng bước giá sàn HOSE
        private BigDecimal trancheTotalValueVnd;       // Giá trị đợt gom (VND)
        private String executionTactic;                // Chiến thuật vi mô (Stealth iceberg, Passive limit, ATC harvest)
    }
}
