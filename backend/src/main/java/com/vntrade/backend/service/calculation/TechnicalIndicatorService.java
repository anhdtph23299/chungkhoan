package com.vntrade.backend.service.calculation;

import com.vntrade.backend.dto.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class TechnicalIndicatorService {

    /**
     * Tính Simple Moving Average (SMA)
     */
    public List<BigDecimal> calculateSMA(List<Candle> candles, int period) {
        List<BigDecimal> smaList = new ArrayList<>();
        if (candles == null || candles.size() < period) return smaList;

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
            if (i >= period) {
                sum = sum.subtract(candles.get(i - period).getClose());
            }
            if (i >= period - 1) {
                smaList.add(sum.divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP));
            } else {
                smaList.add(null);
            }
        }
        return smaList;
    }

    /**
     * Tính Exponential Moving Average (EMA)
     */
    public List<BigDecimal> calculateEMA(List<Candle> candles, int period) {
        List<BigDecimal> emaList = new ArrayList<>();
        if (candles == null || candles.isEmpty()) return emaList;

        double multiplier = 2.0 / (period + 1.0);
        BigDecimal currentEma = null;

        for (int i = 0; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).getClose();
            if (i < period - 1) {
                emaList.add(null);
            } else if (i == period - 1) {
                // Initial SMA as first EMA
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = 0; j < period; j++) {
                    sum = sum.add(candles.get(j).getClose());
                }
                currentEma = sum.divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP);
                emaList.add(currentEma);
            } else {
                // EMA = (Close - PrevEMA) * Multiplier + PrevEMA
                double emaVal = (close.doubleValue() - currentEma.doubleValue()) * multiplier + currentEma.doubleValue();
                currentEma = BigDecimal.valueOf(emaVal).setScale(2, RoundingMode.HALF_UP);
                emaList.add(currentEma);
            }
        }
        return emaList;
    }

    /**
     * Tính Relative Strength Index (RSI 14)
     */
    public List<BigDecimal> calculateRSI(List<Candle> candles, int period) {
        List<BigDecimal> rsiList = new ArrayList<>();
        if (candles == null || candles.size() <= period) return rsiList;

        double avgGain = 0;
        double avgLoss = 0;

        for (int i = 0; i < candles.size(); i++) {
            if (i == 0) {
                rsiList.add(null);
                continue;
            }

            double change = candles.get(i).getClose().doubleValue() - candles.get(i - 1).getClose().doubleValue();
            double gain = Math.max(0, change);
            double loss = Math.max(0, -change);

            if (i < period) {
                avgGain += gain;
                avgLoss += loss;
                rsiList.add(null);
            } else if (i == period) {
                avgGain = (avgGain + gain) / period;
                avgLoss = (avgLoss + loss) / period;
                double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
                double rsi = 100 - (100 / (1 + rs));
                rsiList.add(BigDecimal.valueOf(rsi).setScale(2, RoundingMode.HALF_UP));
            } else {
                avgGain = (avgGain * (period - 1) + gain) / period;
                avgLoss = (avgLoss * (period - 1) + loss) / period;
                double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
                double rsi = 100 - (100 / (1 + rs));
                rsiList.add(BigDecimal.valueOf(rsi).setScale(2, RoundingMode.HALF_UP));
            }
        }
        return rsiList;
    }

    /**
     * Tính Average True Range (ATR) phục vụ đặt Stop Loss động theo biến động giá
     */
    public List<BigDecimal> calculateATR(List<Candle> candles, int period) {
        List<BigDecimal> atrList = new ArrayList<>();
        if (candles == null || candles.isEmpty()) return atrList;

        double prevAtr = 0;
        for (int i = 0; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            double tr;
            if (i == 0) {
                tr = curr.getHigh().subtract(curr.getLow()).doubleValue();
            } else {
                Candle prev = candles.get(i - 1);
                double hl = curr.getHigh().subtract(curr.getLow()).doubleValue();
                double hc = curr.getHigh().subtract(prev.getClose()).abs().doubleValue();
                double lc = curr.getLow().subtract(prev.getClose()).abs().doubleValue();
                tr = Math.max(hl, Math.max(hc, lc));
            }

            if (i < period - 1) {
                atrList.add(null);
                prevAtr += tr;
            } else if (i == period - 1) {
                prevAtr = (prevAtr + tr) / period;
                atrList.add(BigDecimal.valueOf(prevAtr).setScale(2, RoundingMode.HALF_UP));
            } else {
                prevAtr = (prevAtr * (period - 1) + tr) / period;
                atrList.add(BigDecimal.valueOf(prevAtr).setScale(2, RoundingMode.HALF_UP));
            }
        }
        return atrList;
    }

    /**
     * Tính Moving Average Convergence Divergence (MACD)
     */
    public List<com.vntrade.backend.dto.MacdPoint> calculateMACD(List<Candle> candles, int fastPeriod, int slowPeriod, int signalPeriod) {
        List<com.vntrade.backend.dto.MacdPoint> result = new ArrayList<>();
        if (candles == null || candles.size() < slowPeriod) return result;

        List<BigDecimal> fastEma = calculateEMA(candles, fastPeriod);
        List<BigDecimal> slowEma = calculateEMA(candles, slowPeriod);

        List<BigDecimal> macdLine = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            BigDecimal f = fastEma.get(i);
            BigDecimal s = slowEma.get(i);
            if (f != null && s != null) {
                macdLine.add(f.subtract(s).setScale(2, RoundingMode.HALF_UP));
            } else {
                macdLine.add(null);
            }
        }

        // Tính Signal Line (EMA signalPeriod của MACD Line)
        double signalMultiplier = 2.0 / (signalPeriod + 1.0);
        BigDecimal currentSignal = null;
        List<BigDecimal> signalLine = new ArrayList<>();

        int validMacdStartIndex = slowPeriod - 1;
        int signalCount = 0;
        BigDecimal signalSum = BigDecimal.ZERO;

        for (int i = 0; i < macdLine.size(); i++) {
            BigDecimal m = macdLine.get(i);
            if (m == null) {
                signalLine.add(null);
                continue;
            }

            signalCount++;
            if (signalCount < signalPeriod) {
                signalSum = signalSum.add(m);
                signalLine.add(null);
            } else if (signalCount == signalPeriod) {
                signalSum = signalSum.add(m);
                currentSignal = signalSum.divide(BigDecimal.valueOf(signalPeriod), 2, RoundingMode.HALF_UP);
                signalLine.add(currentSignal);
            } else {
                double sigVal = (m.doubleValue() - currentSignal.doubleValue()) * signalMultiplier + currentSignal.doubleValue();
                currentSignal = BigDecimal.valueOf(sigVal).setScale(2, RoundingMode.HALF_UP);
                signalLine.add(currentSignal);
            }
        }

        for (int i = 0; i < candles.size(); i++) {
            BigDecimal m = macdLine.get(i);
            BigDecimal s = signalLine.get(i);
            BigDecimal h = (m != null && s != null) ? m.subtract(s).setScale(2, RoundingMode.HALF_UP) : null;
            result.add(com.vntrade.backend.dto.MacdPoint.builder()
                .macd(m)
                .signal(s)
                .histogram(h)
                .build());
        }
        return result;
    }

    /**
     * Tính Bollinger Bands (Upper, Middle, Lower, Bandwidth)
     */
    public List<com.vntrade.backend.dto.BollingerBandsPoint> calculateBollingerBands(List<Candle> candles, int period, double multiplier) {
        List<com.vntrade.backend.dto.BollingerBandsPoint> result = new ArrayList<>();
        if (candles == null || candles.size() < period) return result;

        List<BigDecimal> sma = calculateSMA(candles, period);

        for (int i = 0; i < candles.size(); i++) {
            BigDecimal mid = sma.get(i);
            if (mid == null || i < period - 1) {
                result.add(null);
                continue;
            }

            double sumSqDiff = 0.0;
            double midVal = mid.doubleValue();
            for (int j = i - period + 1; j <= i; j++) {
                double diff = candles.get(j).getClose().doubleValue() - midVal;
                sumSqDiff += diff * diff;
            }
            double stdDev = Math.sqrt(sumSqDiff / period);
            BigDecimal upper = BigDecimal.valueOf(midVal + (multiplier * stdDev)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lower = BigDecimal.valueOf(midVal - (multiplier * stdDev)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal bandwidth = midVal > 0
                ? upper.subtract(lower).divide(mid, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            result.add(com.vntrade.backend.dto.BollingerBandsPoint.builder()
                .upper(upper)
                .middle(mid)
                .lower(lower)
                .bandwidth(bandwidth)
                .build());
        }
        return result;
    }

    /**
     * Tính SMA của Khối lượng giao dịch (Volume SMA)
     */
    public List<BigDecimal> calculateVolumeSMA(List<Candle> candles, int period) {
        List<BigDecimal> volSma = new ArrayList<>();
        if (candles == null || candles.size() < period) return volSma;

        double sum = 0;
        for (int i = 0; i < candles.size(); i++) {
            long vol = candles.get(i).getVolume() != null ? candles.get(i).getVolume() : 0L;
            sum += vol;
            if (i >= period) {
                long oldVol = candles.get(i - period).getVolume() != null ? candles.get(i - period).getVolume() : 0L;
                sum -= oldVol;
            }
            if (i >= period - 1) {
                volSma.add(BigDecimal.valueOf(sum / period).setScale(0, RoundingMode.HALF_UP));
            } else {
                volSma.add(null);
            }
        }
        return volSma;
    }
}
