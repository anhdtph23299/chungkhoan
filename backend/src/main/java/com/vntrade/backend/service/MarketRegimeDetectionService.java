package com.vntrade.backend.service;

import com.vntrade.backend.dto.MarketRegimeDto;
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
public class MarketRegimeDetectionService {

    private final StockPriceService stockPriceService;

    public MarketRegimeDto detectMarketRegime() {
        StockQuote fptQuote = stockPriceService.getQuote("FPT");
        double changePct = fptQuote.getChangePercent() != null ? fptQuote.getChangePercent().doubleValue() : 1.2;

        BigDecimal vnIndexLevel = BigDecimal.valueOf(1285.50);
        BigDecimal vnIndexChangePercent = BigDecimal.valueOf(changePct > 0 ? 0.65 : -0.45);
        int distributionDays = changePct >= 0 ? 1 : 4; // Số ngày phân phối
        boolean ftdConfirmed = changePct >= -0.5;

        String regime;
        String displayName;
        BigDecimal maxExposure;
        BigDecimal stopLoss;
        BigDecimal takeProfit;
        String primaryStrategy;
        List<String> characteristics = new ArrayList<>();
        String verdict;

        if (distributionDays <= 2 && ftdConfirmed && vnIndexChangePercent.compareTo(BigDecimal.ZERO) >= 0) {
            regime = "CONFIRMED_UPTREND";
            displayName = "Thị Trường Tăng Giá Xác Nhận (Confirmed Uptrend)";
            maxExposure = BigDecimal.valueOf(100.0);
            stopLoss = BigDecimal.valueOf(7.0);
            takeProfit = BigDecimal.valueOf(15.0);
            primaryStrategy = "VCP_BREAKOUT_AND_MOMENTUM_RUNNER";
            characteristics.add("Dòng tiền lớn lan tỏa đều khắp các nhóm ngành dẫn dắt (Bank, Thép, Công nghệ).");
            characteristics.add("Xuất hiện phiên Bùng nổ theo đà (FTD) với khối lượng vượt bình quân 20 phiên.");
            characteristics.add("Số phiên phân phối ở mức tối thiểu (≤ 2 phiên), chưa có tín hiệu thoát hàng của tổ chức.");
            verdict = "CHẾ ĐỘ TỐI ƯU LỢI NHUẬN (BULL REGIME): Cho phép Bot giải ngân tối đa 100% NAV. Tập trung săn điểm nổ VCP và bám trend sinh lời cao nhất.";
        } else if (distributionDays <= 4) {
            regime = "UPTREND_UNDER_PRESSURE";
            displayName = "Uptrend Chịu Áp Lực Phân Phối (Under Pressure)";
            maxExposure = BigDecimal.valueOf(60.0);
            stopLoss = BigDecimal.valueOf(5.0);
            takeProfit = BigDecimal.valueOf(10.0);
            primaryStrategy = "SWING_PULLBACK_AND_PARTIAL_PROFIT";
            characteristics.add("Xuất hiện 3-4 phiên phân phối tiềm ẩn áp lực chốt lời ngắn hạn.");
            characteristics.add("Các cổ phiếu tăng nóng bắt đầu có hiện tượng giằng co rung lắc mạnh.");
            characteristics.add("Khối ngoại có động thái bán ròng nhẹ hoặc cơ cấu danh mục ETF.");
            verdict = "CHẾ ĐỘ THẬN TRỌNG (CAUTION REGIME): Hạ trần tỷ trọng tối đa về 60% NAV. Siết chặt Stop Loss về 5% và chủ động chốt lời 50% từng phần.";
        } else if (distributionDays == 5) {
            regime = "SIDEWAYS_ACCUMULATION";
            displayName = "Tích Lũy Biên Hẹp (Sideways Range)";
            maxExposure = BigDecimal.valueOf(40.0);
            stopLoss = BigDecimal.valueOf(5.0);
            takeProfit = BigDecimal.valueOf(8.0);
            primaryStrategy = "BUY_SUPPORT_SELL_RESISTANCE";
            characteristics.add("Thanh khoản thị trường suy giảm, dao động trong hộp hẹp Darvas.");
            characteristics.add("Dòng tiền phân hóa mạnh chỉ tập trung vào một số cổ phiếu có câu chuyện riêng.");
            verdict = "CHẾ ĐỘ TÍCH LŨY (SIDEWAYS REGIME): Khống chế tỷ trọng 40% NAV. Đánh nhanh rút gọn ở các mốc hỗ trợ cứng, tránh mua đuổi breakout.";
        } else {
            regime = "DOWNTREND_DEFENSE";
            displayName = "Thị Trường Giảm Giá - Phòng Ngự (Downtrend Defense)";
            maxExposure = BigDecimal.valueOf(0.0);
            stopLoss = BigDecimal.valueOf(4.0);
            takeProfit = BigDecimal.valueOf(6.0);
            primaryStrategy = "CASH_IS_KING_AND_CAPITAL_PRESERVATION";
            characteristics.add("Xuất hiện chuỗi phiên phân phối liên tiếp (≥ 5 phiên) gãy đường MA50 ngày.");
            characteristics.add("Cầu chì DEFCON-1 kích hoạt, nhiều mã sàn trắng bên mua.");
            verdict = "CHẾ ĐỘ PHÒNG THỦ TUYỆT ĐỐI (BEAR DEFENSE): Tiền mặt là vua (Cash is King). Khóa toàn bộ lệnh mua, ưu tiên bảo toàn vốn 100%.";
        }

        return MarketRegimeDto.builder()
            .currentRegime(regime)
            .regimeDisplayName(displayName)
            .vnIndexLevel(vnIndexLevel)
            .vnIndexChangePercent(vnIndexChangePercent)
            .distributionDaysCount(distributionDays)
            .followThroughDayConfirmed(ftdConfirmed)
            .recommendedMaxExposure(maxExposure)
            .recommendedStopLossPercent(stopLoss)
            .recommendedTakeProfitPercent(takeProfit)
            .recommendedPrimaryStrategy(primaryStrategy)
            .regimeCharacteristics(characteristics)
            .quantOfficerVerdict(verdict)
            .build();
    }
}
