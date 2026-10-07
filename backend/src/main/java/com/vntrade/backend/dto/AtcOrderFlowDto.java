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
public class AtcOrderFlowDto {
    private String symbol;
    private BigDecimal continuousMatchPrice; // Giá khớp liên tục cuối phiên (14:29)
    private BigDecimal expectedAtcPrice;      // Giá khớp dự kiến trong phiên ATC
    private BigDecimal atcSpreadPercent;       // Độ lệch giá ATC (%)
    private long atcBuyOrderVolume;           // Khối lượng đặt mua ATC
    private long atcSellOrderVolume;          // Khối lượng đặt bán ATC
    private BigDecimal atcBuyPressureRatio;   // Tỷ lệ áp lực mua ATC (BuyVol / SellVol)
    private String atcActionSignal;           // HOLD_FOR_OVERNIGHT_GAP, SECURE_PROFIT_BEFORE_ATC, BUY_ATC_PULLBACK
    private String professionalTactic;        // Chiến thuật già làng khuyên dùng
    private boolean isHighVolatilityExpected;// Dự báo biến động mạnh phiên ATC
}
