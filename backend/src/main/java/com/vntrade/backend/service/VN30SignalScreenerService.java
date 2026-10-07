package com.vntrade.backend.service;

import com.vntrade.backend.dto.SignalScreenerDto;
import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.dto.StockScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VN30SignalScreenerService {

    private final StrategyService strategyService;
    private final StockPriceService stockPriceService;

    public List<SignalScreenerDto> screenVN30Signals() {
        List<SignalScreenerDto> signals = new ArrayList<>();
        List<StockScanResult> scans = strategyService.scanAllStocks();

        for (StockScanResult scan : scans) {
            String symbol = scan.getSymbol();
            StockQuote quote = stockPriceService.getQuote(symbol);
            BigDecimal price = (quote.getPrice() != null && quote.getPrice().compareTo(BigDecimal.ZERO) > 0)
                ? quote.getPrice()
                : scan.getPrice();

            int score = scan.getConfidenceScore();
            String recommendation;
            double winProb;

            if (score >= 80) {
                recommendation = "MUA MẠNH (Hội tụ 4 trụ cột định lượng)";
                winProb = 78.5;
            } else if (score >= 70) {
                recommendation = "MUA (Đạt tiêu chuẩn vào lệnh)";
                winProb = 71.0;
            } else if (score >= 55) {
                recommendation = "NẮM GIỮ (Theo dõi biến động)";
                winProb = 58.0;
            } else {
                recommendation = "QUAN SÁT (Chưa đủ điều kiện giải ngân)";
                winProb = 45.0;
            }

            // Tính các mốc giá theo kỷ luật Việt Nam chuẩn bước giá sàn HOSE/HNX:
            // Stop Loss = -7%
            BigDecimal stopLoss = QuantitativeStrategyEngine.roundToVietnameseTick(
                price.multiply(BigDecimal.valueOf(0.93))
            );
            // Target 1 (Gặt hái tiền mặt 50% hàng ngày) = +10%
            BigDecimal target1 = QuantitativeStrategyEngine.roundToVietnameseTick(
                price.multiply(BigDecimal.valueOf(1.10))
            );
            // Target 2 (Chốt lời toàn phần) = +18%
            BigDecimal target2 = QuantitativeStrategyEngine.roundToVietnameseTick(
                price.multiply(BigDecimal.valueOf(1.18))
            );

            BigDecimal risk = price.subtract(stopLoss);
            BigDecimal reward = target1.subtract(price);
            double rr = (risk.compareTo(BigDecimal.ZERO) > 0)
                ? reward.divide(risk, 2, RoundingMode.HALF_UP).doubleValue()
                : 1.5;

            signals.add(SignalScreenerDto.builder()
                .symbol(symbol)
                .companyName(getCompanyName(symbol))
                .sector(getSectorName(symbol))
                .compositeScore(score)
                .recommendation(recommendation)
                .currentPrice(price)
                .entryPrice(price)
                .stopLossPrice(stopLoss)
                .target1DailyProfit(target1)
                .target2TrendRide(target2)
                .riskRewardRatio(rr)
                .winProbabilityPercent(winProb)
                .technicalSetup(scan.getSignalTitle())
                .tradeThesis(scan.getSignalDescription())
                .build());
        }

        signals.sort(Comparator.comparingInt(SignalScreenerDto::getCompositeScore).reversed());
        return signals;
    }

    private String getCompanyName(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "FPT" -> "Tập đoàn FPT (Công nghệ)";
            case "HPG" -> "Tập đoàn Hòa Phát (Thép)";
            case "SSI" -> "Chứng khoán SSI";
            case "TCB" -> "Ngân hàng Techcombank";
            case "MWG" -> "Thế Giới Di Động";
            case "MBB" -> "Ngân hàng Quân Đội";
            case "VCB" -> "Ngân hàng Vietcombank";
            case "VHM" -> "Vinhomes";
            case "VIC" -> "Tập đoàn Vingroup";
            case "MSN" -> "Tập đoàn Masan";
            case "GAS" -> "Tổng Công ty Khí Việt Nam";
            case "VNM" -> "Vinamilk";
            default -> "Doanh nghiệp niêm yết HOSE";
        };
    }

    private String getSectorName(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "FPT" -> "Công nghệ";
            case "HPG" -> "Thép & Vật liệu";
            case "SSI" -> "Chứng khoán";
            case "TCB", "MBB", "VCB" -> "Ngân hàng";
            case "MWG", "MSN", "VNM" -> "Bán lẻ & Tiêu dùng";
            case "VHM", "VIC" -> "Bất động sản";
            case "GAS" -> "Dầu khí & Năng lượng";
            default -> "Đa ngành";
        };
    }
}
