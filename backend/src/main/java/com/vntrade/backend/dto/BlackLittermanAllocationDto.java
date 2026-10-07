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
public class BlackLittermanAllocationDto {
    private String modelName;
    private BigDecimal portfolioNav;
    private String benchmarkName;
    private BigDecimal marketRiskAversionDelta;       // Hệ số ngại rủi ro thị trường (Delta ~ 2.5)
    private BigDecimal tauParameter;                  // Độ bất định của ma trận cân bằng (Tau ~ 0.05)
    private BigDecimal activeSharePercent;            // Mức độ phân bổ tích cực (Active Share vs VN30 %)
    private BigDecimal expectedPosteriorReturnPercent;// Lợi nhuận kỳ vọng hậu nghiệm Black-Litterman (%)
    private BigDecimal expectedPosteriorVolatilityPercent; // Biến động danh mục kỳ vọng hậu nghiệm (%)
    private BigDecimal posteriorSharpeRatio;          // Tỷ số Sharpe danh mục tối ưu Black-Litterman
    private List<String> quantitativeAlphaViews;      // Các nhận định định lượng (P, Q, Omega)
    private List<BlackLittermanAssetItem> assetAllocations; // Chi tiết phân bổ tài sản tối ưu
    private String institutionalAuditSummary;         // Kết luận kiểm định phân bổ danh mục

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlackLittermanAssetItem {
        private String symbol;
        private String sector;
        private BigDecimal marketCapWeightPercent;        // Trọng số vốn hóa cân bằng thị trường (Benchmark Weight)
        private BigDecimal impliedEquilibriumReturnPercent;// Lợi nhuận cân bằng ngầm định (Implied Return Pi)
        private BigDecimal quantitativeViewTiltPercent;   // Mức độ nghiêng vị thế theo Alpha (View Tilt)
        private BigDecimal optimalBlackLittermanWeightPercent; // Trọng số tối ưu hậu nghiệm (Black-Litterman Weight)
        private BigDecimal allocatedMoneyVnd;             // Giá trị phân bổ tiền mặt (VND)
        private int targetSharesLot100;                   // Số lượng cổ phiếu làm tròn lô 100 cp
        private String t25LiquidityStatus;                // Trạng thái thanh khoản T+2.5
    }
}
