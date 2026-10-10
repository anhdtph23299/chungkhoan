package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.OrderBookDto.OrderBookLevel;
import com.vntrade.backend.dto.SpoofingDetectorDto;
import com.vntrade.backend.dto.SpoofingDetectorDto.DepthLevelInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import com.vntrade.backend.service.marketdata.OrderBookService;

@Service
@Slf4j
@RequiredArgsConstructor
public class MicrostructureSpoofingDetectorService {

    private final OrderBookService orderBookService;

    /**
     * Phân tích cấu trúc vi mô sổ lệnh và nhận diện hành vi kê lệnh ảo (Spoofing / Phantom Wall)
     */
    public SpoofingDetectorDto detectSpoofing(String symbol) {
        String sym = symbol != null ? symbol.toUpperCase().trim() : "FPT";
        OrderBookDto ob = orderBookService.getOrderBook(sym);

        List<OrderBookLevel> bids = ob.getBidLevels() != null ? ob.getBidLevels() : List.of();
        List<OrderBookLevel> asks = ob.getAskLevels() != null ? ob.getAskLevels() : List.of();

        long totalBidVol = bids.stream().mapToLong(OrderBookLevel::getVolume).sum();
        long totalAskVol = asks.stream().mapToLong(OrderBookLevel::getVolume).sum();

        List<DepthLevelInfo> bidInfoList = new ArrayList<>();
        for (int i = 0; i < bids.size(); i++) {
            OrderBookLevel b = bids.get(i);
            double prop = totalBidVol > 0 ? (double) b.getVolume() / totalBidVol * 100.0 : 0.0;
            bidInfoList.add(DepthLevelInfo.builder()
                    .level(i + 1)
                    .price(b.getPrice())
                    .volume(b.getVolume())
                    .proportionPercent(BigDecimal.valueOf(prop).setScale(1, RoundingMode.HALF_UP))
                    .build());
        }

        List<DepthLevelInfo> askInfoList = new ArrayList<>();
        for (int i = 0; i < asks.size(); i++) {
            OrderBookLevel a = asks.get(i);
            double prop = totalAskVol > 0 ? (double) a.getVolume() / totalAskVol * 100.0 : 0.0;
            askInfoList.add(DepthLevelInfo.builder()
                    .level(i + 1)
                    .price(a.getPrice())
                    .volume(a.getVolume())
                    .proportionPercent(BigDecimal.valueOf(prop).setScale(1, RoundingMode.HALF_UP))
                    .build());
        }

        // Tính Micro-Price Dislocation
        BigDecimal bestBidPrice = !bids.isEmpty() ? bids.get(0).getPrice() : ob.getCurrentPrice();
        BigDecimal bestAskPrice = !asks.isEmpty() ? asks.get(0).getPrice() : ob.getCurrentPrice();
        long bestBidVol = !bids.isEmpty() ? bids.get(0).getVolume() : 1L;
        long bestAskVol = !asks.isEmpty() ? asks.get(0).getVolume() : 1L;

        double midPrice = (bestBidPrice.doubleValue() + bestAskPrice.doubleValue()) / 2.0;
        double microPrice = (bestBidVol * bestAskPrice.doubleValue() + bestAskVol * bestBidPrice.doubleValue())
                / Math.max(1.0, (double) (bestBidVol + bestAskVol));
        double microDislocationPercent = midPrice > 0 ? ((microPrice - midPrice) / midPrice) * 100.0 : 0.0;

        // Phân tích tầng sâu Level 3 để phát hiện kê lệnh ảo
        double bidL3Ratio = bids.size() >= 3 && totalBidVol > 0 ? (double) bids.get(2).getVolume() / totalBidVol : 0.0;
        double bidL1Ratio = bids.size() >= 1 && totalBidVol > 0 ? (double) bids.get(0).getVolume() / totalBidVol : 0.0;

        double askL3Ratio = asks.size() >= 3 && totalAskVol > 0 ? (double) asks.get(2).getVolume() / totalAskVol : 0.0;
        double askL1Ratio = asks.size() >= 1 && totalAskVol > 0 ? (double) asks.get(0).getVolume() / totalAskVol : 0.0;

        int riskScore = 15; // Nền bình thường
        String pattern = "GENUINE_ORGANIC_DEPTH";
        boolean isSafeToBuy = true;
        BigDecimal suspectedPrice = BigDecimal.ZERO;
        long suspectedVolume = 0L;
        double cancelHazard = 10.0;
        double level3MaxRatio = Math.max(bidL3Ratio, askL3Ratio) * 100.0;

        // Phát hiện Bẫy Kê Mua Ảo Tầng 3 (Phantom Bid Support Wall)
        if (bidL3Ratio >= 0.55 && bidL1Ratio <= 0.20) {
            pattern = "PHANTOM_BID_SUPPORT_LAYER";
            riskScore = (int) Math.min(95, 60 + (bidL3Ratio - 0.55) * 80);
            isSafeToBuy = false; // Nguy hiểm: Không mua đuổi theo lệnh MP vì bức tường đỡ là ảo
            suspectedPrice = bids.get(2).getPrice();
            suspectedVolume = bids.get(2).getVolume();
            cancelHazard = Math.min(92.0, 70.0 + (bidL3Ratio - 0.55) * 60);
        }
        // Phát hiện Bẫy Đè Bán Ảo Tầng 3 (Phantom Ask Resistance Layer)
        else if (askL3Ratio >= 0.55 && askL1Ratio <= 0.20) {
            pattern = "PHANTOM_ASK_RESISTANCE_LAYER";
            riskScore = (int) Math.min(85, 50 + (askL3Ratio - 0.55) * 70);
            isSafeToBuy = true; // An toàn: Big boy đè bán ảo để gom hàng giá thấp
            suspectedPrice = asks.get(2).getPrice();
            suspectedVolume = asks.get(2).getVolume();
            cancelHazard = Math.min(88.0, 65.0 + (askL3Ratio - 0.55) * 50);
        } else if (Math.abs(microDislocationPercent) > 0.40) {
            riskScore = 35;
        }

        // Khuyến nghị hành động đối phó thao túng
        String verdict;
        if ("PHANTOM_BID_SUPPORT_LAYER".equals(pattern)) {
            verdict = String.format("CẢNH BÁO BẪY KÊ MUA ẢO (Bull Trap): Khối lượng tầng 3 chiếm %.1f%% tổng dư mua (%d CP tại giá %.0f đ) trong khi tầng 1 rất mỏng (%.1f%%). Xác suất hủy lệnh trước khớp: %.0f%%. Bot TỪ CHỐI mua lệnh MP, đề phòng cá mập rút lệnh kê xả hàng.",
                    bidL3Ratio * 100.0, suspectedVolume, suspectedPrice.doubleValue(), bidL1Ratio * 100.0, cancelHazard);
        } else if ("PHANTOM_ASK_RESISTANCE_LAYER".equals(pattern)) {
            verdict = String.format("PHÁT HIỆN ĐÈ BÁN ẢO ĐỂ GOM (Bear Trap): Khối lượng tầng bán 3 chiếm %.1f%% (%d CP tại giá %.0f đ) tạo tâm lý đè giá giả. Bot ưu tiên đặt lệnh Mua giới hạn (Limit) gom trước khi tường bán biến mất.",
                    askL3Ratio * 100.0, suspectedVolume, suspectedPrice.doubleValue());
        } else {
            verdict = String.format("Thanh khoản tự nhiên hữu cơ (Organic Depth). Sổ lệnh phân bổ cân bằng 3 tầng giá (Điểm rủi ro thao túng: %d/100). Đủ điều kiện khớp lệnh an toàn chuẩn mực.",
                    riskScore);
        }

        return SpoofingDetectorDto.builder()
                .symbol(sym)
                .currentPrice(ob.getCurrentPrice())
                .spoofingRiskScore(riskScore)
                .layeringPattern(pattern)
                .isSafeToBuy(isSafeToBuy)
                .level3ToTotalRatio(BigDecimal.valueOf(level3MaxRatio).setScale(1, RoundingMode.HALF_UP))
                .suspectedPhantomVolume(suspectedVolume)
                .suspectedPhantomPrice(suspectedPrice)
                .cancelHazardProbability(BigDecimal.valueOf(cancelHazard).setScale(1, RoundingMode.HALF_UP))
                .microPriceDislocationPercent(BigDecimal.valueOf(microDislocationPercent).setScale(2, RoundingMode.HALF_UP))
                .bidLevels(bidInfoList)
                .askLevels(askInfoList)
                .institutionalActionVerdict(verdict)
                .build();
    }
}