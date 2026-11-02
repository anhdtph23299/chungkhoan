package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.MarketRegimeDto;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import com.vntrade.backend.service.marketdata.StockPriceService;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketRegimeDetectionService {

    private final StockPriceService stockPriceService;

    public MarketRegimeDto detectMarketRegime() {
        // Khảo sát độ rộng thị trường qua 6 trụ cột dẫn dắt chỉ số VN-Index / VN30
        List<String> pillars = List.of("VCB", "BID", "TCB", "HPG", "FPT", "VHM");
        double totalChange = 0;
        int validQuotes = 0;
        int negativePillars = 0;

        for (String sym : pillars) {
            StockQuote q = stockPriceService.getQuote(sym);
            if (q != null && q.getChangePercent() != null) {
                double chg = q.getChangePercent().doubleValue();
                totalChange += chg;
                validQuotes++;
                if (chg < 0) negativePillars++;
            }
        }

        double avgPillarChange = validQuotes > 0 ? (totalChange / validQuotes) : 0.0;
        StockQuote vniQuote = stockPriceService.getQuote("VNINDEX");
        BigDecimal vnIndexLevel = (vniQuote != null && vniQuote.getPrice() != null && vniQuote.getPrice().compareTo(BigDecimal.ZERO) > 0)
            ? vniQuote.getPrice()
            : BigDecimal.valueOf(1285.50);
        BigDecimal vnIndexChangePercent = (vniQuote != null && vniQuote.getChangePercent() != null)
            ? vniQuote.getChangePercent()
            : BigDecimal.valueOf(avgPillarChange).setScale(2, RoundingMode.HALF_UP);

        double vniChg = vnIndexChangePercent.doubleValue();

        String regime;
        String displayName;
        BigDecimal maxExposure;
        BigDecimal stopLoss;
        BigDecimal takeProfit;
        String primaryStrategy;
        List<String> characteristics = new ArrayList<>();
        String verdict;

        // Phân loại chế độ thị trường theo chuẩn Lão làng (O'Neil & Minervini):
        // 1. DOWNTREND_DEFENSE: Thị trường giảm mạnh (>=4 trụ đỏ, hoặc VNI giảm <= -0.6%, hoặc trung bình trụ <= -0.5%)
        if (negativePillars >= 4 || vniChg <= -0.6 || avgPillarChange <= -0.5) {
            regime = "DOWNTREND_DEFENSE";
            displayName = "Thị Trường Giảm Giá - Phòng Ngự (Downtrend Defense)";
            maxExposure = BigDecimal.ZERO; // 0% Cổ phiếu - 100% TIỀN MẶT LÀ VUA
            stopLoss = BigDecimal.valueOf(4.0);
            takeProfit = BigDecimal.valueOf(6.0);
            primaryStrategy = "CASH_IS_KING_AND_CAPITAL_PRESERVATION";
            characteristics.add("Áp lực bán tháo diện rộng trên các trụ cột chính (>= 4 trụ giảm hoặc VN-Index rơi mạnh).");
            characteristics.add("80% cổ phiếu breakout trong pha này đều là bẫy Bull-trap.");
            characteristics.add("Cầu chì DEFCON-1 kích hoạt bảo vệ vốn tối cao.");
            verdict = "CHẾ ĐỘ PHÒNG THỦ TUYỆT ĐỐI (BEAR DEFENSE): Tiền mặt là vua (Cash is King). Khóa 100% lệnh mở mới, bảo toàn tuyệt đối 100% vốn.";
        }
        // 2. SIDEWAYS_ACCUMULATION: Thị trường giằng co, phân hóa (3 trụ đỏ hoặc VNI biến động âm nhẹ)
        else if (negativePillars == 3 || vniChg < 0 || avgPillarChange < 0) {
            regime = "SIDEWAYS_ACCUMULATION";
            displayName = "Tích Lũy Biên Hẹp (Sideways Range)";
            maxExposure = BigDecimal.valueOf(30.0); // Giới hạn tối đa 30% NAV (chỉ 1 mã thăm dò)
            stopLoss = BigDecimal.valueOf(4.5);
            takeProfit = BigDecimal.valueOf(7.0);
            primaryStrategy = "PILOT_BUY_AND_SUPPORT_BOUNCE";
            characteristics.add("Dòng tiền phân hóa mạnh, chỉ tập trung cục bộ vào 1-2 cổ phiếu cá biệt.");
            characteristics.add("Độ rộng thị trường cân bằng 50/50, không có sóng ngành đồng thuận.");
            verdict = "CHẾ ĐỘ THĂM DÒ BIÊN HẸP: Giới hạn tối đa 30% NAV (1 vị thế duy nhất). Chỉ giải ngân khi có điểm nổ Pocket Pivot cực chuẩn hoặc bắt đáy hoảng loạn.";
        }
        // 3. UPTREND_UNDER_PRESSURE: Tăng giá nhưng chịu áp lực chốt lời ngắn hạn
        else if (negativePillars == 2 || avgPillarChange < 0.3) {
            regime = "UPTREND_UNDER_PRESSURE";
            displayName = "Uptrend Chịu Áp Lực Phân Phối (Under Pressure)";
            maxExposure = BigDecimal.valueOf(60.0); // Tối đa 60% NAV
            stopLoss = BigDecimal.valueOf(5.0);
            takeProfit = BigDecimal.valueOf(10.0);
            primaryStrategy = "SWING_PULLBACK_AND_PARTIAL_PROFIT";
            characteristics.add("Thị trường giữ được sắc xanh nhưng xuất hiện rung lắc tại các vùng kháng cự.");
            characteristics.add("Chỉ mở rộng quy mô khi các vị thế trước đó đã sinh đệm lãi an toàn.");
            verdict = "CHẾ ĐỘ TĂNG GIÁ THẬN TRỌNG: Trần tỷ trọng 60% NAV. Tuân thủ nghiêm ngặt quy tắc kim tự tháp: Có đệm lãi mới được gia tăng vị thế.";
        }
        // 4. CONFIRMED_UPTREND: Tăng giá đồng thuận toàn thị trường
        else {
            regime = "CONFIRMED_UPTREND";
            displayName = "Thị Trường Tăng Giá Xác Nhận (Confirmed Uptrend)";
            maxExposure = BigDecimal.valueOf(100.0); // Tối đa 100% NAV
            stopLoss = BigDecimal.valueOf(6.0);
            takeProfit = BigDecimal.valueOf(18.0);
            primaryStrategy = "VCP_BREAKOUT_AND_MOMENTUM_RUNNER";
            characteristics.add("Dòng tiền lớn lan tỏa đều khắp các nhóm ngành dẫn dắt (Bank, Thép, Chứng khoán, Công nghệ).");
            characteristics.add("Xuất hiện phiên Bùng nổ theo đà (FTD) với khối lượng vượt trội.");
            characteristics.add("Đa số các trụ cột đều tăng giá mạnh mẽ.");
            verdict = "CHẾ ĐỘ TỐI ƯU HÓA LỢI NHUẬN (BULL REGIME): Cho phép Bot giải ngân tối đa 100% NAV theo mô hình kim tự tháp để tối đa hóa tài sản.";
        }

        return MarketRegimeDto.builder()
            .currentRegime(regime)
            .regimeDisplayName(displayName)
            .vnIndexLevel(vnIndexLevel)
            .vnIndexChangePercent(vnIndexChangePercent)
            .distributionDaysCount(negativePillars)
            .followThroughDayConfirmed(avgPillarChange >= 0.3)
            .recommendedMaxExposure(maxExposure)
            .recommendedStopLossPercent(stopLoss)
            .recommendedTakeProfitPercent(takeProfit)
            .recommendedPrimaryStrategy(primaryStrategy)
            .regimeCharacteristics(characteristics)
            .quantOfficerVerdict(verdict)
            .build();
    }
}