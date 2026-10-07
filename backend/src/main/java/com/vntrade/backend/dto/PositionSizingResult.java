package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionSizingResult {
    private String symbol;
    private BigDecimal maxRiskAmount;          // Tiền rủi ro tối đa cho phép mất (VND)
    private BigDecimal riskPerShare;            // Rủi ro trên 1 cổ phiếu (Entry - SL)
    private BigDecimal profitPerShare;          // Lợi nhuận trên 1 cổ phiếu (TP - Entry)
    private BigDecimal stopLossPercent;         // Khoảng cách cắt lỗ (%)
    private BigDecimal takeProfitPercent;       // Lợi nhuận kỳ vọng (%)
    private Integer maxSharesToBuy;             // Số lượng cổ phiếu tối đa được mua (Lô 100)
    private BigDecimal totalCapitalRequired;    // Tổng vốn cần giải ngân (VND)
    private BigDecimal allocationPercent;       // Tỷ trọng danh mục (% NAV)
    private BigDecimal riskRewardRatio;         // Tỷ lệ R:R (TP / SL)
    private boolean acceptable;                 // Đạt chuẩn giao dịch không?
    private String verdict;                     // Kết luận (ĐẠT CHUẨN / CẢNH BÁO RỦI RO / TỪ CHỐI)
    private List<String> rulesEvaluated;        // Chi tiết đánh giá theo các quy tắc
    private BigDecimal expectedLossAmount;      // Mức lỗ thực tế nếu dính SL
    private BigDecimal expectedProfitAmount;    // Mức lãi thực tế nếu chạm TP
}
