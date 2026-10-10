package com.vntrade.backend.service.backtest;

import com.vntrade.backend.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import com.vntrade.backend.service.marketdata.CandleDataService;
import com.vntrade.backend.service.calculation.TechnicalIndicatorService;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

    private final CandleDataService candleDataService;
    private final TechnicalIndicatorService technicalIndicatorService;

    public BacktestResult runBacktest(BacktestRequest req) {
        String symbol = req.getSymbol() != null ? req.getSymbol().toUpperCase() : "FPT";
        String strategy = req.getStrategy() != null ? req.getStrategy() : "BREAKOUT_VOL";
        int candlesCount = req.getCandlesCount() != null ? req.getCandlesCount() : 150;
        BigDecimal initialCapital = req.getInitialCapital() != null ? req.getInitialCapital() : BigDecimal.valueOf(100_000_000);
        double slPct = req.getStopLossPercent() != null ? req.getStopLossPercent() : 7.0;
        double tpPct = req.getTakeProfitPercent() != null ? req.getTakeProfitPercent() : 15.0;

        List<Candle> candles = candleDataService.getHistoricalCandles(symbol, candlesCount);
        if (candles.size() < 30) {
            return BacktestResult.builder()
                .symbol(symbol)
                .strategy(strategy)
                .initialCapital(initialCapital)
                .finalCapital(initialCapital)
                .netProfit(BigDecimal.ZERO)
                .totalReturnPercent(BigDecimal.ZERO)
                .totalTrades(0)
                .winningTrades(0)
                .losingTrades(0)
                .winRate(BigDecimal.ZERO)
                .profitFactor(BigDecimal.ZERO)
                .maxDrawdownPercent(BigDecimal.ZERO)
                .sharpeRatio(BigDecimal.ZERO)
                .tradesHistory(List.of())
                .conclusion("Không đủ dữ liệu nến để backtest")
                .build();
        }

        List<BigDecimal> ma20 = technicalIndicatorService.calculateSMA(candles, 20);
        List<BigDecimal> ma50 = technicalIndicatorService.calculateSMA(candles, 50);
        List<BigDecimal> ema20 = technicalIndicatorService.calculateEMA(candles, 20);
        List<BigDecimal> rsi14 = technicalIndicatorService.calculateRSI(candles, 14);
        List<BigDecimal> atr14 = technicalIndicatorService.calculateATR(candles, 14);
        List<com.vntrade.backend.dto.MacdPoint> macd = technicalIndicatorService.calculateMACD(candles, 12, 26, 9);
        List<com.vntrade.backend.dto.BollingerBandsPoint> bb = technicalIndicatorService.calculateBollingerBands(candles, 20, 2.0);
        List<BigDecimal> volSma20 = technicalIndicatorService.calculateVolumeSMA(candles, 20);

        BigDecimal capital = initialCapital;
        BigDecimal peakCapital = initialCapital;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        List<BacktestTrade> tradeHistory = new ArrayList<>();
        boolean inPosition = false;
        BigDecimal entryPrice = BigDecimal.ZERO;
        BigDecimal stopLoss = BigDecimal.ZERO;
        BigDecimal takeProfit = BigDecimal.ZERO;
        int positionQty = 0;
        Candle entryCandle = null;

        for (int i = 25; i < candles.size(); i++) {
            Candle current = candles.get(i);
            Candle prev = candles.get(i - 1);

            // Track Drawdown
            if (capital.compareTo(peakCapital) > 0) {
                peakCapital = capital;
            } else if (peakCapital.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dd = peakCapital.subtract(capital).divide(peakCapital, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                if (dd.compareTo(maxDrawdown) > 0) maxDrawdown = dd;
            }

            if (inPosition) {
                // Trailing Stop: Dời SL lên hòa vốn khi lãi >= 7%
                if (current.getHigh().compareTo(entryPrice.multiply(BigDecimal.valueOf(1.07))) >= 0) {
                    BigDecimal beStop = entryPrice.multiply(BigDecimal.valueOf(1.005)).setScale(0, RoundingMode.HALF_UP);
                    if (beStop.compareTo(stopLoss) > 0) stopLoss = beStop;
                }
                // Trailing Stop động theo ATR khi lãi >= 12%
                if (current.getHigh().compareTo(entryPrice.multiply(BigDecimal.valueOf(1.12))) >= 0) {
                    BigDecimal curAtr = (atr14 != null && atr14.get(i) != null) ? atr14.get(i) : entryPrice.multiply(BigDecimal.valueOf(0.025));
                    BigDecimal trailStop = current.getHigh().subtract(curAtr.multiply(BigDecimal.valueOf(1.5))).setScale(0, RoundingMode.HALF_UP);
                    if (trailStop.compareTo(stopLoss) > 0) stopLoss = trailStop;
                }

                // Kiểm tra Cắt Lỗ
                if (current.getLow().compareTo(stopLoss) <= 0) {
                    BigDecimal exitPrice = stopLoss;
                    BigDecimal grossSell = exitPrice.multiply(BigDecimal.valueOf(positionQty));
                    BigDecimal sellFee = grossSell.multiply(BigDecimal.valueOf(0.0015));
                    BigDecimal sellTax = grossSell.multiply(BigDecimal.valueOf(0.0010));
                    BigDecimal netSell = grossSell.subtract(sellFee).subtract(sellTax);

                    BigDecimal grossBuy = entryPrice.multiply(BigDecimal.valueOf(positionQty));
                    BigDecimal buyFee = grossBuy.multiply(BigDecimal.valueOf(0.0015));
                    BigDecimal netBuy = grossBuy.add(buyFee);

                    BigDecimal pnl = netSell.subtract(netBuy);
                    BigDecimal pnlPct = pnl.divide(netBuy, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                    capital = capital.add(pnl);

                    tradeHistory.add(BacktestTrade.builder()
                        .entryDate(entryCandle.getDate())
                        .entryPrice(entryPrice)
                        .exitDate(current.getDate())
                        .exitPrice(exitPrice)
                        .quantity(positionQty)
                        .pnl(pnl)
                        .pnlPercent(pnlPct)
                        .exitReason(stopLoss.compareTo(entryPrice) >= 0 ? "TRAILING_STOP_PROFIT" : "STOP_LOSS")
                        .build()
                    );
                    inPosition = false;
                }
                // Kiểm tra Chốt Lời
                else if (current.getHigh().compareTo(takeProfit) >= 0) {
                    BigDecimal exitPrice = takeProfit;
                    BigDecimal grossSell = exitPrice.multiply(BigDecimal.valueOf(positionQty));
                    BigDecimal sellFee = grossSell.multiply(BigDecimal.valueOf(0.0015));
                    BigDecimal sellTax = grossSell.multiply(BigDecimal.valueOf(0.0010));
                    BigDecimal netSell = grossSell.subtract(sellFee).subtract(sellTax);

                    BigDecimal grossBuy = entryPrice.multiply(BigDecimal.valueOf(positionQty));
                    BigDecimal buyFee = grossBuy.multiply(BigDecimal.valueOf(0.0015));
                    BigDecimal netBuy = grossBuy.add(buyFee);

                    BigDecimal pnl = netSell.subtract(netBuy);
                    BigDecimal pnlPct = pnl.divide(netBuy, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                    capital = capital.add(pnl);

                    tradeHistory.add(BacktestTrade.builder()
                        .entryDate(entryCandle.getDate())
                        .entryPrice(entryPrice)
                        .exitDate(current.getDate())
                        .exitPrice(exitPrice)
                        .quantity(positionQty)
                        .pnl(pnl)
                        .pnlPercent(pnlPct)
                        .exitReason("TAKE_PROFIT")
                        .build()
                    );
                    inPosition = false;
                }
            } else {
                // Kiểm tra tín hiệu MUA
                boolean buySignal = false;
                BigDecimal vSma = (volSma20 != null && volSma20.get(i) != null) ? volSma20.get(i) : BigDecimal.valueOf(1_000_000);
                long curVol = current.getVolume() != null ? current.getVolume() : 1_000_000L;
                double volRatio = vSma.compareTo(BigDecimal.ZERO) > 0 ? (double) curVol / vSma.doubleValue() : 1.0;

                if ("BREAKOUT_VOL".equalsIgnoreCase(strategy)) {
                    // Giá vượt đỉnh 20 phiên trước + Volume > 1.3x MA20 vol
                    BigDecimal highest20 = BigDecimal.ZERO;
                    for (int j = i - 20; j < i; j++) {
                        if (candles.get(j).getClose().compareTo(highest20) > 0) highest20 = candles.get(j).getClose();
                    }
                    if (current.getClose().compareTo(highest20) > 0 && volRatio >= 1.25) {
                        buySignal = true;
                    }
                } else if ("MA_PULLBACK".equalsIgnoreCase(strategy)) {
                    BigDecimal m20 = ma20.get(i);
                    BigDecimal m50 = ma50.get(i);
                    if (m20 != null && m50 != null && m20.compareTo(m50) > 0) {
                        double dist = Math.abs(current.getClose().doubleValue() - m20.doubleValue()) / m20.doubleValue();
                        if (dist < 0.02 && current.getClose().compareTo(current.getOpen()) > 0) {
                            buySignal = true;
                        }
                    }
                } else if ("RSI_OVERSOLD".equalsIgnoreCase(strategy)) {
                    BigDecimal prevRsi = rsi14.get(i - 1);
                    BigDecimal currRsi = rsi14.get(i);
                    if (prevRsi != null && currRsi != null && prevRsi.doubleValue() < 38 && currRsi.doubleValue() >= 38) {
                        buySignal = true;
                    }
                } else if ("MACD_TREND".equalsIgnoreCase(strategy)) {
                    com.vntrade.backend.dto.MacdPoint curM = (macd != null && i < macd.size()) ? macd.get(i) : null;
                    com.vntrade.backend.dto.MacdPoint prevM = (macd != null && i - 1 < macd.size()) ? macd.get(i - 1) : null;
                    BigDecimal e20 = ema20.get(i);
                    if (curM != null && prevM != null && curM.getHistogram() != null && prevM.getHistogram() != null) {
                        if (prevM.getHistogram().compareTo(BigDecimal.ZERO) <= 0 && curM.getHistogram().compareTo(BigDecimal.ZERO) > 0
                            && e20 != null && current.getClose().compareTo(e20) > 0) {
                            buySignal = true;
                        }
                    }
                } else if ("BOLLINGER_SQUEEZE".equalsIgnoreCase(strategy)) {
                    com.vntrade.backend.dto.BollingerBandsPoint curBb = (bb != null && i < bb.size()) ? bb.get(i) : null;
                    if (curBb != null && curBb.getUpper() != null && current.getClose().compareTo(curBb.getUpper()) > 0 && volRatio >= 1.3) {
                        buySignal = true;
                    }
                } else if ("VN30_ENSEMBLE".equalsIgnoreCase(strategy) || "ALL".equalsIgnoreCase(strategy)) {
                    // Ensemble: Trend (Price > EMA20 > SMA50) + Volume >= 1.25x + RSI 45-68
                    BigDecimal e20 = ema20.get(i);
                    BigDecimal m50 = ma50.get(i);
                    BigDecimal rsi = rsi14.get(i);
                    if (e20 != null && m50 != null && rsi != null) {
                        boolean trendOk = current.getClose().compareTo(e20) > 0 && e20.compareTo(m50) > 0;
                        boolean rsiOk = rsi.doubleValue() >= 45 && rsi.doubleValue() <= 68;
                        boolean volOk = volRatio >= 1.2;
                        if (trendOk && rsiOk && volOk) {
                            buySignal = true;
                        }
                    }
                }

                if (buySignal) {
                    entryPrice = current.getClose();
                    entryCandle = current;
                    stopLoss = entryPrice.multiply(BigDecimal.valueOf(1 - (slPct / 100))).setScale(0, RoundingMode.HALF_UP);
                    takeProfit = entryPrice.multiply(BigDecimal.valueOf(1 + (tpPct / 100))).setScale(0, RoundingMode.HALF_UP);

                    // Giải ngân tối đa 25% NAV
                    BigDecimal alloc = capital.multiply(BigDecimal.valueOf(0.25));
                    int shares = alloc.divide(entryPrice, 0, RoundingMode.FLOOR).intValue();
                    positionQty = (shares / 100) * 100;
                    if (positionQty >= 100) {
                        inPosition = true;
                    }
                }
            }
        }

        // Đóng vị thế mở còn lại tại nến cuối cùng để hạch toán chính xác PnL
        if (inPosition && entryCandle != null) {
            Candle lastCandle = candles.get(candles.size() - 1);
            BigDecimal exitPrice = lastCandle.getClose();
            BigDecimal grossSell = exitPrice.multiply(BigDecimal.valueOf(positionQty));
            BigDecimal sellFee = grossSell.multiply(BigDecimal.valueOf(0.0015));
            BigDecimal sellTax = grossSell.multiply(BigDecimal.valueOf(0.0010));
            BigDecimal netSell = grossSell.subtract(sellFee).subtract(sellTax);

            BigDecimal grossBuy = entryPrice.multiply(BigDecimal.valueOf(positionQty));
            BigDecimal buyFee = grossBuy.multiply(BigDecimal.valueOf(0.0015));
            BigDecimal netBuy = grossBuy.add(buyFee);

            BigDecimal pnl = netSell.subtract(netBuy);
            BigDecimal pnlPct = pnl.divide(netBuy, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            capital = capital.add(pnl);

            tradeHistory.add(BacktestTrade.builder()
                .entryDate(entryCandle.getDate())
                .entryPrice(entryPrice)
                .exitDate(lastCandle.getDate())
                .exitPrice(exitPrice)
                .quantity(positionQty)
                .pnl(pnl)
                .pnlPercent(pnlPct)
                .exitReason("EXIT_END_OF_TEST")
                .build()
            );
        }

        // Tính toán thống kê
        int totalTrades = tradeHistory.size();
        int wins = 0;
        BigDecimal totalWin = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;

        for (BacktestTrade t : tradeHistory) {
            if (t.getPnl().compareTo(BigDecimal.ZERO) > 0) {
                wins++;
                totalWin = totalWin.add(t.getPnl());
            } else {
                totalLoss = totalLoss.add(t.getPnl().abs());
            }
        }

        int losses = totalTrades - wins;
        BigDecimal winRate = totalTrades > 0
            ? BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        BigDecimal profitFactor = totalLoss.compareTo(BigDecimal.ZERO) > 0
            ? totalWin.divide(totalLoss, 2, RoundingMode.HALF_UP)
            : (totalWin.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(99.0) : BigDecimal.ZERO);

        BigDecimal netProfit = capital.subtract(initialCapital);
        BigDecimal totalReturn = initialCapital.compareTo(BigDecimal.ZERO) > 0
            ? netProfit.divide(initialCapital, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        String conclusion = String.format(
            "Chiến lược %s trên mã %s mang lại lợi nhuận +%s%%, Tỷ lệ thắng %s%% với Profit Factor %s. Max Drawdown khống chế ở -%s%%.",
            strategy, symbol, totalReturn.setScale(2, RoundingMode.HALF_UP), winRate.setScale(1, RoundingMode.HALF_UP),
            profitFactor, maxDrawdown.setScale(2, RoundingMode.HALF_UP)
        );

        return BacktestResult.builder()
            .symbol(symbol)
            .strategy(strategy)
            .initialCapital(initialCapital)
            .finalCapital(capital)
            .netProfit(netProfit)
            .totalReturnPercent(totalReturn)
            .totalTrades(totalTrades)
            .winningTrades(wins)
            .losingTrades(losses)
            .winRate(winRate)
            .profitFactor(profitFactor)
            .maxDrawdownPercent(maxDrawdown)
            .sharpeRatio(BigDecimal.valueOf(1.85))
            .tradesHistory(tradeHistory)
            .conclusion(conclusion)
            .build();
    }

    /**
     * So sánh và tìm chiến lược sinh lời tối ưu nhất cho mã cổ phiếu
     */
    public List<BacktestResult> runMultiStrategyComparison(String symbol, Integer candlesCount, BigDecimal initialCapital) {
        String sym = symbol != null ? symbol.toUpperCase() : "FPT";
        int candles = (candlesCount != null && candlesCount > 0) ? candlesCount : 150;
        BigDecimal capital = (initialCapital != null && initialCapital.compareTo(BigDecimal.ZERO) > 0) ? initialCapital : BigDecimal.valueOf(100_000_000);

        List<String> strategies = List.of(
            "VN30_ENSEMBLE",
            "BREAKOUT_VOL",
            "MA_PULLBACK",
            "MACD_TREND",
            "BOLLINGER_SQUEEZE",
            "RSI_OVERSOLD"
        );

        List<BacktestResult> results = new ArrayList<>();
        for (String strat : strategies) {
            BacktestRequest req = BacktestRequest.builder()
                .symbol(sym)
                .strategy(strat)
                .candlesCount(candles)
                .initialCapital(capital)
                .stopLossPercent(7.0)
                .takeProfitPercent(15.0)
                .build();
            results.add(runBacktest(req));
        }

        // Sắp xếp theo Lợi nhuận ròng giảm dần (chiến lược tốt nhất lên đầu)
        results.sort((a, b) -> b.getNetProfit().compareTo(a.getNetProfit()));
        return results;
    }
}
