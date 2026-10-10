package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuturesPositionDto {
    private String id;
    private String symbol;               // VN30F1M
    private String side;                 // LONG hoặc SHORT
    private Integer contracts;           // Số hợp đồng (1, 2, 3...)
    private BigDecimal entryPrice;       // Giá vào vị thế
    private BigDecimal currentPrice;     // Giá thị trường hiện tại
    private BigDecimal stopLossPrice;    // Điểm cắt lỗ
    private BigDecimal takeProfitPrice;  // Điểm chốt lời
    private BigDecimal trailingStopPrice;// Điểm trần bảo vệ lãi
    private BigDecimal pnlPoints;        // Lãi/Lỗ theo điểm (pts)
    private BigDecimal grossPnlVnd;      // Lãi/Lỗ thô = pnlPoints * 100,000 * contracts
    private BigDecimal feesVnd;          // Phí HNX + Phí môi giới + Thuế
    private BigDecimal netPnlVnd;        // Lãi/Lỗ ròng thực tế
    private BigDecimal marginUsed;       // Tiền ký quỹ phong tỏa (~17.5% giá trị HĐ)
    private String status;               // OPEN, CLOSED
    private String closeReason;          // TAKE_PROFIT, STOP_LOSS, TRAILING_STOP, MANUAL, EOD_CLOSE
    private BigDecimal closePrice;
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
}
