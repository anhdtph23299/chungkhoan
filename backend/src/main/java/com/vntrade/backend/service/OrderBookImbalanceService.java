package com.vntrade.backend.service;

import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.OrderBookDto.OrderBookLevel;
import com.vntrade.backend.dto.OrderBookImbalanceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderBookImbalanceService {

    private final OrderBookService orderBookService;

    public OrderBookImbalanceDto analyzeMicrostructure(String symbol) {
        String sym = symbol != null ? symbol.toUpperCase().trim() : "FPT";
        OrderBookDto ob = orderBookService.getOrderBook(sym);

        List<OrderBookLevel> bids = ob.getBidLevels();
        List<OrderBookLevel> asks = ob.getAskLevels();

        long totalBidVol = 0L;
        long totalAskVol = 0L;
        double weightedBid = 0.0;
        double weightedAsk = 0.0;

        OrderBookLevel bestBid = (bids != null && !bids.isEmpty()) ? bids.get(0) : null;
        OrderBookLevel bestAsk = (asks != null && !asks.isEmpty()) ? asks.get(0) : null;

        BigDecimal bestBidPrice = bestBid != null ? bestBid.getPrice() : ob.getCurrentPrice();
        BigDecimal bestAskPrice = bestAsk != null ? bestAsk.getPrice() : ob.getCurrentPrice();

        long bestBidVol = bestBid != null ? bestBid.getVolume() : 100_000L;
        long bestAskVol = bestAsk != null ? bestAsk.getVolume() : 100_000L;

        // Tính trọng số độ sâu 3 tầng giá (Tầng 1 trọng số 1.0, Tầng 2 trọng số 0.6, Tầng 3 trọng số 0.3)
        double[] weights = {1.0, 0.6, 0.3};

        if (bids != null) {
            for (int i = 0; i < bids.size(); i++) {
                long v = bids.get(i).getVolume();
                totalBidVol += v;
                double w = i < weights.length ? weights[i] : 0.2;
                weightedBid += v * w;
            }
        }

        if (asks != null) {
            for (int i = 0; i < asks.size(); i++) {
                long v = asks.get(i).getVolume();
                totalAskVol += v;
                double w = i < weights.length ? weights[i] : 0.2;
                weightedAsk += v * w;
            }
        }

        // Tính Order Book Imbalance (OBI) [-1.0, +1.0]
        double totalWeighted = weightedBid + weightedAsk;
        BigDecimal obi = totalWeighted > 0
            ? BigDecimal.valueOf((weightedBid - weightedAsk) / totalWeighted).setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Tính Mid-Price và Micro-Price (Stoikov formula)
        BigDecimal midPrice = bestBidPrice.add(bestAskPrice).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        long denom = bestBidVol + bestAskVol;
        BigDecimal microPrice;
        if (denom > 0) {
            BigDecimal num = bestAskPrice.multiply(BigDecimal.valueOf(bestBidVol))
                .add(bestBidPrice.multiply(BigDecimal.valueOf(bestAskVol)));
            microPrice = num.divide(BigDecimal.valueOf(denom), 2, RoundingMode.HALF_UP);
        } else {
            microPrice = midPrice;
        }

        BigDecimal microPremiumPct = midPrice.compareTo(BigDecimal.ZERO) > 0
            ? microPrice.subtract(midPrice).divide(midPrice, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Phát hiện Tường Lệnh (Liquidity Wall)
        String wallDetected = "NONE";
        BigDecimal wallPrice = BigDecimal.ZERO;
        Long wallVolume = 0L;
        BigDecimal wallProportion = BigDecimal.ZERO;

        if (asks != null && totalAskVol > 0) {
            for (OrderBookLevel ask : asks) {
                long v = ask.getVolume();
                double ratio = (double) v / totalAskVol;
                if (ratio >= 0.40 && v >= 200_000L) {
                    wallDetected = "ASK_WALL_RESISTANCE";
                    wallPrice = ask.getPrice();
                    wallVolume = v;
                    wallProportion = BigDecimal.valueOf(ratio * 100).setScale(1, RoundingMode.HALF_UP);
                    break;
                }
            }
        }

        if (bids != null && totalBidVol > 0 && "NONE".equals(wallDetected)) {
            for (OrderBookLevel bid : bids) {
                long v = bid.getVolume();
                double ratio = (double) v / totalBidVol;
                if (ratio >= 0.40 && v >= 200_000L) {
                    wallDetected = "BID_WALL_SUPPORT";
                    wallPrice = bid.getPrice();
                    wallVolume = v;
                    wallProportion = BigDecimal.valueOf(ratio * 100).setScale(1, RoundingMode.HALF_UP);
                    break;
                }
            }
        }

        // Ước tính trượt giá (Slippage) theo Basis Points
        BigDecimal tickSpread = bestAskPrice.subtract(bestBidPrice);
        BigDecimal slippageBps = midPrice.compareTo(BigDecimal.ZERO) > 0
            ? tickSpread.divide(midPrice, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(10000)).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(10.0);

        // Đánh giá Tín hiệu Vi cấu trúc (Microstructure Signal)
        String signal;
        String execution;
        if (obi.compareTo(BigDecimal.valueOf(0.35)) > 0 && microPremiumPct.compareTo(BigDecimal.ZERO) > 0) {
            signal = "BULLISH_AGGRESSIVE_FLOW";
            execution = "TWAP_ICEBERG_ACCUMULATE";
        } else if (obi.compareTo(BigDecimal.valueOf(0.15)) > 0) {
            signal = "STEALTH_ACCUMULATION";
            execution = "PASSIVE_LIMIT_POST_ONLY";
        } else if (obi.compareTo(BigDecimal.valueOf(-0.15)) < 0 && obi.compareTo(BigDecimal.valueOf(-0.35)) >= 0) {
            signal = "BEARISH_PRESSURE";
            execution = "PASSIVE_LIMIT_POST_ONLY";
        } else if (obi.compareTo(BigDecimal.valueOf(-0.35)) < 0) {
            signal = "DUMPING";
            execution = "IMMEDIATE_CROSS_SPREAD";
        } else {
            signal = "BALANCED";
            execution = "PASSIVE_LIMIT_POST_ONLY";
        }

        String verdict = String.format(
            "VI CẤU TRÚC SỔ LỆNH %s: OBI = %+,.2f%% (Dư Mua %s cp vs Dư Bán %s cp). " +
            "Micro-Price (Stoikov) = %s đ (%+,.2f%% so với Mid-Price %s đ). " +
            "%s. Chiến lược khớp lệnh tối ưu: %s (Độ trượt giá dự báo: %.1f bps).",
            sym,
            obi.multiply(BigDecimal.valueOf(100)).doubleValue(),
            String.format("%,d", totalBidVol),
            String.format("%,d", totalAskVol),
            microPrice.toPlainString(),
            microPremiumPct.doubleValue(),
            midPrice.toPlainString(),
            "NONE".equals(wallDetected) ? "Không có tường lệnh cản trở" : ("Phát hiện " + wallDetected + " tại giá " + wallPrice + " (khối lượng " + wallVolume + " cp - " + wallProportion + "% độ sâu)"),
            execution,
            slippageBps.doubleValue()
        );

        return OrderBookImbalanceDto.builder()
            .symbol(sym)
            .currentPrice(ob.getCurrentPrice())
            .midPrice(midPrice)
            .microPrice(microPrice)
            .microPricePremiumPercent(microPremiumPct)
            .totalBidVolume(totalBidVol)
            .totalAskVolume(totalAskVol)
            .orderBookImbalanceRatio(obi)
            .wallDetected(wallDetected)
            .wallPrice(wallPrice)
            .wallVolume(wallVolume)
            .wallProportionPercent(wallProportion)
            .optimalExecutionStrategy(execution)
            .estimatedSlippageBps(slippageBps)
            .microstructureSignal(signal)
            .institutionalVerdict(verdict)
            .build();
    }
}
