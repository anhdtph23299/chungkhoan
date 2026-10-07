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
public class MarketRegimeDto {
    private String currentRegime;                 // CONFIRMED_UPTREND, UPTREND_UNDER_PRESSURE, SIDEWAYS_ACCUMULATION, DOWNTREND_DEFENSE
    private String regimeDisplayName;             // Tên hiển thị tiếng Việt
    private BigDecimal vnIndexLevel;              // Điểm số VN-Index
    private BigDecimal vnIndexChangePercent;      // % biến động VN-Index hôm nay
    private int distributionDaysCount;            // Số phiên phân phối trong 25 phiên gần nhất
    private boolean followThroughDayConfirmed;    // Có phiên bùng nổ theo đà (FTD) bảo trợ không
    private BigDecimal recommendedMaxExposure;    // Tỷ trọng cổ phiếu tối đa khuyến nghị (% NAV)
    private BigDecimal recommendedStopLossPercent;// Ngưỡng cắt lỗ phù hợp chế độ thị trường (%)
    private BigDecimal recommendedTakeProfitPercent;// Ngưỡng chốt lời kỳ vọng (%)
    private String recommendedPrimaryStrategy;    // Chiến lược tối ưu (VCP Breakout, Buy Dip, Cash King)
    private List<String> regimeCharacteristics;   // Đặc tính thị trường hiện tại
    private String quantOfficerVerdict;           // Nhận định định lượng từ Giám đốc rủi ro quỹ
}
