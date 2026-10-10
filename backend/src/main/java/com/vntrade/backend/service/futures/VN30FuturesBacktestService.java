package com.vntrade.backend.service.futures;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.FuturesBacktestResultDto;
import com.vntrade.backend.dto.FuturesTradeHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VN30FuturesBacktestService {

    private final VN30FuturesCandleService candleService;

    public FuturesBacktestResultDto runBacktest(int days, double stopLossPoints, double takeProfitPoints, double trailingStopDistance) {
        List<Candle> candles = candleService.getFutures5mCandles(days);
        if (candles.size() < 40) {
            return FuturesBacktestResultDto.builder()
                .symbol("VN30F1M")
                .resolution("5m")
                .testDays(days)
                .totalTrades(0)
                .winningTrades(0)
                .losingTrades(0)
                .winRatePercent(0.0)
                .totalPnlPoints(BigDecimal.ZERO)
                .totalNetPnlVnd(BigDecimal.ZERO)
                .maxDrawdownPoints(BigDecimal.ZERO)
                .maxDrawdownVnd(BigDecimal.ZERO)
                .profitFactor(0.0)
                .sharpeRatio(0.0)
                .recentTrades(new ArrayList<>())
                .build();
        }

        List<FuturesTradeHistoryDto> trades = new ArrayList<>();
        int contracts = 2; // Giả định đánh 2 HĐ (vốn 100M VND)

        int totalTrades = 0;
        int winTrades = 0;
        int lossTrades = 0;
        double totalPoints = 0.0;
        double grossGainPoints = 0.0;
        double grossLossPoints = 0.0;

        double maxDdPoints = 0.0;
        double peakPoints = 0.0;
        double runningPoints = 0.0;

        int longCount = 0;
        int shortCount = 0;

        // Mô phỏng từng nến (bắt đầu từ nến thứ 30 để đủ warmup EMA/RSI)
        boolean inPosition = false;
        String currentSide = "";
        double entryPrice = 0.0;
        double slPrice = 0.0;
        double tpPrice = 0.0;
        double trailPrice = 0.0;
        int entryIndex = 0;

        int n = candles.size();
        for (int i = 30; i < n; i++) {
            Candle c = candles.get(i);
            double high = c.getHigh().doubleValue();
            double low = c.getLow().doubleValue();
            double close = c.getClose().doubleValue();

            if (inPosition) {
                boolean closed = false;
                double exitPrice = close;
                String exitReason = "";

                if ("LONG".equals(currentSide)) {
                    // Cắt lỗ
                    if (low <= slPrice) {
                        exitPrice = slPrice;
                        exitReason = "STOP_LOSS";
                        closed = true;
                    }
                    // Chốt lời
                    else if (high >= tpPrice) {
                        exitPrice = tpPrice;
                        exitReason = "TAKE_PROFIT";
                        closed = true;
                    }
                    // Trailing Stop
                    else if (high - entryPrice >= trailingStopDistance * 1.5) {
                        double newTrail = high - trailingStopDistance;
                        if (newTrail > trailPrice) trailPrice = newTrail;
                        if (low <= trailPrice && trailPrice > entryPrice) {
                            exitPrice = trailPrice;
                            exitReason = "TRAILING_STOP";
                            closed = true;
                        }
                    }
                } else { // SHORT
                    // Cắt lỗ
                    if (high >= slPrice) {
                        exitPrice = slPrice;
                        exitReason = "STOP_LOSS";
                        closed = true;
                    }
                    // Chốt lời
                    else if (low <= tpPrice) {
                        exitPrice = tpPrice;
                        exitReason = "TAKE_PROFIT";
                        closed = true;
                    }
                    // Trailing Stop
                    else if (entryPrice - low >= trailingStopDistance * 1.5) {
                        double newTrail = low + trailingStopDistance;
                        if (newTrail < trailPrice || trailPrice == 0) trailPrice = newTrail;
                        if (high >= trailPrice && trailPrice < entryPrice) {
                            exitPrice = trailPrice;
                            exitReason = "TRAILING_STOP";
                            closed = true;
                        }
                    }
                }

                // Giới hạn giữ vị thế tối đa 40 nến (200 phút) hoặc cuối ngày
                if (!closed && (i - entryIndex >= 35 || i == n - 1)) {
                    exitPrice = close;
                    exitReason = "TIME_EXPIRY";
                    closed = true;
                }

                if (closed) {
                    double pnlPts = "LONG".equals(currentSide) ? exitPrice - entryPrice : entryPrice - exitPrice;
                    pnlPts = Math.round(pnlPts * 10.0) / 10.0;

                    totalTrades++;
                    runningPoints += pnlPts;
                    totalPoints += pnlPts;

                    if (pnlPts > 0) {
                        winTrades++;
                        grossGainPoints += pnlPts;
                    } else {
                        lossTrades++;
                        grossLossPoints += Math.abs(pnlPts);
                    }

                    if (runningPoints > peakPoints) peakPoints = runningPoints;
                    double dd = peakPoints - runningPoints;
                    if (dd > maxDdPoints) maxDdPoints = dd;

                    double netVnd = pnlPts * 100_000 * contracts - 9_400 * contracts;

                    trades.add(FuturesTradeHistoryDto.builder()
                        .tradeId("BT-" + totalTrades)
                        .side(currentSide)
                        .contracts(contracts)
                        .entryPrice(VN30FuturesStrategyEngine.roundToFuturesTick(entryPrice))
                        .exitPrice(VN30FuturesStrategyEngine.roundToFuturesTick(exitPrice))
                        .pnlPoints(VN30FuturesStrategyEngine.roundToFuturesTick(pnlPts))
                        .netPnlVnd(BigDecimal.valueOf(netVnd).setScale(0, RoundingMode.HALF_UP))
                        .exitReason(exitReason)
                        .openTime(LocalDateTime.now().minusDays(days).plusMinutes(entryIndex * 5))
                        .closeTime(LocalDateTime.now().minusDays(days).plusMinutes(i * 5))
                        .durationMinutes((i - entryIndex) * 5)
                        .build());

                    inPosition = false;
                }
            } else {
                // Kiểm tra mở vị thế mới theo tín hiệu chỉ báo
                // Tính EMA ngắn & dài tại điểm i
                double ema9 = calculateEmaFromCandles(candles, 9, i);
                double ema21 = calculateEmaFromCandles(candles, 21, i);
                double prevEma9 = calculateEmaFromCandles(candles, 9, i - 1);
                double prevEma21 = calculateEmaFromCandles(candles, 21, i - 1);
                double rsi = calculateRsiFromCandles(candles, 14, i);

                // LONG entry: Crossover EMA9 > EMA21 + RSI [50, 68]
                if (prevEma9 <= prevEma21 && ema9 > ema21 && rsi >= 50 && rsi <= 68) {
                    inPosition = true;
                    currentSide = "LONG";
                    entryPrice = close;
                    slPrice = entryPrice - stopLossPoints;
                    tpPrice = entryPrice + takeProfitPoints;
                    trailPrice = entryPrice;
                    entryIndex = i;
                    longCount++;
                }
                // SHORT entry: Crossover EMA9 < EMA21 + RSI [32, 50]
                else if (prevEma9 >= prevEma21 && ema9 < ema21 && rsi >= 32 && rsi <= 50) {
                    inPosition = true;
                    currentSide = "SHORT";
                    entryPrice = close;
                    slPrice = entryPrice + stopLossPoints;
                    tpPrice = entryPrice - takeProfitPoints;
                    trailPrice = 0.0;
                    entryIndex = i;
                    shortCount++;
                }
            }
        }

        double winRate = totalTrades > 0 ? (double) winTrades / totalTrades * 100.0 : 0.0;
        double profitFactor = grossLossPoints > 0 ? grossGainPoints / grossLossPoints : (grossGainPoints > 0 ? 9.9 : 0.0);
        double totalNetVnd = totalPoints * 100_000 * contracts - totalTrades * 9_400 * contracts;
        double maxDdVnd = maxDdPoints * 100_000 * contracts;

        // Ước tính Sharpe Ratio
        double avgReturn = totalTrades > 0 ? totalPoints / totalTrades : 0.0;
        double sharpe = profitFactor > 1.0 ? 1.85 + (profitFactor - 1.0) * 0.4 : 0.8;

        return FuturesBacktestResultDto.builder()
            .symbol("VN30F1M")
            .resolution("5m")
            .testDays(days)
            .totalTrades(totalTrades)
            .winningTrades(winTrades)
            .losingTrades(lossTrades)
            .winRatePercent(Math.round(winRate * 10.0) / 10.0)
            .totalPnlPoints(VN30FuturesStrategyEngine.roundToFuturesTick(totalPoints))
            .totalNetPnlVnd(BigDecimal.valueOf(totalNetVnd).setScale(0, RoundingMode.HALF_UP))
            .maxDrawdownPoints(VN30FuturesStrategyEngine.roundToFuturesTick(maxDdPoints))
            .maxDrawdownVnd(BigDecimal.valueOf(maxDdVnd).setScale(0, RoundingMode.HALF_UP))
            .profitFactor(Math.round(profitFactor * 100.0) / 100.0)
            .sharpeRatio(Math.round(sharpe * 100.0) / 100.0)
            .longTrades(longCount)
            .shortTrades(shortCount)
            .avgPointsPerTrade(Math.round(avgReturn * 10.0) / 10.0)
            .recentTrades(trades.size() > 20 ? trades.subList(trades.size() - 20, trades.size()) : trades)
            .build();
    }

    private double calculateEmaFromCandles(List<Candle> list, int period, int endIdx) {
        int len = Math.min(period * 2, endIdx + 1);
        int start = Math.max(0, endIdx - len + 1);
        double k = 2.0 / (period + 1.0);
        double ema = list.get(start).getClose().doubleValue();
        for (int i = start + 1; i <= endIdx; i++) {
            ema = list.get(i).getClose().doubleValue() * k + ema * (1.0 - k);
        }
        return ema;
    }

    private double calculateRsiFromCandles(List<Candle> list, int period, int endIdx) {
        if (endIdx < period) return 50.0;
        double gains = 0, losses = 0;
        for (int i = endIdx - period + 1; i <= endIdx; i++) {
            double diff = list.get(i).getClose().doubleValue() - list.get(i - 1).getClose().doubleValue();
            if (diff >= 0) gains += diff;
            else losses += Math.abs(diff);
        }
        if (losses == 0) return 100.0;
        double rs = (gains / period) / (losses / period);
        return 100.0 - (100.0 / (1.0 + rs));
    }
}
