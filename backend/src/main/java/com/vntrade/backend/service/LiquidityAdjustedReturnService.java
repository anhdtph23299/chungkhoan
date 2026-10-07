package com.vntrade.backend.service;

import com.vntrade.backend.dto.LiquidityAdjustedReturnDto;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Liquidity-Adjusted Return Service — Institutional Grade VN-Market.
 *
 * Tính toán lợi nhuận thực tế sau khi trừ các chi phí thanh khoản:
 *
 * 1. MARKET IMPACT (Square-Root Law):
 *    Slippage% = σ_daily × √(Q / ADTV)
 *    Trong đó: σ_daily = daily vol, Q = position size, ADTV = avg daily vol
 *    → Lệnh lớn tác động lên giá nhiều hơn theo căn bậc hai
 *
 * 2. BID-ASK SPREAD:
 *    Spread phụ thuộc thanh khoản: TIER1: 0.05%, TIER2: 0.15%, TIER3: 0.30%, ILLIQUID: 0.60%
 *    → Mỗi lần giao dịch mất 0.5x spread (crossing half spread)
 *
 * 3. TRANSACTION COSTS VN (2024):
 *    Phí môi giới: 0.15% (có thể negotiate về 0.10% cho khách lớn)
 *    Phí SSC (Ủy ban CK): 0.03% một chiều
 *    Tổng 2 chiều: ~0.36%
 *
 * 4. T+2.5 OPPORTUNITY COST:
 *    Vốn bị lock 2.5 ngày → mất opportunity cost = rf × 2.5/252
 *    rf = 5% (lãi suất phi rủi ro VN 2024)
 *
 * 5. PARTICIPATION RATE RULE:
 *    Không nên chiếm quá 20-30% ADTV mỗi phiên (tránh front-running)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiquidityAdjustedReturnService {

    private final StockPriceService stockPriceService;

    // ===== CONSTANTS (VN-Market 2024) =====
    private static final double BROKERAGE_RATE       = 0.0015;  // 0.15% một chiều
    private static final double SSC_FEE_RATE         = 0.0003;  // 0.03% SSC một chiều
    private static final double RISK_FREE_RATE       = 0.05;    // 5%/năm lãi phi rủi ro
    private static final double T25_DAYS             = 2.5;     // T+2.5 HOSE settlement
    private static final double MAX_PARTICIPATION    = 0.20;    // Tối đa 20% ADTV mỗi phiên
    private static final double DAILY_VOL_PROXY      = 0.015;   // 1.5% daily vol proxy

    // ADTV ước tính theo tier VN-Market (cp/ngày)
    private static final double TIER1_ADTV = 5_000_000;   // VN30: > 5M cp/ngày (VCB, FPT, VHM...)
    private static final double TIER2_ADTV = 1_000_000;   // Mid-cap: 1-5M cp/ngày
    private static final double TIER3_ADTV =   200_000;   // Small-cap: 200K-1M cp/ngày
    private static final double ILLIQUID_ADTV =  50_000;  // Illiquid: < 200K cp/ngày

    // VN30 symbols (Tier 1 liquidity)
    private static final java.util.Set<String> TIER1_SYMBOLS = java.util.Set.of(
        "VCB", "BID", "CTG", "VPB", "MBB", "TCB", "ACB", "HDB",
        "FPT", "VHM", "VIC", "HPG", "GVR", "SAB", "MSN", "MWG",
        "PLX", "POW", "GAS", "BSR", "VRE", "NVL", "PDR", "SSI",
        "HCM", "VND", "SHS", "VCI", "BCM", "TPB"
    );

    public LiquidityAdjustedReturnDto calculateLiquidityAdjustedReturn(
            String symbol, int shares, BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal capital) {

        String sym = symbol != null ? symbol.toUpperCase().trim() : "FPT";
        BigDecimal nav = capital != null && capital.compareTo(BigDecimal.ZERO) > 0
            ? capital : BigDecimal.valueOf(200_000_000);

        // Lấy giá thực nếu không có input
        StockQuote quote = stockPriceService.getQuote(sym);
        BigDecimal entry = (entryPrice != null && entryPrice.compareTo(BigDecimal.ZERO) > 0)
            ? entryPrice
            : (quote.getPrice() != null ? quote.getPrice() : BigDecimal.valueOf(50_000));
        double entryD = entry.doubleValue();

        // Tỷ lệ lợi nhuận lý thuyết
        double exitD = (exitPrice != null && exitPrice.compareTo(BigDecimal.ZERO) > 0)
            ? exitPrice.doubleValue() : entryD * 1.10;  // Default target +10%
        double grossReturn = (exitD - entryD) / entryD * 100.0;

        // ---- 1. Xác định Liquidity Tier và ADTV ----
        double adtvShares;
        String liquidityTier;
        double bidAskSpreadPct;

        if (TIER1_SYMBOLS.contains(sym)) {
            adtvShares = TIER1_ADTV;
            liquidityTier = "TIER_1_BLUE_CHIP";
            bidAskSpreadPct = 0.05;
        } else if (shares <= 500_000) {
            adtvShares = TIER2_ADTV;
            liquidityTier = "TIER_2_MID_CAP";
            bidAskSpreadPct = 0.15;
        } else if (shares <= 100_000) {
            adtvShares = TIER3_ADTV;
            liquidityTier = "TIER_3_SMALL_CAP";
            bidAskSpreadPct = 0.30;
        } else {
            adtvShares = ILLIQUID_ADTV;
            liquidityTier = "ILLIQUID";
            bidAskSpreadPct = 0.60;
        }

        double adtvVnd = adtvShares * entryD;

        // ---- 2. Market Impact (Square-Root Law) ----
        double participationRate = (double) shares / adtvShares * 100.0;
        double sqrtParticipation = Math.sqrt(Math.min(1.0, (double) shares / adtvShares));

        // Slippage = sigma_daily × sqrt(Q/ADTV) — mỗi chiều (vào và thoát)
        double entrySlippagePct = DAILY_VOL_PROXY * sqrtParticipation * 100.0;
        double exitSlippagePct  = entrySlippagePct * 1.1;  // Exit thường tốn hơn vì đang bán áp lực
        double totalSlippagePct = entrySlippagePct + exitSlippagePct;

        double tradeValue       = shares * entryD;
        double entrySlippageCost = tradeValue * entrySlippagePct / 100.0;
        double exitSlippageCost  = tradeValue * exitSlippagePct / 100.0;
        double totalSlippageCost = entrySlippageCost + exitSlippageCost;

        // ---- 3. Transaction Costs (2 chiều) ----
        double brokerFee    = tradeValue * BROKERAGE_RATE * 2;   // Vào + thoát
        double sscFee       = tradeValue * SSC_FEE_RATE * 2;
        double totalTxCost  = brokerFee + sscFee;
        double txCostPct    = totalTxCost / tradeValue * 100.0;

        // Bid-ask spread cost (crossing half spread mỗi chiều = 1 full spread cho 2 chiều)
        double spreadCost    = tradeValue * bidAskSpreadPct / 100.0;
        double spreadCostPct = bidAskSpreadPct;

        // ---- 4. T+2.5 Opportunity Cost ----
        double oppCostPct = RISK_FREE_RATE * T25_DAYS / 252.0 * 100.0;  // ~0.050%
        double oppCostVnd = tradeValue * oppCostPct / 100.0;

        // ---- 5. Tổng hợp Liquidity Penalty ----
        double liquidityPenaltyPct = totalSlippagePct + txCostPct + spreadCostPct + oppCostPct;
        double netReturnPct        = grossReturn - liquidityPenaltyPct;
        double breakEvenReturnPct  = liquidityPenaltyPct;  // Phải có gross return >= này để có lời

        // ---- 6. Execution Recommendation ----
        String executionStrategy;
        int maxLotPerSession = (int) (adtvShares * MAX_PARTICIPATION / 100) * 100;  // Làm tròn lô 100
        maxLotPerSession = Math.max(100, Math.min(maxLotPerSession, shares));
        int sessionsToFill = (int) Math.ceil((double) shares / maxLotPerSession);

        if (participationRate <= 3.0) {
            executionStrategy = "MARKET_ORDER (< 3% ADTV — ảnh hưởng giá không đáng kể)";
        } else if (participationRate <= 10.0) {
            executionStrategy = "LIMIT_ORDER_TWAP (3-10% ADTV — chia 2-3 lệnh trong phiên)";
        } else if (participationRate <= 20.0) {
            executionStrategy = "VWAP_EXECUTION (10-20% ADTV — chia đều theo volume profile)";
        } else {
            executionStrategy = "AGGRESSIVE_TWAP_MULTI_SESSION (> 20% ADTV — cần " + sessionsToFill + " phiên)";
        }

        boolean isLiquidEnough = participationRate <= 30.0 && !liquidityTier.equals("ILLIQUID");

        // ---- 7. Risk Factors ----
        List<String> riskFactors = buildRiskFactors(sym, participationRate, liquidityTier, totalSlippagePct, netReturnPct, breakEvenReturnPct);

        // ---- 8. Verdict ----
        String verdict = buildVerdict(sym, shares, grossReturn, netReturnPct, liquidityPenaltyPct, breakEvenReturnPct, liquidityTier, participationRate, sessionsToFill);

        return LiquidityAdjustedReturnDto.builder()
            .symbol(sym)
            .entryPrice(entry)
            .exitPrice(bd(exitD, 0))
            .sharesQty(shares)
            .accountCapital(nav)
            // Liquidity
            .adtvShares(bd(adtvShares, 0))
            .adtvVnd(bd(adtvVnd, 0))
            .participationRate(bd(participationRate, 2))
            .liquidityTier(liquidityTier)
            .bidAskSpreadPct(bd(bidAskSpreadPct, 2))
            // Slippage
            .entrySlippagePct(bd(entrySlippagePct, 4))
            .exitSlippagePct(bd(exitSlippagePct, 4))
            .totalSlippagePct(bd(totalSlippagePct, 4))
            .entrySlippageCost(bd(entrySlippageCost, 0))
            .exitSlippageCost(bd(exitSlippageCost, 0))
            .totalSlippageCost(bd(totalSlippageCost, 0))
            // Transaction costs
            .brokarageFee(bd(brokerFee + spreadCost, 0))
            .secuFee(bd(sscFee, 0))
            .totalTransactionCost(bd(totalTxCost + spreadCost, 0))
            // Opportunity cost
            .t25LockupDays(bd(T25_DAYS, 1))
            .opportunityCostPct(bd(oppCostPct, 4))
            .opportunityCostVnd(bd(oppCostVnd, 0))
            // Adjusted returns
            .grossReturnPct(bd(grossReturn, 4))
            .netReturnPct(bd(netReturnPct, 4))
            .breakEvenReturnPct(bd(breakEvenReturnPct, 4))
            .liquidityPenaltyPct(bd(liquidityPenaltyPct, 4))
            // Execution
            .executionStrategy(executionStrategy)
            .recommendedLotSize(maxLotPerSession)
            .estimatedSessionsToFill(sessionsToFill)
            .isLiquidEnough(isLiquidEnough)
            // Verdict
            .liquidityVerdict(verdict)
            .riskFactors(riskFactors)
            .build();
    }

    private List<String> buildRiskFactors(String sym, double partRate, String tier,
            double slippage, double netReturn, double breakEven) {
        List<String> factors = new ArrayList<>();

        if (partRate > 20.0) factors.add(String.format(
            "⚠️ PARTICIPATION RISK: Lệnh chiếm %.1f%% ADTV — nguy cơ front-running và adverse market impact", partRate));
        if (slippage > 0.5) factors.add(String.format(
            "⚠️ SLIPPAGE RISK: Market impact %.4f%% — lệnh lớn so với thanh khoản", slippage));
        if ("ILLIQUID".equals(tier)) factors.add(
            "🚨 ILLIQUID MARKET: Cổ phiếu thanh khoản thấp — rủi ro kẹp hàng và không thoát được");
        if (netReturn < 0) factors.add(String.format(
            "❌ NEGATIVE NET RETURN: Sau chi phí thanh khoản, lợi nhuận ròng = %.2f%% (âm!) — Không nên giao dịch", netReturn));
        if (netReturn < breakEven * 0.5 && netReturn > 0) factors.add(String.format(
            "⚠️ THIN MARGIN: Net return %.2f%% chỉ cách hòa vốn %.2f%% — Rủi ro cao", netReturn, breakEven));

        if (factors.isEmpty()) factors.add(
            "✅ Các chỉ số thanh khoản trong ngưỡng an toàn cho giao dịch");
        return factors;
    }

    private String buildVerdict(String sym, int shares, double gross, double net,
            double penalty, double breakEven, String tier, double partRate, int sessions) {
        if (net <= 0) {
            return String.format(
                "❌ TỪ CHỐI GIAO DỊCH %s: Sau khi điều chỉnh chi phí thanh khoản %.4f%%, " +
                "lợi nhuận ròng = %.4f%% (ÂM). Cần tỷ suất gộp > %.4f%% để có lời thực. " +
                "Giảm size hoặc chọn mã có thanh khoản tốt hơn.",
                sym, penalty, net, breakEven);
        }
        return String.format(
            "📊 LIQUIDITY-ADJUSTED ANALYSIS %s (%s): %,d cp | Gross: %.2f%% → Net: %.2f%% " +
            "(sau %.4f%% chi phí: slippage + phí + T+2.5). Tỷ lệ hòa vốn: %.4f%%. " +
            "Participation rate: %.1f%% ADTV%s. Chiến lược thực thi: %s.",
            sym, tier, shares, gross, net, penalty, breakEven, partRate,
            sessions > 1 ? " (chia " + sessions + " phiên)" : "",
            tier.startsWith("TIER_1") ? "MARKET/LIMIT tự do" : "VWAP/TWAP khuyến nghị");
    }

    private BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
