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
public class RealMoneyAuditDto {

    private boolean certifiedForRealMoney;      // Đã đủ điều kiện giao dịch tiền thật an toàn chưa
    private int readinessScorePercent;          // Điểm sẵn sàng (0 - 100%)
    private String executiveSummary;            // Đánh giá tổng quan từ hệ thống kiểm toán định lượng
    private BigDecimal currentNav;              // Giá trị tài sản ròng hiện tại (VND)
    private BigDecimal netProfitToDate;         // Tổng lợi nhuận ròng tích lũy (VND)
    private BigDecimal winRate;                 // Tỷ lệ thắng hiện tại (%)
    private BigDecimal profitFactor;            // Hệ số lợi nhuận (Gross Profit / Gross Loss)
    private BigDecimal mathematicalExpectancy;  // Kỳ vọng toán học kiếm tiền trên mỗi lệnh (VND)
    private BigDecimal maxDrawdownPercent;      // Mức sụt giảm tài sản lớn nhất (%)
    private List<AuditCriterion> criteria;      // Danh sách 7 tiêu chí kiểm định khắt khe
    private List<String> recommendedNextSteps;  // Các bước rèn luyện tiếp theo để tối ưu lợi nhuận

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditCriterion {
        private String name;                    // Tên tiêu chí kiểm định
        private String requiredThreshold;       // Ngưỡng yêu cầu tối thiểu
        private String actualValue;             // Giá trị thực tế đạt được
        private String status;                  // PASS, IN_PROGRESS, WARNING
        private String notes;                   // Ghi chú chi tiết
    }
}
