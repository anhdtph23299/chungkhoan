package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BollingerBandsPoint {
    private BigDecimal upper;      // SMA + (k * stdDev)
    private BigDecimal middle;     // SMA
    private BigDecimal lower;      // SMA - (k * stdDev)
    private BigDecimal bandwidth;  // (Upper - Lower) / Middle
}
