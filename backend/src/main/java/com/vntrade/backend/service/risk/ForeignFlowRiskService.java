package com.vntrade.backend.service.risk;

import com.vntrade.backend.dto.ForeignFlowRiskDto;
import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.service.marketdata.OrderBookService;
import com.vntrade.backend.service.marketdata.StockPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForeignFlowRiskService {

    private final StockPriceService stockPriceService;
    private final OrderBookService orderBookService;

    /**
     * Đánh giá dòng tiền Khối ngoại (FII) THẬT trong phiên từ sổ lệnh 24hMoney + StockPriceService.
     */
    public ForeignFlowRiskDto evaluateForeignFlowRisk(String symbol) {
        String sym = symbol != null ? symbol.toUpperCase().trim() : "FPT";
        StockQuote quote = stockPriceService.getQuote(sym);
        OrderBookDto ob = orderBookService.getOrderBook(sym);

        BigDecimal price = (ob.getCurrentPrice() != null && ob.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0)
            ? ob.getCurrentPrice()
            : (quote != null && quote.getPrice() != null ? quote.getPrice() : BigDecimal.ZERO);
        long volume = ob.getTotalMatchedVolume() > 0
            ? ob.getTotalMatchedVolume()
            : (quote != null && quote.getVolume() != null ? quote.getVolume() : 0L);

        long buyVol = ob.getForeignBuyVolume();
        long sellVol = ob.getForeignSellVolume();
        long netVol = ob.getForeignNetVolume();
        long roomRemaining = ob.getForeignRoomRemaining();

        BigDecimal foreignBuyVol = BigDecimal.valueOf(buyVol);
        BigDecimal foreignSellVol = BigDecimal.valueOf(sellVol);
        BigDecimal foreignNetVal = price.multiply(BigDecimal.valueOf(netVol));

        List<String> alerts = new ArrayList<>();
        String verdict;
        String slippageRisk;
        String recommendation;
        int liquidityScore;

        if (!ob.isRealOrderBookAvailable() && buyVol == 0 && sellVol == 0) {
            verdict = "PRE_MARKET_OR_NO_FOREIGN_DATA";
            slippageRisk = "UNKNOWN";
            liquidityScore = volume >= 1_000_000L ? 80 : 60;
            alerts.add("Chưa có dữ liệu giao dịch khối ngoại trong phiên (" + ob.getDataSource() + ").");
            recommendation = "Chờ phiên khớp lệnh liên tục để quan sát dòng tiền ngoại thực tế.";
        } else {
            double netValueBillions = foreignNetVal.doubleValue() / 1_000_000_000.0;
            double totalForeignVol = buyVol + sellVol;
            double sellDominanceRatio = buyVol > 0 ? (double) sellVol / buyVol : (sellVol > 0 ? 10.0 : 1.0);

            if (netValueBillions >= 5.0 || (netVol > 0 && sellVol > 0 && (double) buyVol / sellVol >= 1.5)) {
                verdict = "STRONG_FOREIGN_NET_ACCUMULATION";
                slippageRisk = "LOW";
                liquidityScore = volume >= 2_000_000L ? 95 : 85;
                alerts.add(String.format("Khối ngoại ĐANG GOM RÒNG thực tế +%s CP (~%+.2f tỷ đ) trong phiên.",
                    String.format("%,d", netVol), netValueBillions));
                recommendation = "TÍN HIỆU TÍCH CỰC: Dòng vốn ngoại đang mua ròng nâng đỡ giá.";
            } else if (netValueBillions <= -10.0 && sellDominanceRatio >= 1.8 && totalForeignVol > volume * 0.10) {
                verdict = "FOREIGN_DUMP_WARNING";
                slippageRisk = "HIGH_OUTFLOW_RISK";
                liquidityScore = 50;
                alerts.add(String.format("CẢNH BÁO XẢ RÒNG THỰC TẾ: Khối ngoại bán %s CP vs mua %s CP (Ròng %+.2f tỷ đ).",
                    String.format("%,d", sellVol), String.format("%,d", buyVol), netValueBillions));
                recommendation = "CẢNH BÁO PHÂN PHỐI: Khối ngoại xả áp đảo, hạn chế mua đuổi.";
            } else {
                verdict = "BALANCED_FOREIGN_FLOW";
                slippageRisk = volume >= 500_000L ? "LOW" : "MODERATE";
                liquidityScore = volume >= 1_000_000L ? 85 : 70;
                alerts.add(String.format("Khối ngoại cân bằng: Mua %s CP | Bán %s CP (Ròng %+.2f tỷ đ).",
                    String.format("%,d", buyVol), String.format("%,d", sellVol), netValueBillions));
                recommendation = "Dòng tiền ngoại trung tính, quyết định theo cấu trúc kỹ thuật & thanh khoản.";
            }
        }

        return ForeignFlowRiskDto.builder()
            .symbol(sym)
            .foreignBuyVolume(foreignBuyVol)
            .foreignSellVolume(foreignSellVol)
            .foreignNetValueVnd(foreignNetVal)
            .foreignOwnershipPercent(BigDecimal.ZERO)
            .foreignRoomRemainingPercent(roomRemaining > 0 ? BigDecimal.valueOf(roomRemaining) : BigDecimal.ZERO)
            .consecutiveNetSellDays(0)
            .liquidityHealthScore(liquidityScore)
            .slippageRiskIndex(slippageRisk)
            .fiiFlowVerdict(verdict)
            .earlyWarningAlerts(alerts)
            .institutionalRecommendation(recommendation)
            .build();
    }
}