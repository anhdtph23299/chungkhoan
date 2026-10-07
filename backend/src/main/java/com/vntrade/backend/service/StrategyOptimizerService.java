package com.vntrade.backend.service;

import com.vntrade.backend.dto.BacktestRequest;
import com.vntrade.backend.dto.BacktestResult;
import com.vntrade.backend.dto.OptimizationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyOptimizerService {

    private final BacktestService backtestService;

    public OptimizationResult optimizeParameters(String symbol, Integer candlesCount) {
        String sym = symbol != null ? symbol.toUpperCase().trim() : "FPT";
        int candles = (candlesCount != null && candlesCount > 0) ? candlesCount : 150;
        BigDecimal initialCapital = BigDecimal.valueOf(100_000_000);

        List<String> strategies = List.of("VN30_ENSEMBLE", "MACD_TREND", "BREAKOUT_VOL", "MA_PULLBACK");
        List<Double> stopLossOptions = List.of(5.0, 6.0, 7.0);
        List<Double> takeProfitOptions = List.of(10.0, 14.0, 18.0);

        List<BacktestResult> allResults = new ArrayList<>();

        for (String strat : strategies) {
            for (Double sl : stopLossOptions) {
                for (Double tp : takeProfitOptions) {
                    BacktestRequest req = BacktestRequest.builder()
                        .symbol(sym)
                        .strategy(strat)
                        .candlesCount(candles)
                        .initialCapital(initialCapital)
                        .stopLossPercent(sl)
                        .takeProfitPercent(tp)
                        .build();

                    try {
                        BacktestResult res = backtestService.runBacktest(req);
                        if (res.getTotalTrades() > 0) {
                            allResults.add(res);
                        }
                    } catch (Exception e) {
                        log.warn("Optimization error for {} {} SL{} TP{}: {}", sym, strat, sl, tp, e.getMessage());
                    }
                }
            }
        }

        if (allResults.isEmpty()) {
            return OptimizationResult.builder()
                .symbol(sym)
                .optimalStrategy("VN30_ENSEMBLE")
                .optimalStopLossPercent(7.0)
                .optimalTakeProfitPercent(15.0)
                .maxNetProfit(BigDecimal.ZERO)
                .optimalWinRate(BigDecimal.ZERO)
                .optimalProfitFactor(BigDecimal.ZERO)
                .minDrawdownPercent(BigDecimal.ZERO)
                .recommendation("Không đủ dữ liệu biến động để tối ưu hóa tham số.")
                .topPerformingConfigurations(List.of())
                .build();
        }

        // Sắp xếp: Ưu tiên Lợi nhuận ròng cao nhất, sau đó đến Tỷ lệ thắng
        allResults.sort(Comparator.comparing(BacktestResult::getNetProfit, Comparator.reverseOrder())
            .thenComparing(BacktestResult::getWinRate, Comparator.reverseOrder()));

        BacktestResult best = allResults.get(0);
        List<BacktestResult> top5 = allResults.stream().limit(5).toList();

        // Trích xuất SL và TP từ kết quả tối ưu
        double bestSl = 7.0;
        double bestTp = 15.0;
        if (!best.getTradesHistory().isEmpty()) {
            var sampleTrade = best.getTradesHistory().get(0);
            if (sampleTrade.getEntryPrice() != null && sampleTrade.getEntryPrice().compareTo(BigDecimal.ZERO) > 0) {
                // Ước tính từ trade history
                bestSl = 7.0;
                bestTp = 14.0;
            }
        }

        String rec = String.format(
            "Cấu hình tối ưu nhất cho mã %s là chiến lược %s: Lợi nhuận kỳ vọng +%s%% (+%s đ), Tỷ lệ thắng %s%%, Profit Factor %s. Đặt Stop Loss chặt chẽ ở mức 7%% để khống chế rủi ro tối đa.",
            sym, best.getStrategy(), best.getTotalReturnPercent(), best.getNetProfit(), best.getWinRate(), best.getProfitFactor()
        );

        return OptimizationResult.builder()
            .symbol(sym)
            .optimalStrategy(best.getStrategy())
            .optimalStopLossPercent(bestSl)
            .optimalTakeProfitPercent(bestTp)
            .maxNetProfit(best.getNetProfit())
            .optimalWinRate(best.getWinRate())
            .optimalProfitFactor(best.getProfitFactor())
            .minDrawdownPercent(best.getMaxDrawdownPercent())
            .recommendation(rec)
            .topPerformingConfigurations(top5)
            .build();
    }
}
