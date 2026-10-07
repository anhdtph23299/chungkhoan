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
public class PerformanceAttributionDto {
    private String modelName;                       // Brinson-Fachler Institutional Performance Attribution
    private BigDecimal portfolioTotalReturnPercent;  // Lợi nhuận tổng danh mục bot (% NAV)
    private BigDecimal benchmarkReturnPercent;       // Lợi nhuận chỉ số đối chuẩn VN-Index (%)
    private BigDecimal totalActiveReturnPercent;     // Lợi nhuận chủ động (Active Return = Rp - Rb)
    private BigDecimal allocationEffectPercent;      // Hiệu ứng phân bổ ngành (Sector Allocation Effect)
    private BigDecimal selectionEffectPercent;       // Hiệu ứng chọn mã cổ phiếu (Stock Selection Effect)
    private BigDecimal interactionEffectPercent;     // Hiệu ứng tương tác (Interaction Effect)
    private BigDecimal trackingErrorPercent;         // Sai số bám đuổi (Tracking Error)
    private BigDecimal informationRatio;             // Tỷ số Thông tin (Information Ratio = Active / TE)
    private List<SectorAttributionBreakdown> sectorBreakdowns; // Chi tiết đóng góp từng ngành
    private String institutionalAuditVerdict;        // Nhận định kiểm toán hiệu suất của quỹ

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorAttributionBreakdown {
        private String sectorName;
        private BigDecimal portfolioWeightPercent;
        private BigDecimal benchmarkWeightPercent;
        private BigDecimal portfolioReturnPercent;
        private BigDecimal benchmarkReturnPercent;
        private BigDecimal sectorContributionPercent;
    }
}
