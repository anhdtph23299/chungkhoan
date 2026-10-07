package com.vntrade.backend.service;

import com.vntrade.backend.dto.AdaptivePositionSizingDto;
import com.vntrade.backend.dto.InstitutionalBacktestResultDto;
import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptive Position Sizing Service — Institutional Grade.
 *
 * Framework kết hợp 4 tầng sizing:
 *
 * 1. ATR Volatility Targeting (Tier 1 - Base):
 *    Shares = (Account * TargetDailyVolPct) / (ATR per share)
 *    → Đảm bảo mỗi vị thế đóng góp đúng mức volatility vào portfolio
 *
 * 2. Half-Kelly Criterion (Tier 2 - Edge Gate):
 *    f* = (p*b - q) / b, sử dụng Half-Kelly để safety margin
 *    → Chỉ mở vị thế khi có edge thống kê dương
 *
 * 3. Regime Multiplier (Tier 3 - Market Context):
 *    BULL: 1.0x | SIDEWAYS: 0.6x | BEAR: 0.0x (locked out)
 *    → Phản ánh điều kiện thị trường vĩ mô
 *
 * 4. Volatility Environment Scale (Tier 4 - Risk Adjustment):
 *    LOW vol: 1.2x | NORMAL: 1.0x | ELEVATED: 0.75x | EXTREME: 0.5x
 *    → Giảm size khi thị trường bất ổn định
 *
 * Final: min(ATR_size, Kelly_size) * Regime_mult * Vol_scale
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdaptivePositionSizingService {

    private final StockPriceService stockPriceService;
    private final InstitutionalBacktestService institutionalBacktestService;
    private final TradeRepository tradeRepository;
    private final RegimeSwitchingSignalService regimeSwitchingSignalService;
    private final GarchVolatilityForecastService garchVolatilityForecastService;

    // ===== CONSTANTS =====
    private static final double TARGET_DAILY_VOL_PCT = 0.015;  // 1.5% NAV per position per day
    private static final double MAX_SINGLE_POSITION  = 0.25;   // Tối đa 25% NAV một cổ phiếu
    private static final double ATR_STOP_MULTIPLIER  = 1.5;    // Stop loss = 1.5x ATR
    private static final double RR_RATIO             = 2.5;    // Risk:Reward mục tiêu 1:2.5

    public AdaptivePositionSizingDto calculateAdaptiveSize(String symbol, BigDecimal capital) {
        String sym = symbol != null ? symbol.toUpperCase().trim() : "FPT";
        BigDecimal nav = capital != null && capital.compareTo(BigDecimal.ZERO) > 0
            ? capital : BigDecimal.valueOf(200_000_000);

        // ---- 1. Lấy dữ liệu cổ phiếu ----
        StockQuote quote = stockPriceService.getQuote(sym);
        BigDecimal price = quote.getPrice() != null && quote.getPrice().compareTo(BigDecimal.ZERO) > 0
            ? quote.getPrice() : BigDecimal.valueOf(50_000);

        // ---- 2. Tính ATR (True Average Range proxy) ----
        double changeAbs = quote.getChangePercent() != null
            ? Math.abs(quote.getChangePercent().doubleValue()) : 1.5;

        // ATR proxy: Ưu tiên GARCH conditional vol hiện tại (annualized → daily)
        double atrPct = changeAbs * 1.5;  // Fallback: ATR ~ 1.5x daily range thông thường
        try {
            var garch = garchVolatilityForecastService.forecastVolatility(sym);
            if (garch != null && garch.getConditionalVolCurrent() != null
                    && garch.getConditionalVolCurrent().compareTo(BigDecimal.ZERO) > 0) {
                // Conditional vol annualized (%) → daily vol (%): vol_daily = vol_annual / sqrt(252)
                atrPct = garch.getConditionalVolCurrent().doubleValue() / Math.sqrt(252);
            } else if (garch != null && garch.getForecastT25SettlementVolPercent() != null
                    && garch.getForecastT25SettlementVolPercent().compareTo(BigDecimal.ZERO) > 0) {
                // T+2.5 settlement vol phụ: scale xấp xỉ ATR
                atrPct = garch.getForecastT25SettlementVolPercent().doubleValue() * 0.8;
            }
        } catch (Exception e) {
            log.debug("GARCH unavailable cho ATR, dùng fallback: {}", e.getMessage());
        }
        atrPct = Math.max(0.5, Math.min(10.0, atrPct));

        double priceDouble = price.doubleValue();
        double atr14       = priceDouble * atrPct / 100.0;

        // ---- 3. Tier 1: ATR Volatility Targeting ----
        // atrBasedShares = (NAV * TARGET_VOL_PCT) / ATR_per_share
        double targetVolMoney    = nav.doubleValue() * TARGET_DAILY_VOL_PCT;
        double atrBasedSharesRaw = targetVolMoney / atr14;
        int    atrBasedShares    = (int) (atrBasedSharesRaw / 100) * 100;  // Làm tròn lô 100
        double atrBasedMoney     = atrBasedShares * priceDouble;
        // Cap ATR-based allocation to MAX_SINGLE_POSITION
        double atrBasedPct = Math.min(
            MAX_SINGLE_POSITION * 100.0,
            atrBasedMoney / nav.doubleValue() * 100.0
        );
        // Recalculate after cap
        atrBasedMoney  = nav.doubleValue() * atrBasedPct / 100.0;
        atrBasedShares = (int) (atrBasedMoney / priceDouble / 100) * 100;
        atrBasedMoney  = atrBasedShares * priceDouble;

        // ---- 4. Tier 2: Kelly Criterion ----
        double p = 0.62, b = 2.0;  // Default values
        try {
            InstitutionalBacktestResultDto bt = institutionalBacktestService
                .runInstitutionalBacktest(sym, "ADAPTIVE_COMPOSITE", 120, nav, 7.0, 17.5);
            if (bt != null) {
                if (bt.getWinRatePercent() != null && bt.getWinRatePercent().doubleValue() > 0)
                    p = Math.min(0.85, Math.max(0.35, bt.getWinRatePercent().doubleValue() / 100.0));
                if (bt.getProfitFactor() != null && bt.getProfitFactor().doubleValue() > 0)
                    b = Math.min(4.0, Math.max(1.0, bt.getProfitFactor().doubleValue()));
            }
        } catch (Exception e) {
            log.debug("Backtest Kelly fallback: {}", e.getMessage());
        }

        // Real win rate blending (60% backtest + 40% live)
        try {
            long closed = tradeRepository.countClosedTrades();
            long wins   = tradeRepository.countWinningTrades();
            if (closed >= 5) {
                double realWR = (double) wins / closed;
                p = p * 0.60 + realWR * 0.40;
            }
        } catch (Exception ignored) {}

        double q         = 1.0 - p;
        // IMPORTANT: Không clamp fullKelly về 0 để có thể detect edge âm
        double fullKelly = (p * b - q) / b;  // Có thể âm nếu edge âm
        double halfKelly = Math.max(0.0, fullKelly / 2.0);
        double kellyPct  = Math.min(MAX_SINGLE_POSITION * 100, halfKelly * 100.0);

        // ---- 5. Tier 3: Regime Multiplier ----
        String currentRegime = "SIDEWAYS";
        double regimeMult = 0.6;
        try {
            var regimeDto = regimeSwitchingSignalService.analyzeMarketRegime();
            currentRegime = regimeDto.getCurrentRegime();
        } catch (Exception e) {
            log.debug("Regime service fallback: {}", e.getMessage());
        }
        String regimeJustification;
        switch (currentRegime) {
            case "BULL" -> {
                regimeMult = 1.0;
                regimeJustification = "Bull Regime: Hệ số nhân tối đa 1.0x — Thị trường thuận lợi, cho phép khai thác edge tối đa";
            }
            case "BEAR" -> {
                regimeMult = 0.0;
                regimeJustification = "Bear Regime: Hệ số nhân = 0.0x — KHÓA HOÀN TOÀN, không mở vị thế mới trong downtrend";
            }
            default -> {
                regimeMult = 0.6;
                regimeJustification = "Sideways Regime: Hệ số nhân 0.6x — Thị trường tích lũy, giảm size để hạn chế whipsaw";
            }
        }

        // ---- 6. Tier 4: Volatility Environment Scale ----
        double volPctile = Math.min(100.0, atrPct / 0.15);
        double volScale;
        String volEnvironment;
        if (atrPct < 1.0) {
            volScale = 1.2; volEnvironment = "LOW";
        } else if (atrPct < 2.0) {
            volScale = 1.0; volEnvironment = "NORMAL";
        } else if (atrPct < 4.0) {
            volScale = 0.75; volEnvironment = "ELEVATED";
        } else {
            volScale = 0.50; volEnvironment = "EXTREME";
        }

        // ---- Pre-calculate Risk metrics (needed even for zero-size DTO) ----
        double stopLossPct     = ATR_STOP_MULTIPLIER * atrPct;
        double stopLossPrice   = priceDouble * (1 - stopLossPct / 100.0);
        double takeProfitPct   = stopLossPct * RR_RATIO;
        double takeProfitPrice = priceDouble * (1 + takeProfitPct / 100.0);

        // ---- 7. Combine: Kelly Edge Gate ----
        // Nếu Kelly edge âm → block hoàn toàn (edge gate - không có lợi thế thống kê)
        if (fullKelly <= 0.0) {
            return buildZeroSizeDto(sym, nav, price, currentRegime, fullKelly, halfKelly, p, b,
                atr14, atrPct, atrBasedShares, atrBasedMoney, atrBasedPct,
                regimeMult, regimeJustification, volPctile, volScale, volEnvironment,
                stopLossPct, stopLossPrice, takeProfitPct, takeProfitPrice);
        }

        // Conservative combination: min(ATR, Kelly) * Regime * Vol
        double conservativeBase = Math.min(atrBasedPct, kellyPct > 0 ? kellyPct : atrBasedPct);
        conservativeBase = Math.min(conservativeBase, MAX_SINGLE_POSITION * 100.0);

        double adaptivePct    = conservativeBase * regimeMult * volScale;
        double adaptiveMoney  = nav.doubleValue() * adaptivePct / 100.0;
        int    adaptiveShares = (int) (adaptiveMoney / priceDouble / 100) * 100;
        double actualMoney    = adaptiveShares * priceDouble;
        double actualPct      = actualMoney / nav.doubleValue() * 100.0;

        // ---- 8. Risk metrics (full) ----
        double maxLossIfStop = actualMoney * stopLossPct / 100.0;
        double rrRatio       = takeProfitPct / Math.max(0.01, stopLossPct);

        // ---- 9. Comparison metrics ----
        double fixed5Pct    = nav.doubleValue() * 0.05;
        double kellyOnlyPct = nav.doubleValue() * kellyPct / 100.0;
        double atrOnlyPct   = nav.doubleValue() * atrBasedPct / 100.0;
        double advantage    = adaptivePct - 5.0;  // vs fixed 5%

        // ---- 10. Sizing factors & verdict ----
        List<String> factors = buildSizingFactors(sym, atrPct, p, b, fullKelly, regimeMult, volScale, adaptivePct, regimeJustification, volEnvironment);
        String verdict = buildVerdict(sym, currentRegime, adaptivePct, adaptiveShares, actualMoney, stopLossPct, takeProfitPct, maxLossIfStop, rrRatio, p, b, atrPct);
        String riskWarning = buildRiskWarning(regimeMult, adaptivePct, atrPct, fullKelly);

        return AdaptivePositionSizingDto.builder()
            .symbol(sym)
            .accountCapital(nav)
            .currentPrice(price)
            .currentRegime(currentRegime)
            // ATR
            .atr14(bd(atr14, 0))
            .atrPercent(bd(atrPct, 2))
            .targetDailyVolatility(bd(TARGET_DAILY_VOL_PCT * 100.0, 2))
            .atrBasedShares(bd(atrBasedShares, 0))
            .atrBasedAllocation(bd(atrBasedMoney, 0))
            .atrBasedPercent(bd(atrBasedPct, 2))
            // Kelly
            .kellyWinRate(bd(p * 100.0, 1))
            .kellyPayoffRatio(bd(b, 2))
            .fullKellyPercent(bd(fullKelly * 100.0, 2))
            .halfKellyPercent(bd(halfKelly * 100.0, 2))
            // Regime
            .regimeMultiplier(bd(regimeMult, 2))
            .regimeJustification(regimeJustification)
            // Vol environment
            .volatilityPercentile(bd(volPctile, 1))
            .volatilityScaleFactor(bd(volScale, 2))
            .volatilityEnvironment(volEnvironment)
            // Combined
            .adaptiveAllocationPercent(bd(adaptivePct, 2))
            .adaptiveAllocationMoney(bd(actualMoney, 0))
            .adaptiveSharesToBuy(adaptiveShares)
            .actualAllocationPercent(bd(actualPct, 2))
            // Risk
            .stopLossPrice(bd(stopLossPrice, 0))
            .stopLossPercent(bd(stopLossPct, 2))
            .takeProfitPrice(bd(takeProfitPrice, 0))
            .takeProfitPercent(bd(takeProfitPct, 2))
            .maxLossIfStopHit(bd(maxLossIfStop, 0))
            .riskRewardRatio(bd(rrRatio, 2))
            // Comparison
            .fixedPercent5Pct(bd(fixed5Pct, 0))
            .kellyOnlyPercent(bd(kellyOnlyPct, 0))
            .atrOnlyPercent(bd(atrOnlyPct, 0))
            .adaptiveAdvantage(bd(advantage, 2))
            // Verdict
            .sizingVerdict(verdict)
            .sizingFactors(factors)
            .riskWarning(riskWarning)
            .build();
    }

    private List<String> buildSizingFactors(
            String sym, double atrPct, double p, double b, double fullKelly,
            double regimeMult, double volScale, double adaptivePct,
            String regimeJustification, String volEnv) {
        List<String> factors = new ArrayList<>();
        factors.add(String.format("📊 ATR Volatility: %.2f%% — Target daily vol 1.5%% NAV → ATR-based sizing xác định base size", atrPct));
        factors.add(String.format("🎯 Kelly Criterion: Win Rate=%.1f%%, Payoff=%.2fx → Full Kelly=%.1f%%, dùng Half Kelly an toàn", p*100, b, fullKelly*100));
        factors.add(String.format("🌊 Regime: %s → %s", regimeMult == 1.0 ? "BULL" : regimeMult == 0.0 ? "BEAR" : "SIDEWAYS", regimeJustification));
        factors.add(String.format("⚡ Volatility Env: %s (ATR %.2f%%) → Scale factor %.2fx", volEnv, atrPct, volScale));
        factors.add(String.format("✅ Final Adaptive Size: %.2f%% NAV (sau tất cả 4 tầng điều chỉnh)", adaptivePct));
        return factors;
    }

    private String buildVerdict(
            String sym, String regime, double adaptivePct, int shares, double money,
            double stopPct, double tpPct, double maxLoss, double rr, double winRate, double payoff, double atrPct) {
        if (adaptivePct <= 0) {
            return String.format(
                "❌ TỪ CHỐI GIẢI NGÂN %s: Regime = %s. Mô hình Adaptive Sizing đặt exposure = 0. " +
                "Không mở vị thế mới. Bảo toàn 100%% vốn cho đến khi chế độ thị trường thay đổi.",
                sym, regime);
        }
        return String.format(
            "✅ ADAPTIVE SIZING %s: Dựa trên [ATR=%.2f%% | Kelly WR=%.1f%% PR=%.2fx | Regime=%s | Vol=%s], " +
            "kích thước vị thế tối ưu = %.2f%% NAV → %,d cp = %,.0f đ. " +
            "Stop: -%.2f%% | TP: +%.2f%% | R:R = 1:%.2f | MaxLoss = %,.0f đ. " +
            "Expected Value = %.4f%% per trade.",
            sym, atrPct, winRate*100, payoff, regime, "VOL",
            adaptivePct, shares, money, stopPct, tpPct, rr, maxLoss,
            winRate * tpPct - (1 - winRate) * stopPct);
    }

    private String buildRiskWarning(double regimeMult, double adaptivePct, double atrPct, double fullKelly) {
        if (regimeMult == 0.0) return "🚨 BEAR MARKET LOCKOUT: Tất cả lệnh mua bị khóa tự động bởi regime filter";
        if (fullKelly <= 0.0) return "⚠️ EDGE ÂM: Kelly < 0 — Chiến lược thiếu lợi thế thống kê, xem xét lại tham số";
        if (atrPct > 4.0) return "⚠️ EXTREME VOLATILITY: ATR > 4% — Giảm size 50% so với normal environment";
        if (adaptivePct > 20.0) return "⚠️ CONCENTRATION RISK: Size > 20% NAV — Kiểm tra tương quan với các vị thế hiện có";
        return "✅ Các điều kiện an toàn đã được kiểm tra và thông qua.";
    }

    /**
     * Trả về DTO với 0 shares khi Kelly edge âm — không mở vị thế.
     */
    private AdaptivePositionSizingDto buildZeroSizeDto(
            String sym, BigDecimal nav, BigDecimal price, String currentRegime,
            double fullKelly, double halfKelly, double p, double b,
            double atr14, double atrPct, int atrBasedShares, double atrBasedMoney, double atrBasedPct,
            double regimeMult, String regimeJustification, double volPctile, double volScale,
            String volEnvironment, double stopLossPct, double stopLossPrice,
            double takeProfitPct, double takeProfitPrice) {

        List<String> factors = List.of(
            String.format("🚫 Kelly Edge Âm: Full Kelly = %.2f%% ≤ 0 — Chiến lược không có lợi thế thống kê", fullKelly * 100),
            "❌ Quyết định: Từ chối mở vị thế để bảo toàn vốn tuyệt đối"
        );
        return AdaptivePositionSizingDto.builder()
            .symbol(sym).accountCapital(nav).currentPrice(price).currentRegime(currentRegime)
            .atr14(bd(atr14, 0)).atrPercent(bd(atrPct, 2))
            .targetDailyVolatility(bd(TARGET_DAILY_VOL_PCT * 100.0, 2))
            .atrBasedShares(bd(atrBasedShares, 0)).atrBasedAllocation(bd(atrBasedMoney, 0)).atrBasedPercent(bd(atrBasedPct, 2))
            .kellyWinRate(bd(p * 100.0, 1)).kellyPayoffRatio(bd(b, 2))
            .fullKellyPercent(bd(fullKelly * 100.0, 2)).halfKellyPercent(bd(halfKelly * 100.0, 2))
            .regimeMultiplier(bd(regimeMult, 2)).regimeJustification(regimeJustification)
            .volatilityPercentile(bd(volPctile, 1)).volatilityScaleFactor(bd(volScale, 2)).volatilityEnvironment(volEnvironment)
            .adaptiveAllocationPercent(BigDecimal.ZERO).adaptiveAllocationMoney(BigDecimal.ZERO)
            .adaptiveSharesToBuy(0).actualAllocationPercent(BigDecimal.ZERO)
            .stopLossPrice(bd(stopLossPrice, 0)).stopLossPercent(bd(stopLossPct, 2))
            .takeProfitPrice(bd(takeProfitPrice, 0)).takeProfitPercent(bd(takeProfitPct, 2))
            .maxLossIfStopHit(BigDecimal.ZERO).riskRewardRatio(bd(RR_RATIO, 2))
            .fixedPercent5Pct(bd(nav.doubleValue() * 0.05, 0))
            .kellyOnlyPercent(BigDecimal.ZERO).atrOnlyPercent(bd(nav.doubleValue() * atrBasedPct / 100.0, 0))
            .adaptiveAdvantage(BigDecimal.ZERO)
            .sizingVerdict(String.format("❌ TỪ CHỐI %s: Kelly Edge Âm (%.2f%%) — Không mở vị thế để bảo toàn vốn.", sym, fullKelly * 100))
            .sizingFactors(factors)
            .riskWarning("⚠️ EDGE ÂM: Kelly < 0 — Chiến lược thiếu lợi thế thống kê, xem xét lại tham số")
            .build();
    }

    private BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
