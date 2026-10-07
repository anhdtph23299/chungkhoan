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
public class VeteranDisciplineAuditDto {
    private String systemName;
    private int disciplineScore; // 0 - 100
    private String overallVerdict;
    private List<DisciplineRuleCheck> ruleChecks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisciplineRuleCheck {
        private String ruleName;             // Tên nguyên tắc vàng già làng
        private String sourceOrigin;         // Nguồn gốc (O'Neil CANSLIM, Minervini VCP, Wyckoff VSA, Kinh nghiệm TTCK VN)
        private boolean compliant;           // Đã tuân thủ nghiêm ngặt hay chưa
        private String statusText;           // ĐẠT CHUẨN / VI PHẠM
        private String explanation;          // Giải thích cơ chế bảo vệ vốn
        private String quantitativeMetric;   // Thông số định lượng thực tế
    }
}
