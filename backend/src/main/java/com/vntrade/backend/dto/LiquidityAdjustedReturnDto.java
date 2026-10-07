package com.vntrade.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO cho Liquidity-Adjusted Return Service.
 * Điều chỉnh lợi nhuận lý thuyết theo chi phí thanh khoản thực tế VN-Market:
 * - Market impact (price slippage khi vào/ra lệnh lớn)
 * - Bid-ask spread cost
 * - T+2.5 opportunity cost (vốn bị lock)
 * - Participation rate vs ADTV (Average Daily Trading Volume)
 */
@Data
@Builder
public class LiquidityAdjustedReturnDto {

    // ===== INPUT =====
    private String symbol;
    private BigDecimal entryPrice;           // Giá vào lệnh dự kiến
    private BigDecimal exitPrice;            // Giá thoát lệnh dự kiến
    private int sharesQty;                   // Số cổ phiếu giao dịch
    private BigDecimal accountCapital;       // NAV tổng

    // ===== LIQUIDITY METRICS =====
    private BigDecimal adtvShares;           // Average Daily Trading Volume (cp/ngày)
    private BigDecimal adtvVnd;              // ADTV theo VND
    private BigDecimal participationRate;    // Tỷ lệ lệnh chiếm ADTV (%)
    private String liquidityTier;           // TIER_1 / TIER_2 / TIER_3 / ILLIQUID
    private BigDecimal bidAskSpreadPct;     // Spread bid-ask (%)

    // ===== SLIPPAGE ESTIMATES =====
    private BigDecimal entrySlippagePct;    // Slippage khi vào lệnh (%)
    private BigDecimal exitSlippagePct;     // Slippage khi thoát lệnh (%)
    private BigDecimal totalSlippagePct;    // Tổng slippage 2 chiều (%)
    private BigDecimal entrySlippageCost;   // Chi phí slippage vào (VND)
    private BigDecimal exitSlippageCost;    // Chi phí slippage thoát (VND)
    private BigDecimal totalSlippageCost;   // Tổng chi phí slippage (VND)

    // ===== TRANSACTION COSTS =====
    private BigDecimal brokarageFee;        // Phí môi giới (0.15% thông thường)
    private BigDecimal secuFee;             // Phí SSC (0.03%)
    private BigDecimal totalTransactionCost; // Tổng phí giao dịch cả 2 chiều

    // ===== OPPORTUNITY COST (T+2.5) =====
    private BigDecimal t25LockupDays;       // Số ngày vốn bị lock do T+2.5
    private BigDecimal opportunityCostPct;  // Chi phí cơ hội (%)
    private BigDecimal opportunityCostVnd;  // Chi phí cơ hội (VND)

    // ===== ADJUSTED RETURNS =====
    private BigDecimal grossReturnPct;      // Lợi nhuận gộp lý thuyết (%)
    private BigDecimal netReturnPct;        // Lợi nhuận ròng sau tất cả chi phí (%)
    private BigDecimal breakEvenReturnPct;  // Return tối thiểu để hòa vốn (%)
    private BigDecimal liquidityPenaltyPct; // Tổng phạt thanh khoản (%)

    // ===== EXECUTION RECOMMENDATION =====
    private String executionStrategy;       // VWAP / TWAP / ATC / LIMIT_ORDER
    private int recommendedLotSize;         // Số lô tối đa mỗi lần đặt (≤ 3% ADTV)
    private int estimatedSessionsToFill;    // Số phiên cần chia để vào đủ vị thế
    private boolean isLiquidEnough;         // true nếu đủ thanh khoản để giao dịch (dùng getter isLiquidEnough())


    // ===== VERDICT =====
    private String liquidityVerdict;        // Kết luận chi tiết
    private List<String> riskFactors;       // Các yếu tố rủi ro thanh khoản
}
