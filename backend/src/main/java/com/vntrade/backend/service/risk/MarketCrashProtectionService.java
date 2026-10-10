package com.vntrade.backend.service.risk;

import com.vntrade.backend.dto.MarketCrashProtectionDto;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import com.vntrade.backend.service.marketdata.StockPriceService;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketCrashProtectionService {

    private final StockPriceService stockPriceService;

    public MarketCrashProtectionDto evaluateMarketCircuitBreaker() {
        // Kiểm tra biến động chỉ số và các mã trụ cột VN30
        List<String> leaders = List.of("VCB", "FPT", "HPG", "TCB", "SSI", "MWG", "VHM");
        int floorHits = 0;
        int severeDropCount = 0;

        for (String sym : leaders) {
            try {
                StockQuote q = stockPriceService.getQuote(sym);
                if (q.getChangePercent() != null) {
                    if (q.getChangePercent().doubleValue() <= -6.8) {
                        floorHits++;
                    } else if (q.getChangePercent().doubleValue() <= -4.0) {
                        severeDropCount++;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Lấy biến động thực tế của chỉ số VN-Index từ sàn HOSE
        BigDecimal indexChange = BigDecimal.ZERO;
        BigDecimal indexChangePct = BigDecimal.ZERO;
        try {
            StockQuote vnIndexQuote = stockPriceService.getQuote("VNINDEX");
            if (vnIndexQuote != null) {
                if (vnIndexQuote.getChange() != null) {
                    indexChange = vnIndexQuote.getChange().setScale(2, java.math.RoundingMode.HALF_UP);
                }
                if (vnIndexQuote.getChangePercent() != null) {
                    indexChangePct = vnIndexQuote.getChangePercent().setScale(2, java.math.RoundingMode.HALF_UP);
                }
            }
        } catch (Exception e) {
            log.warn("Không thể lấy dữ liệu VNINDEX, dùng baseline: {}", e.getMessage());
        }

        String status;
        int level;
        boolean allowNewBuy;
        BigDecimal maxExposure;
        String message;
        String protocol;

        if (floorHits >= 2 || indexChange.doubleValue() <= -20.0) {
            status = "DEFCON_1_STORM_LOCKOUT";
            level = 2;
            allowNewBuy = false;
            maxExposure = BigDecimal.valueOf(30.0); // Giới hạn chỉ giữ tối đa 30% cổ phiếu
            message = "🚨 BÁO ĐỘNG ĐỎ DEFCON-1: Thị trường có hiện tượng bán tháo diện rộng hoặc giảm > 20 điểm. Cầu chì tự động kích hoạt: KHÓA 100% LỆNH MUA MỚI, siết chặt Trailing Stop bảo toàn vốn tối thượng.";
            protocol = "EMERGENCY_CAPITAL_PRESERVATION_PROTOCOL";
        } else if (severeDropCount >= 3 || indexChange.doubleValue() <= -10.0) {
            status = "CAUTION_DEFENSE";
            level = 1;
            allowNewBuy = true;
            maxExposure = BigDecimal.valueOf(50.0);
            message = "⚠️ CHẾ ĐỘ PHÒNG THỦ: Thị trường phân hóa mạnh, áp lực bán gia tăng. Hạ tỷ lệ rủi ro xuống 1% NAV, chỉ mở mua tối đa 2 vị thế xuất sắc nhất.";
            protocol = "DEFENSIVE_POSITIONING_PROTOCOL";
        } else {
            status = "NORMAL_MARKET_EXPANSION";
            level = 0;
            allowNewBuy = true;
            maxExposure = BigDecimal.valueOf(70.0);
            message = "✅ THỊ TRƯỜNG THUẬN LỢI: Xu hướng tăng được bảo toàn, dòng tiền lớn luân chuyển tốt. Robot vận hành chiến lược săn lợi nhuận và gặt hái lãi bình thường.";
            protocol = "ACTIVE_PROFIT_ACCUMULATION_PROTOCOL";
        }

        return MarketCrashProtectionDto.builder()
            .defenseStatus(status)
            .defenseLevel(level)
            .vnIndexChangePoints(indexChange)
            .vnIndexChangePercent(indexChangePct)
            .vn30FloorHitsCount(floorHits)
            .allowNewPurchases(allowNewBuy)
            .maxAccountExposure(maxExposure)
            .circuitBreakerMessage(message)
            .actionProtocol(protocol)
            .build();
    }
}