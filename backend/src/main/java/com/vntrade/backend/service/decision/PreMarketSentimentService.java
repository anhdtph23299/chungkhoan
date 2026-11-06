package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.PreMarketSentimentDto;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.vntrade.backend.service.marketdata.StockPriceService;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreMarketSentimentService {

    private final StockPriceService stockPriceService;

    public PreMarketSentimentDto analyzePreMarketSentiment() {
        LocalDateTime now = LocalDateTime.now();
        String todayStr = now.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String yesterdayStr = now.minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));

        // 1. Tham chiếu vĩ mô hàng hóa (Brent & WTI) - Ước lượng tham chiếu (chờ feed trực tiếp)
        BigDecimal brent = BigDecimal.valueOf(82.50);
        BigDecimal brentChange = BigDecimal.valueOf(0.45);
        BigDecimal wti = BigDecimal.valueOf(78.20);
        BigDecimal wtiChange = BigDecimal.valueOf(0.35);

        String oilStatus = brent.compareTo(BigDecimal.valueOf(90.0)) >= 0
            ? "SURGING_OVER_90_HIGH_ALERT"
            : (brentChange.compareTo(BigDecimal.ZERO) > 0 ? "STEADY_MOMENTUM" : "COOLING_OFF");

        // 2. Tâm lý thị trường Quốc tế & Châu Á
        String usSentiment = "NEUTRAL_EXPANSION"; // Dow Jones / S&P500 biến động nhẹ
        String asiaSentiment = "CAUTIOUS_MIXED";  // Nikkei / Shanghai giằng co

        // 3. Khối ngoại (Ước lượng tham chiếu phiên gần nhất)
        BigDecimal foreignFlow = BigDecimal.valueOf(-150.00);

        // 4. Đánh giá áp lực mở cửa VN-Index phiên hôm nay
        int sentimentScore = 50; // Vùng trung lập
        String openingSentiment = "ATO_INERTIA_ABSORPTION";

        // 5. Tổng hợp góc nhìn chiến thuật
        String hypothesisVerdict = String.format(
            "GÓC NHÌN VĨ MÔ & LIÊN THỊ TRƯỜNG (%s): Giá dầu Brent tham chiếu %s USD/thùng (%+.2f%%). " +
            "Áp lực bán ròng khối ngoại ước tính %s tỷ VND (%s). Ưu tiên quan sát phản ứng sau 09:30.",
            todayStr, brent, brentChange.doubleValue(), foreignFlow, yesterdayStr
        );

        String recommendedTactic =
            "1. TUYỆT ĐỐI KHÔNG MUA ĐUỔI TRONG PHIÊN ATO (09:00 - 09:15) khi giá mở cửa chưa ổn định. " +
            "2. KIÊN NHẪN QUAN SÁT SAU 09:30: Nếu xuất hiện nhịp bán tháo quán tính, kích hoạt Radar bắt đáy kỹ thuật Oversold Bounce (5-10% NAV). " +
            "3. VỚI NHÓM DẪU KHÍ & VN30: Chỉ giải ngân khi có xác nhận dòng tiền lớn nuốt cạn cung bán và nổ Vol hợp lệ.";

        // 6. Danh mục tiêu điểm trực canh (Priority Watchlist)
        List<PreMarketSentimentDto.PreMarketStockWatch> priorityWatchlist = new ArrayList<>();

        priorityWatchlist.add(PreMarketSentimentDto.PreMarketStockWatch.builder()
            .symbol("PLX")
            .sector("DẦU KHÍ & NĂNG LƯỢNG")
            .referencePrice(getSymbolPrice("PLX", BigDecimal.valueOf(37700)))
            .focusReason("Tâm điểm thử nghiệm: RRG Alpha Leader 99đ, kiểm chứng lực cầu ăn tường bán tại 37,700 đ.")
            .triggerAction("Kích hoạt Breakout Override khi giá >= 37,700 đ và Vol > 1.5x.")
            .build());

        priorityWatchlist.add(PreMarketSentimentDto.PreMarketStockWatch.builder()
            .symbol("PVT")
            .sector("DẦU KHÍ & NĂNG LƯỢNG")
            .referencePrice(getSymbolPrice("PVT", BigDecimal.valueOf(25700)))
            .focusReason("Hưởng lợi kép từ giá dầu bùng nổ và cước vận tải biển dầu thô quốc tế.")
            .triggerAction("Giải ngân khi test thành công nền 25,500 đ và giữ sắc xanh.")
            .build());

        priorityWatchlist.add(PreMarketSentimentDto.PreMarketStockWatch.builder()
            .symbol("BSR")
            .sector("DẦU KHÍ & NĂNG LƯỢNG")
            .referencePrice(getSymbolPrice("BSR", BigDecimal.valueOf(32100)))
            .focusReason("Lọc hóa dầu hưởng lợi crack spread dầu thô tăng vọt.")
            .triggerAction("Theo dõi dòng tiền mua gom tại vùng 31,800 - 32,100 đ.")
            .build());

        priorityWatchlist.add(PreMarketSentimentDto.PreMarketStockWatch.builder()
            .symbol("FPT")
            .sector("CÔNG NGHỆ")
            .referencePrice(getSymbolPrice("FPT", BigDecimal.valueOf(59800)))
            .focusReason("Siêu cổ phiếu công nghệ điều chỉnh kỹ thuật sau chia thưởng cổ phiếu; theo dõi điểm dừng chân.")
            .triggerAction("Săn tín hiệu Oversold Bounce nếu rớt về vùng hỗ trợ cứng kèm nến rút chân.")
            .build());

        priorityWatchlist.add(PreMarketSentimentDto.PreMarketStockWatch.builder()
            .symbol("HPG")
            .sector("THÉP & VẬT LIỆU")
            .referencePrice(getSymbolPrice("HPG", BigDecimal.valueOf(28500)))
            .focusReason("Trụ cột chỉ số VN30, rà soát phản ứng tại ngưỡng hỗ trợ MA50 (28,200 đ).")
            .triggerAction("Kích hoạt mua khi xuất hiện nến Spring Pinbar tạo đáy.")
            .build());

        return PreMarketSentimentDto.builder()
            .checkedAt(now)
            .brentPrice(brent)
            .brentChangePercent(brentChange)
            .wtiPrice(wti)
            .wtiChangePercent(wtiChange)
            .oilMarketStatus(oilStatus)
            .usMarketSentiment(usSentiment)
            .asiaMarketSentiment(asiaSentiment)
            .foreignFlowYesterdayBillionVnd(foreignFlow)
            .openingMarketSentiment(openingSentiment)
            .sentimentScore(sentimentScore)
            .hypothesisVerdict(hypothesisVerdict)
            .recommendedOpeningTactic(recommendedTactic)
            .priorityWatchlist(priorityWatchlist)
            .build();
    }

    private BigDecimal getSymbolPrice(String symbol, BigDecimal fallback) {
        try {
            StockQuote q = stockPriceService.getQuote(symbol);
            if (q != null && q.getPrice() != null && q.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                return q.getPrice();
            }
        } catch (Exception ignored) {}
        return fallback;
    }
}