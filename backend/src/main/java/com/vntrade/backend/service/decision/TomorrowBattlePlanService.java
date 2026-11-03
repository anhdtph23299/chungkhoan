package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.*;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import com.vntrade.backend.service.execution.AutoTradingBotService;
import com.vntrade.backend.service.futures.VN30FuturesStrategyEngine;
import com.vntrade.backend.service.futures.VN30FuturesTradingService;
import com.vntrade.backend.service.marketdata.StockPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TomorrowBattlePlanService {

    private final MarketRegimeDetectionService marketRegimeService;
    private final VN30FuturesStrategyEngine futuresStrategyEngine;
    private final VN30FuturesTradingService futuresTradingService;
    private final OversoldBounceDetectorService oversoldBounceService;
    private final StrategyService strategyService;
    private final StockPriceService stockPriceService;
    private final TradeRepository tradeRepository;
    private final AutoTradingBotService autoTradingBotService;

    public TomorrowBattlePlanDto generateBattlePlan() {
        MarketRegimeDto regime = marketRegimeService.detectMarketRegime();
        FuturesQuoteDto fQuote = futuresStrategyEngine.getCurrentQuote();
        FuturesSignalDto fSignal = futuresStrategyEngine.generateSignal();
        List<FuturesPositionDto> openFutures = futuresTradingService.getOpenPositions();
        List<Trade> openTrades = tradeRepository.findByStatusOrderByTradeDateDesc("open");

        BigDecimal currentNav = autoTradingBotService.getCurrentNav();
        BigDecimal availableCash = autoTradingBotService.getAvailableCash();
        BigDecimal cashRatio = currentNav.compareTo(BigDecimal.ZERO) > 0
            ? availableCash.divide(currentNav, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(100);

        BigDecimal vniPrice = regime.getVnIndexLevel();
        BigDecimal vniChg = regime.getVnIndexChangePercent();
        double vniChgVal = vniChg != null ? vniChg.doubleValue() : 0.0;
        BigDecimal basis = fQuote.getBasis() != null ? fQuote.getBasis() : BigDecimal.ZERO;

        String smartMoneyFootprint;
        if (basis.compareTo(BigDecimal.valueOf(2.0)) >= 0 && vniChgVal < 0) {
            smartMoneyFootprint = String.format("DẤU CHÂN CÁ MẬP HẤP THỤ ĐÁY: Dù cơ sở đỏ (%.2f%%), hợp đồng VN30F1M rút chân mạnh giữ Basis DƯƠNG %+.1f điểm (@ %.1f). Tổ chức đang gom LONG đón nhịp bật hồi kỹ thuật!",
                vniChgVal, basis.doubleValue(), fQuote.getCurrentPrice().doubleValue());
        } else if (basis.compareTo(BigDecimal.valueOf(-2.0)) <= 0) {
            smartMoneyFootprint = String.format("CẢNH BÁO PHÒNG HỘ PHÁI SINH: Basis chiết khấu âm %.1f điểm (@ %.1f). Dòng tiền lớn đang ưu tiên vị thế SHORT phòng vệ rủi ro.",
                basis.doubleValue(), fQuote.getCurrentPrice().doubleValue());
        } else {
            smartMoneyFootprint = String.format("CÂN BẰNG CUNG CẦU: VN30F1M giao dịch tại %.1f (Basis %+.1f điểm), bám sát chỉ số VN30 cơ sở (%.1f).",
                fQuote.getCurrentPrice().doubleValue(), basis.doubleValue(), fQuote.getVn30IndexPrice().doubleValue());
        }

        String masterVerdict = String.format(
            "CHIẾN LƯỢC ĐỘNG CƠ KÉP (DUAL-ENGINE): (1) Kiếm tiền tươi T+0 ngay trong phiên bằng Phái Sinh VN30F1M (%s @ %.1f, kỳ vọng +400k đến +900k/HĐ). " +
            "(2) Trên Cơ sở: Khóa mua đuổi Breakout giả, chỉ giải ngân Pilot 18-20%% NAV vào Siêu Cổ Phiếu Kháng Bão (Xanh ngược VNI) hoặc Bắt Đáy Rút Chân RSI < 30. " +
            "(3) Chốt lời 50%% đút túi ngay khi chạm +6.0%% và kéo Stop Loss về hòa vốn!",
            fSignal.getAction(), fQuote.getCurrentPrice().doubleValue()
        );

        // 1. Xây dựng kế hoạch Phái sinh T+0
        BigDecimal fEntry = fSignal.getEntryPrice() != null ? fSignal.getEntryPrice() : fQuote.getCurrentPrice();
        BigDecimal fSl = fSignal.getStopLossPrice() != null ? fSignal.getStopLossPrice() : fEntry.subtract(BigDecimal.valueOf(2.5));
        BigDecimal fTp1 = fSignal.getTakeProfit1() != null ? fSignal.getTakeProfit1() : fEntry.add(BigDecimal.valueOf(4.5));
        BigDecimal fTp2 = fSignal.getTakeProfit2() != null ? fSignal.getTakeProfit2() : fEntry.add(BigDecimal.valueOf(9.0));
        BigDecimal ptsDiff = fTp2.subtract(fEntry).abs();
        BigDecimal expectedProfit2Contracts = ptsDiff.multiply(BigDecimal.valueOf(100_000)).multiply(BigDecimal.valueOf(2));

        String activeFutSummary = "Chưa mở vị thế — Bot đang trực canh điểm nổ T+0";
        if (!openFutures.isEmpty()) {
            FuturesPositionDto p = openFutures.get(0);
            activeFutSummary = String.format("ĐANG GIỮ %s %d HĐ @ %.1f | Giá hiện tại: %.1f | PnL: %+.1f pts (%+,.0f đ)",
                p.getSide(), p.getContracts(), p.getEntryPrice().doubleValue(), p.getCurrentPrice().doubleValue(),
                p.getPnlPoints().doubleValue(), p.getNetPnlVnd().doubleValue());
        }

        double curF = fQuote.getCurrentPrice().doubleValue();
        TomorrowBattlePlanDto.FuturesT0Plan futuresPlan = TomorrowBattlePlanDto.FuturesT0Plan.builder()
            .symbol("VN30F1M")
            .activeSignal(fSignal.getAction())
            .strategyName(fSignal.getStrategyName())
            .confidenceScore(fSignal.getConfidenceScore())
            .currentPrice(fQuote.getCurrentPrice())
            .basis(basis)
            .vwap(fSignal.getVwap())
            .rsi(Math.round(fSignal.getRsi() * 10.0) / 10.0)
            .macdHist(fSignal.getMacdHist())
            .entryPrice(fEntry)
            .stopLossPrice(fSl)
            .takeProfit1(fTp1)
            .takeProfit2(fTp2)
            .expectedProfit2ContractsVnd(expectedProfit2Contracts)
            .longScenarioTrigger(String.format("Kịch bản LONG T+0: Giữ vững trên %.1f & vượt EMA9/VWAP -> Mở LONG 2 HĐ, chốt 1 HĐ tại %.1f (+450k), gồng 1 HĐ lên %.1f (+900k), SL: %.1f.",
                curF - 1.5, curF + 4.5, curF + 9.0, curF - 2.5))
            .shortScenarioTrigger(String.format("Kịch bản SHORT T+0: Nếu hồi yếu bị dội ngược thủng %.1f -> Mở SHORT 2 HĐ thuận xu hướng, chốt lời tại %.1f - %.1f (+900k đến +1.8M), SL: %.1f.",
                curF - 2.0, curF - 6.5, curF - 11.0, curF + 2.5))
            .activePositionSummary(activeFutSummary)
            .build();

        // 2. Quét Top Siêu Cổ Phiếu Kháng Bão (Anti-Market RS Leaders)
        List<StockScanResult> scans = strategyService.scanAllStocks();
        BigDecimal pilotBudget = currentNav.multiply(BigDecimal.valueOf(0.20)).min(availableCash);

        List<TomorrowBattlePlanDto.TacticalStockPick> antiMarketList = scans.stream()
            .filter(s -> s.getPrice() != null && s.getChangePercent() != null)
            .sorted((a, b) -> {
                double alphaA = a.getChangePercent().doubleValue() - vniChgVal;
                double alphaB = b.getChangePercent().doubleValue() - vniChgVal;
                return Double.compare(alphaB, alphaA);
            })
            .limit(4)
            .map(s -> {
                BigDecimal price = s.getPrice();
                BigDecimal alpha = s.getChangePercent().subtract(vniChg != null ? vniChg : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                BigDecimal harvest6 = QuantitativeStrategyEngine.roundToVietnameseTick(price.multiply(BigDecimal.valueOf(1.06)));
                BigDecimal tp = s.getTargetPrice() != null ? s.getTargetPrice() : QuantitativeStrategyEngine.roundToVietnameseTick(price.multiply(BigDecimal.valueOf(1.12)));
                BigDecimal sl = s.getStopLoss() != null ? s.getStopLoss() : QuantitativeStrategyEngine.roundToVietnameseTick(price.multiply(BigDecimal.valueOf(0.955)));
                int shares = pilotBudget.compareTo(BigDecimal.ZERO) > 0
                    ? (pilotBudget.divide(price, 0, RoundingMode.FLOOR).intValue() / 100) * 100 : 0;

                return TomorrowBattlePlanDto.TacticalStockPick.builder()
                    .symbol(s.getSymbol())
                    .category("ANTI_MARKET_LEADER")
                    .currentPrice(price)
                    .changePercent(s.getChangePercent().setScale(2, RoundingMode.HALF_UP))
                    .alphaVsVnIndex(alpha)
                    .rsi(s.getRsi())
                    .score(s.getConfidenceScore())
                    .entryPrice(price)
                    .stopLoss(sl)
                    .harvest6PercentPrice(harvest6)
                    .targetPrice(tp)
                    .suggestedShares(shares)
                    .suggestedBudgetVnd(price.multiply(BigDecimal.valueOf(shares)))
                    .catalystReason(String.format("Khỏe hơn VN-Index %+.2f%% (%s%% vs %s%%) | Giữ nền giá vững, cản trên mỏng, ưu tiên chạy đầu tiên khi thị trường hồi!",
                        alpha.doubleValue(), s.getChangePercent().setScale(2, RoundingMode.HALF_UP), vniChg != null ? vniChg.setScale(2, RoundingMode.HALF_UP) : "0"))
                    .build();
            })
            .toList();

        // 3. Quét Top Cổ Phiếu Bắt Đáy Hoảng Loạn Rút Chân (Oversold Spring)
        OversoldBounceDto bounceDto = oversoldBounceService.scanOversoldBounceCandidates();
        List<TomorrowBattlePlanDto.TacticalStockPick> oversoldList = new ArrayList<>();
        if (bounceDto != null && bounceDto.getCandidates() != null) {
            oversoldList = bounceDto.getCandidates().stream()
                .limit(4)
                .map(c -> {
                    BigDecimal price = c.getCurrentPrice();
                    BigDecimal harvest6 = QuantitativeStrategyEngine.roundToVietnameseTick(price.multiply(BigDecimal.valueOf(1.06)));
                    int shares = pilotBudget.compareTo(BigDecimal.ZERO) > 0
                        ? (pilotBudget.divide(price, 0, RoundingMode.FLOOR).intValue() / 100) * 100 : 0;

                    return TomorrowBattlePlanDto.TacticalStockPick.builder()
                        .symbol(c.getSymbol())
                        .category("OVERSOLD_SPRING")
                        .currentPrice(price)
                        .changePercent(c.getChangePercent() != null ? c.getChangePercent().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                        .alphaVsVnIndex(BigDecimal.ZERO)
                        .rsi(c.getRsi14())
                        .score(c.getBounceScore())
                        .entryPrice(price)
                        .stopLoss(c.getTacticalStopLoss())
                        .harvest6PercentPrice(harvest6)
                        .targetPrice(c.getTacticalTargetPrice())
                        .suggestedShares(shares)
                        .suggestedBudgetVnd(price.multiply(BigDecimal.valueOf(shares)))
                        .catalystReason(String.format("RSI=%.1f (%s) | Score: %d/100 | R:R = 1:%.1f — Săn nhịp bật hồi chữ V về MA20!",
                            c.getRsi14() != null ? c.getRsi14().doubleValue() : 30.0,
                            c.getDivergenceSignal(), c.getBounceScore(), c.getRiskRewardRatio()))
                        .build();
                })
                .toList();
        }

        // 4. Kế hoạch hành động cho các mã đang nắm giữ (ví dụ: PVS)
        List<TomorrowBattlePlanDto.PortfolioPositionAction> posActions = new ArrayList<>();
        for (Trade t : openTrades) {
            StockQuote q = stockPriceService.getQuote(t.getSymbol());
            BigDecimal curPrice = (q != null && q.getPrice() != null && q.getPrice().compareTo(BigDecimal.ZERO) > 0)
                ? q.getPrice() : t.getPrice();
            BigDecimal pnlPerShare = curPrice.subtract(t.getPrice());
            BigDecimal pnlVnd = pnlPerShare.multiply(BigDecimal.valueOf(t.getQuantity()));
            BigDecimal pnlPct = pnlPerShare.divide(t.getPrice(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal harvest6 = QuantitativeStrategyEngine.roundToVietnameseTick(t.getPrice().multiply(BigDecimal.valueOf(1.06)));

            long daysHeld = t.getTradeDate() != null ? java.time.temporal.ChronoUnit.DAYS.between(t.getTradeDate(), LocalDate.now()) : 0;
            String settlement = daysHeld == 0 ? "T+0 (Vừa mua)" : (daysHeld == 1 ? "T+1 (Chờ hàng về chiều mai 13:00)" : "T+2.5+ (Sẵn sàng bán 100%)");

            String instruction = String.format(
                "Canh chốt lời 50%% (%d cp) tại %,.0f đ (+6.0%%) để đút túi tiền tươi & dời SL lên hòa vốn. Chốt toàn bộ tại %,.0f đ. Bảo vệ nghiêm ngặt SL tại %,.0f đ.",
                (t.getQuantity() / 200) * 100, harvest6.doubleValue(),
                t.getTakeProfit() != null ? t.getTakeProfit().doubleValue() : harvest6.doubleValue(),
                t.getStopLoss() != null ? t.getStopLoss().doubleValue() : t.getPrice().multiply(BigDecimal.valueOf(0.96)).doubleValue()
            );

            posActions.add(TomorrowBattlePlanDto.PortfolioPositionAction.builder()
                .symbol(t.getSymbol())
                .quantity(t.getQuantity())
                .entryPrice(t.getPrice())
                .currentPrice(curPrice)
                .pnlVnd(pnlVnd)
                .pnlPercent(pnlPct)
                .strategy(t.getStrategy())
                .stopLoss(t.getStopLoss())
                .harvest6PercentPrice(harvest6)
                .takeProfit(t.getTakeProfit())
                .settlementStatus(settlement)
                .actionInstruction(instruction)
                .build());
        }

        return TomorrowBattlePlanDto.builder()
            .generatedAt(LocalDateTime.now())
            .sessionTitle("KẾ HOẠCH TÁC CHIẾN KIẾM TIỀN (ĐỘNG CƠ KÉP: CƠ SỞ + PHÁI SINH T+0)")
            .marketRegime(regime.getCurrentRegime())
            .marketRegimeDisplay(regime.getRegimeDisplayName())
            .vnIndexPrice(vniPrice)
            .vnIndexChangePercent(vniChg)
            .vn30FuturesPrice(fQuote.getCurrentPrice())
            .vn30Basis(basis)
            .smartMoneyFootprint(smartMoneyFootprint)
            .masterStrategyVerdict(masterVerdict)
            .currentNav(currentNav)
            .availableCash(availableCash)
            .cashRatioPercent(cashRatio)
            .openEquityPositionsCount(openTrades.size())
            .openFuturesPositionsCount(openFutures.size())
            .futuresT0Plan(futuresPlan)
            .antiMarketLeaders(antiMarketList)
            .oversoldBouncePicks(oversoldList)
            .openPositionActions(posActions)
            .build();
    }
}
