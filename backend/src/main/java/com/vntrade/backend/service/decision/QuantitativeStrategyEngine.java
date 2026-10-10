package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.BollingerBandsPoint;
import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.MacdPoint;
import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.dto.StockScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import com.vntrade.backend.service.calculation.TechnicalIndicatorService;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuantitativeStrategyEngine {

    private final TechnicalIndicatorService indicatorService;

    public StockScanResult evaluateRealTimeData(String symbol, StockQuote quote, List<Candle> candles) {
        BigDecimal price = quote.getPrice().compareTo(BigDecimal.ZERO) > 0 ? quote.getPrice() : BigDecimal.valueOf(30000);
        String sym = symbol.toUpperCase().trim();

        if (candles == null || candles.size() < 30) {
            return buildFallbackResult(sym, quote, price);
        }

        int lastIdx = candles.size() - 1;
        Candle currentCandle = candles.get(lastIdx);
        Candle prevCandle = candles.get(lastIdx - 1);

        List<BigDecimal> sma20 = indicatorService.calculateSMA(candles, 20);
        List<BigDecimal> sma50 = indicatorService.calculateSMA(candles, 50);
        List<BigDecimal> sma200 = indicatorService.calculateSMA(candles, Math.min(200, candles.size()));
        List<BigDecimal> ema20 = indicatorService.calculateEMA(candles, 20);
        List<BigDecimal> rsi14 = indicatorService.calculateRSI(candles, 14);
        List<BigDecimal> atr14 = indicatorService.calculateATR(candles, 14);
        List<MacdPoint> macd = indicatorService.calculateMACD(candles, 12, 26, 9);
        List<BollingerBandsPoint> bb = indicatorService.calculateBollingerBands(candles, 20, 2.0);
        List<BigDecimal> volSma20 = indicatorService.calculateVolumeSMA(candles, 20);

        BigDecimal curSma20 = sma20.get(lastIdx) != null ? sma20.get(lastIdx) : price.multiply(BigDecimal.valueOf(0.98));
        BigDecimal curSma50 = sma50.get(lastIdx) != null ? sma50.get(lastIdx) : price.multiply(BigDecimal.valueOf(0.95));
        BigDecimal curSma200 = sma200.get(lastIdx) != null ? sma200.get(lastIdx) : price.multiply(BigDecimal.valueOf(0.90));
        BigDecimal curEma20 = ema20.get(lastIdx) != null ? ema20.get(lastIdx) : curSma20;
        BigDecimal curRsi = rsi14.get(lastIdx) != null ? rsi14.get(lastIdx) : BigDecimal.valueOf(50);
        BigDecimal curAtr = (atr14 != null && atr14.get(lastIdx) != null) ? atr14.get(lastIdx) : price.multiply(BigDecimal.valueOf(0.025));
        MacdPoint curMacd = macd.get(lastIdx);
        BollingerBandsPoint curBb = bb.get(lastIdx);
        BigDecimal curVolSma = (volSma20 != null && volSma20.get(lastIdx) != null) ? volSma20.get(lastIdx) : BigDecimal.valueOf(1_000_000);

        long currentVol = currentCandle.getVolume() != null ? currentCandle.getVolume() : 1_000_000L;
        BigDecimal volRatio = curVolSma.compareTo(BigDecimal.ZERO) > 0
            ? BigDecimal.valueOf(currentVol).divide(curVolSma, 2, RoundingMode.HALF_UP)
            : BigDecimal.ONE;

        // 1. Scoring Factors
        int trendScore = 0;
        if (price.compareTo(curEma20) > 0) trendScore += 10;
        if (curEma20.compareTo(curSma50) > 0) trendScore += 10;
        if (curSma50.compareTo(curSma200) > 0) trendScore += 10;

        int momentumScore = 0;
        double rsiVal = curRsi.doubleValue();
        if (rsiVal >= 48 && rsiVal <= 65) {
            momentumScore += 15; // Golden momentum zone
        } else if (rsiVal > 65) {
            momentumScore += 12; // Strong bullish momentum
        } else if (rsiVal >= 35 && rsiVal < 48) {
            momentumScore += 8; // Accumulation
        }

        if (curMacd != null && curMacd.getHistogram() != null) {
            if (curMacd.getHistogram().compareTo(BigDecimal.ZERO) >= 0) {
                momentumScore += 10;
            } else if (lastIdx >= 1 && macd.get(lastIdx - 1) != null && macd.get(lastIdx - 1).getHistogram() != null
                && curMacd.getHistogram().compareTo(macd.get(lastIdx - 1).getHistogram()) > 0) {
                momentumScore += 5; // Turning up
            }
        }

        int volumeScore = 0;
        double vr = volRatio.doubleValue();
        if (vr >= 1.4) {
            volumeScore += 25; // Big smart money volume breakout
        } else if (vr >= 1.2) {
            volumeScore += 18;
        } else if (vr >= 0.9) {
            volumeScore += 12;
        } else {
            volumeScore += 5;
        }

        int riskRewardScore = 0;
        // Stop loss: 1.5 ATR dưới giá hiện tại, khống chế tối đa 7%
        BigDecimal atrSlDistance = curAtr.multiply(BigDecimal.valueOf(1.5)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal maxSlPercentDistance = price.multiply(BigDecimal.valueOf(0.07)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal slDistance = atrSlDistance.min(maxSlPercentDistance);
        if (slDistance.compareTo(BigDecimal.ZERO) <= 0) slDistance = maxSlPercentDistance;
        BigDecimal stopLoss = roundToVietnameseTick(price.subtract(slDistance));

        // Take Profit: 2.5 ATR trên giá hiện tại
        BigDecimal tpDistance = curAtr.multiply(BigDecimal.valueOf(2.5)).setScale(0, RoundingMode.HALF_UP);
        if (tpDistance.compareTo(slDistance.multiply(BigDecimal.valueOf(2))) < 0) {
            tpDistance = slDistance.multiply(BigDecimal.valueOf(2.2)).setScale(0, RoundingMode.HALF_UP);
        }
        BigDecimal targetPrice = roundToVietnameseTick(price.add(tpDistance));

        double rrRatio = slDistance.compareTo(BigDecimal.ZERO) > 0
            ? tpDistance.divide(slDistance, 2, RoundingMode.HALF_UP).doubleValue()
            : 2.0;

        if (rrRatio >= 2.0) riskRewardScore += 20;
        else if (rrRatio >= 1.5) riskRewardScore += 10;

        int totalScore = trendScore + momentumScore + volumeScore + riskRewardScore;
        totalScore = Math.min(100, Math.max(20, totalScore));

        // Signal Classification
        String signalType;
        String signalTitle;
        String signalDesc;
        String action;

        if (totalScore >= 75 && vr >= 1.25 && price.compareTo(curEma20) > 0) {
            signalType = "BREAKOUT_VOL";
            signalTitle = "Bùng nổ vượt kháng cự với Vol tăng " + volRatio + "x";
            signalDesc = String.format("Dòng tiền Big Boys tích lũy mạnh, EMA20 (%s) dốc lên, RSI %s khỏe.", curEma20, curRsi);
            action = "STRONG_BUY";
        } else if (totalScore >= 70 && rsiVal >= 45 && rsiVal <= 60 && Math.abs(price.doubleValue() - curEma20.doubleValue()) / curEma20.doubleValue() < 0.02) {
            signalType = "PULLBACK_MA20";
            signalTitle = "Kéo ngược kiểm định hỗ trợ EMA20 chuẩn VSA";
            signalDesc = String.format("Nến rút chân quanh EMA20 (%s), rủi ro thấp R:R 1:%.1f.", curEma20, rrRatio);
            action = "BUY";
        } else if (curMacd != null && curMacd.getHistogram() != null && curMacd.getHistogram().compareTo(BigDecimal.ZERO) > 0 && totalScore >= 65) {
            signalType = "MACD_GOLDEN_CROSS";
            signalTitle = "Tín hiệu MACD phân kỳ dương hướng lên";
            signalDesc = "Động lượng tăng giá trung hạn được củng cố, hỗ trợ bởi dòng tiền.";
            action = "BUY";
        } else if (totalScore >= 50) {
            signalType = "CONSOLIDATION";
            signalTitle = "Tích lũy nền giá đi ngang chờ xu hướng";
            signalDesc = "Biến động hẹp, khối lượng trung bình. Đặt lệnh điều kiện theo dõi.";
            action = "WATCH";
        } else {
            signalType = "WARNING_BEAR";
            signalTitle = "Áp lực bán điều chỉnh dưới đường trung bình";
            signalDesc = "Cổ phiếu suy yếu, không thỏa mãn tiêu chí an toàn vốn.";
            action = "AVOID";
        }

        BigDecimal buyZoneLow = roundToVietnameseTick(price.multiply(BigDecimal.valueOf(0.985)));
        String buyZone = buyZoneLow + " - " + price;

        return StockScanResult.builder()
            .symbol(sym)
            .name(getCompanyName(sym))
            .exchange("HOSE")
            .price(price)
            .change(quote.getChange())
            .changePercent(quote.getChangePercent())
            .volume(currentVol)
            .volumeRatio(volRatio)
            .rsi(curRsi)
            .ma20(curSma20)
            .ma50(curSma50)
            .ma200(curSma200)
            .signalType(signalType)
            .signalTitle(signalTitle)
            .signalDescription(signalDesc)
            .buyZone(buyZone)
            .stopLoss(stopLoss)
            .targetPrice(targetPrice)
            .confidenceScore(totalScore)
            .action(action)
            .build();
    }

    private String getCompanyName(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "FPT" -> "Tập đoàn FPT";
            case "HPG" -> "Tập đoàn Hòa Phát";
            case "SSI" -> "Chứng khoán SSI";
            case "TCB" -> "Ngân hàng Kỹ thương Techcombank";
            case "MWG" -> "Thế Giới Di Động";
            case "VHM" -> "Vinhomes";
            case "VCB" -> "Ngân hàng Ngoại thương Vietcombank";
            case "MBB" -> "Ngân hàng Quân Đội";
            case "DGC" -> "Hóa chất Đức Giang";
            case "STB" -> "Ngân hàng Sacombank";
            case "VIX" -> "Chứng khoán VIX";
            case "KBC" -> "Đô thị Kinh Bắc";
            default -> "Cổ phiếu " + symbol.toUpperCase();
        };
    }

    private StockScanResult buildFallbackResult(String symbol, StockQuote quote, BigDecimal price) {
        return StockScanResult.builder()
            .symbol(symbol)
            .name(getCompanyName(symbol))
            .exchange("HOSE")
            .price(price)
            .change(quote.getChange())
            .changePercent(quote.getChangePercent())
            .volume(quote.getVolume() != null ? quote.getVolume() : 2_000_000L)
            .volumeRatio(BigDecimal.valueOf(1.1))
            .rsi(BigDecimal.valueOf(50))
            .ma20(price.multiply(BigDecimal.valueOf(0.98)).setScale(0, RoundingMode.HALF_UP))
            .ma50(price.multiply(BigDecimal.valueOf(0.95)).setScale(0, RoundingMode.HALF_UP))
            .ma200(price.multiply(BigDecimal.valueOf(0.90)).setScale(0, RoundingMode.HALF_UP))
            .signalType("CONSOLIDATION")
            .signalTitle("Đang theo dõi tích lũy")
            .signalDescription("Chưa đủ dữ liệu nến định lượng chi tiết.")
            .buyZone(price.multiply(BigDecimal.valueOf(0.98)).setScale(0, RoundingMode.HALF_UP) + " - " + price)
            .stopLoss(price.multiply(BigDecimal.valueOf(0.93)).setScale(0, RoundingMode.HALF_UP))
            .targetPrice(price.multiply(BigDecimal.valueOf(1.15)).setScale(0, RoundingMode.HALF_UP))
            .confidenceScore(60)
            .action("WATCH")
            .build();
    }

    public static BigDecimal roundToVietnameseTick(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        double p = price.doubleValue();
        double tick = p < 10000 ? 10.0 : (p < 50000 ? 50.0 : 100.0);
        long rounded = Math.round(p / tick);
        return BigDecimal.valueOf(rounded * (long) tick);
    }
}