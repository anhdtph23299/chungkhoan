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
public class StressTestReportDto {
    private String systemName;
    private BigDecimal currentNav;
    private List<CrisisScenarioResult> scenarioResults;
    private String executiveStressVerdict;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrisisScenarioResult {
        private String scenarioName;           // Tên cuộc khủng hoảng lịch sử
        private String timePeriod;             // Thời gian xảy ra
        private BigDecimal marketDropPercent;   // Mức giảm của VN-Index (%)
        private BigDecimal buyAndHoldLossPercent; // Mức lỗ nếu ôm gồng như F0 (%)
        private BigDecimal botProtectedLossPercent; // Mức lỗ thực tế nhờ cơ chế phòng thủ của Bot (%)
        private BigDecimal capitalPreservedPercent; // % tài sản được bảo toàn an toàn
        private String defenseMechanismTriggered;  // Cơ chế đã kích hoạt (SL 7% + Cầu chì DEFCON-1)
        private String recoveryOutcome;            // Kết quả sau khủng hoảng
    }
}
