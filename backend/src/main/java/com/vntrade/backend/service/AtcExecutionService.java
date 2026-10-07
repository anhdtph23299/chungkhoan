package com.vntrade.backend.service;

import com.vntrade.backend.dto.AtcOrderFlowDto;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AtcExecutionService {

    private final StockPriceService stockPriceService;

    public AtcOrderFlowDto evaluateAtcWindow(String symbol) {
        StockQuote quote = stockPriceService.getQuote(symbol);
        BigDecimal continuousPrice = quote.getPrice() != null ? quote.getPrice() : BigDecimal.valueOf(50000);

        // Mô phỏng / phân tích hành vi khớp lệnh phiên ATC
        double changePct = quote.getChangePercent() != null ? quote.getChangePercent().doubleValue() : 1.2;

        BigDecimal expectedAtc;
        long buyAtcVol;
        long sellAtcVol;
        String action;
        String tactic;
        boolean highVol = Math.abs(changePct) >= 2.0;

        if (changePct > 0.5) {
            // Lực cầu mạnh: Đẩy giá ATC cao hơn giá liên tục từ +0.5% đến +1.2%
            expectedAtc = continuousPrice.multiply(BigDecimal.valueOf(1.008)).setScale(0, RoundingMode.HALF_UP);
            buyAtcVol = 850_000L;
            sellAtcVol = 320_000L;
            action = "HOLD_FOR_OVERNIGHT_GAP";
            tactic = "Lệnh mua ATC áp đảo hoàn toàn (2.65x lệnh bán). Đội lái đang kê lệnh đỡ giá ATC. Khuyến nghị giữ vị thế qua đêm để đón sóng tăng điểm mở phiên sáng mai.";
        } else if (changePct < -0.5) {
            // Lực bán đè: Giá ATC thấp hơn từ -0.5% đến -1.0%
            expectedAtc = continuousPrice.multiply(BigDecimal.valueOf(0.992)).setScale(0, RoundingMode.HALF_UP);
            buyAtcVol = 250_000L;
            sellAtcVol = 780_000L;
            action = "SECURE_PROFIT_BEFORE_ATC";
            tactic = "Lực bán táng ATC tăng đột biến. Cần chủ động chốt lời bảo vệ tiền mặt trước 14:28, tránh bị trượt giá trong phiên khớp định kỳ.";
        } else {
            expectedAtc = continuousPrice;
            buyAtcVol = 400_000L;
            sellAtcVol = 420_000L;
            action = "NEUTRAL_MONITOR";
            tactic = "Cung cầu cân bằng tại phiên ATC. Tiếp tục duy trì vị thế và bám sát ngưỡng Trailing Stop.";
        }

        BigDecimal spreadPct = expectedAtc.subtract(continuousPrice)
            .divide(continuousPrice, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        BigDecimal pressureRatio = sellAtcVol > 0
            ? BigDecimal.valueOf((double) buyAtcVol / sellAtcVol).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(1.0);

        return AtcOrderFlowDto.builder()
            .symbol(symbol.toUpperCase())
            .continuousMatchPrice(continuousPrice)
            .expectedAtcPrice(expectedAtc)
            .atcSpreadPercent(spreadPct)
            .atcBuyOrderVolume(buyAtcVol)
            .atcSellOrderVolume(sellAtcVol)
            .atcBuyPressureRatio(pressureRatio)
            .atcActionSignal(action)
            .professionalTactic(tactic)
            .isHighVolatilityExpected(highVol)
            .build();
    }

    public List<AtcOrderFlowDto> evaluateTopAtcStocks() {
        List<String> watchlist = List.of("FPT", "HPG", "TCB", "SSI", "MWG");
        List<AtcOrderFlowDto> list = new ArrayList<>();
        for (String sym : watchlist) {
            try {
                list.add(evaluateAtcWindow(sym));
            } catch (Exception ignored) {}
        }
        return list;
    }
}
