package com.vntrade.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockQuote {
    private String symbol;
    private BigDecimal price;           // Giá hiện tại
    private BigDecimal change;          // Thay đổi so với hôm qua
    private BigDecimal changePercent;   // % thay đổi
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private Long volume;
    private String exchange;
    private String source;              // Nguồn dữ liệu
    private String dataSource;          // "REAL" (từ sàn thật VNDirect/TCBS/SSI) hoặc "STALE" (mất kết nối / cache cũ)
    private String timestamp;

    public boolean isReal() {
        return "REAL".equalsIgnoreCase(this.dataSource);
    }

    public boolean isStale() {
        return !isReal();
    }
}
