package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.vntrade.backend.service.marketdata.CandleDataService;
import com.vntrade.backend.service.calculation.TechnicalIndicatorService;
import com.vntrade.backend.service.marketdata.StockPriceService;
import com.vntrade.backend.service.risk.RiskService;

@Service
@RequiredArgsConstructor
@Slf4j
public class OversoldBounceDetectorService {

    private final StockPriceService stockPriceService;
    private final CandleDataService candleDataService;
    private final TechnicalIndicatorService indicatorService;
    private final RiskService riskService;

    public OversoldBounceDto scanOversoldBounceCandidates() {
        List<String> symbols = StrategyService.FOCUS_SYMBOLS;
        List<OversoldBounceDto.OversoldCandidate> candidates = new ArrayList<>();

        for (String sym : symbols) {
            try {
                StockQuote quote = stockPriceService.getQuote(sym);
                if (quote == null || quote.getPrice() == null || quote.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                List<Candle> candles = candleDataService.getHistoricalCandles(sym, 120);
                if (candles == null || candles.size() < 30) {
                    continue;
                }

                int lastIdx = candles.size() - 1;
                Candle currentCandle = candles.get(lastIdx);
                BigDecimal price = quote.getPrice();

                List<BigDecimal> rsiList = indicatorService.calculateRSI(candles, 14);
                List<BollingerBandsPoint> bbList = indicatorService.calculateBollingerBands(candles, 20, 2.0);
                List<BigDecimal> sma200List = indicatorService.calculateSMA(candles, Math.min(200, candles.size()));
                List<BigDecimal> sma20List = indicatorService.calculateSMA(candles, 20);

                BigDecimal rsi = (rsiList != null && rsiList.get(lastIdx) != null) ? rsiList.get(lastIdx) : BigDecimal.valueOf(50);
                BollingerBandsPoint bb = (bbList != null && bbList.get(lastIdx) != null) ? bbList.get(lastIdx) : null;
                BigDecimal sma200 = (sma200List != null && sma200List.get(lastIdx) != null) ? sma200List.get(lastIdx) : price.multiply(BigDecimal.valueOf(0.90));
                BigDecimal sma20 = (sma20List != null && sma20List.get(lastIdx) != null) ? sma20List.get(lastIdx) : price.multiply(BigDecimal.valueOf(1.05));

                double rsiVal = rsi.doubleValue();
                BigDecimal bbLower = bb != null ? bb.getLower() : price.multiply(BigDecimal.valueOf(0.95));

                // Tiêu chí Quá Bán Hoảng Loạn:
                // 1. RSI <= 36.0 (Vùng quá bán hoặc cận quá bán)
                // HOẶC 2. Giá rơi chạm / đâm thủng dải Bollinger dưới
                // HOẶC 3. Giá rơi về vùng hỗ trợ cứng SMA200 (khoảng cách <= 3%)
                boolean isRsiOversold = rsiVal <= 36.0;
                boolean isBelowBollinger = price.compareTo(bbLower) <= 0;
                BigDecimal distSma200 = sma200.compareTo(BigDecimal.ZERO) > 0
                    ? price.subtract(sma200).divide(sma200, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
                boolean isNearSma200 = Math.abs(distSma200.doubleValue()) <= 3.5;

                if (isRsiOversold || isBelowBollinger || (isNearSma200 && rsiVal <= 42.0)) {
                    int bounceScore = 50;
                    String divSignal = "EXTREME_OVERSOLD";

                    if (rsiVal <= 30.0) bounceScore += 25;
                    else if (rsiVal <= 35.0) bounceScore += 15;

                    if (isBelowBollinger) bounceScore += 15;
                    if (isNearSma200) bounceScore += 10;

                    // Kiểm tra nến rút chân (Hammer / Pinbar)
                    BigDecimal high = currentCandle.getHigh() != null ? currentCandle.getHigh() : price;
                    BigDecimal low = currentCandle.getLow() != null ? currentCandle.getLow() : price;
                    BigDecimal open = currentCandle.getOpen() != null ? currentCandle.getOpen() : price;
                    BigDecimal close = currentCandle.getClose() != null ? currentCandle.getClose() : price;

                    BigDecimal body = close.subtract(open).abs();
                    BigDecimal lowerShadow = open.min(close).subtract(low);
                    if (lowerShadow.compareTo(body.multiply(BigDecimal.valueOf(1.5))) >= 0) {
                        bounceScore += 15;
                        divSignal = "PINBAR_HAMMER_REVERSAL";
                    }

                    // Quản trị rủi ro chiến thuật: Cắt lỗ chặt chẽ 4%, Chốt lời kỳ vọng test lại MA20 (8% - 10%)
                    BigDecimal tacticalSl = QuantitativeStrategyEngine.roundToVietnameseTick(
                        price.multiply(BigDecimal.valueOf(0.96))
                    );
                    BigDecimal tacticalTp = QuantitativeStrategyEngine.roundToVietnameseTick(
                        sma20.max(price.multiply(BigDecimal.valueOf(1.08)))
                    );

                    BigDecimal riskDist = price.subtract(tacticalSl);
                    BigDecimal rewardDist = tacticalTp.subtract(price);
                    double rr = riskDist.compareTo(BigDecimal.ZERO) > 0
                        ? rewardDist.divide(riskDist, 2, RoundingMode.HALF_UP).doubleValue()
                        : 2.0;

                    String sector = riskService.getSectorForSymbol(sym);
                    String tactic = bounceScore >= 75
                        ? "ĐỦ ĐIỀU KIỆN MỞ VỊ THẾ DÒ ĐÁY CHIẾN THUẬT (5-10% NAV)"
                        : "THEO DÕI NẾN ĐẢO CHIỀU TẠI VÙNG HỖ TRỢ";

                    candidates.add(OversoldBounceDto.OversoldCandidate.builder()
                        .symbol(sym)
                        .sector(sector)
                        .currentPrice(price)
                        .changePercent(quote.getChangePercent())
                        .rsi14(rsi.setScale(1, RoundingMode.HALF_UP))
                        .bollingerLower(bbLower)
                        .sma200(sma200)
                        .distanceToSma200Percent(distSma200.setScale(2, RoundingMode.HALF_UP))
                        .divergenceSignal(divSignal)
                        .bounceScore(Math.min(100, bounceScore))
                        .suggestedTacticalAllocationPercent(BigDecimal.valueOf(7.5)) // 7.5% NAV đệm an toàn
                        .tacticalStopLoss(tacticalSl)
                        .tacticalTargetPrice(tacticalTp)
                        .riskRewardRatio(rr)
                        .executionTactic(tactic)
                        .build());
                }
            } catch (Exception e) {
                log.debug("Lỗi quét oversold cho {}: {}", sym, e.getMessage());
            }
        }

        candidates.sort(Comparator.comparingInt(OversoldBounceDto.OversoldCandidate::getBounceScore).reversed());

        int count = candidates.size();
        String panicStatus;
        String macroVerdict;

        if (count >= 8) {
            panicStatus = "EXTREME_PANIC";
            macroVerdict = "TOÀN THỊ TRƯỜNG RƠI VÀO VÙNG QUÁ BÁN HOẢNG LOẠN: Lực cung bán tháo cực đại, xác suất hình thành đáy chữ V hoặc nhịp hồi kỹ thuật cao. Cho phép giải ngân dò đáy 1-2 mã dẫn dắt có nến rút chân.";
        } else if (count >= 3) {
            panicStatus = "MODERATE_OVERSOLD";
            macroVerdict = "XUẤT HIỆN CỔ PHIẾU CHIẾT KHẤU SÂU: Một số mã trụ cột đã lùi về dải Bollinger dưới và MA200. Chọn lọc giải ngân tỷ trọng nhỏ (5-10% NAV) với Stop Loss nghiêm ngặt 4%.";
        } else {
            panicStatus = "NORMAL_CORRECTION";
            macroVerdict = "THỊ TRƯỜNG CHƯA ĐẠT ĐIỂM QUÁ BÁN TOÀN DIỆN: Kiên nhẫn chờ đợi tín hiệu bùng nổ theo đà hoặc bứt phá RRG Leader thay vì vội vàng bắt dao rơi.";
        }

        return OversoldBounceDto.builder()
            .scanTime(LocalDateTime.now())
            .totalSymbolsScanned(symbols.size())
            .oversoldCandidatesCount(count)
            .marketPanicStatus(panicStatus)
            .macroVerdict(macroVerdict)
            .candidates(candidates)
            .build();
    }
}