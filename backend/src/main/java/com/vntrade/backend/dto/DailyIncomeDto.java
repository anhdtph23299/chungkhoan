package com.vntrade.backend.dto;

import com.vntrade.backend.entity.Trade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyIncomeDto {

    private LocalDate reportDate;
    private BigDecimal dailyRealizedProfit;      // Lợi nhuận đã chốt tiền tươi hôm nay
    private BigDecimal dailyUnrealizedProfit;    // Lợi nhuận tạm tính các mã đang gồng lãi
    private BigDecimal totalDailyNetProfit;      // Tổng lợi nhuận ngày
    private BigDecimal dailyTarget;              // Mục tiêu lợi nhuận đặt ra (VD: 1.500.000 đ)
    private BigDecimal targetAchievementPercent; // % hoàn thành mục tiêu ngày
    private int harvestedProfitsCount;           // Số lệnh gặt hái 50% tiền mặt
    private int tradesClosedToday;               // Số lệnh đã chốt hôm nay
    private BigDecimal winRateToday;             // Tỷ lệ thắng hôm nay (%)
    private BigDecimal reinvestmentCapital;      // 70% tái đầu tư tăng trưởng NAV
    private BigDecimal withdrawableIncome;       // 30% tiền mặt sẵn sàng rút chi tiêu hàng ngày
    private BigDecimal monthlyProjectedIncome;   // Dự phóng thu nhập tháng (22 ngày giao dịch)
    private String marketStatus;                 // Trạng thái thị trường
    private String dailyStatusMessage;           // Thông điệp trạng thái bot kiếm tiền
    private List<Trade> closedTradesToday;       // Danh sách các lệnh đã chốt hôm nay
    private List<Trade> openPositionsSummary;    // Danh sách vị thế đang nắm giữ
}
