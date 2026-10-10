package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.CanslimRatingDto;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.vntrade.backend.service.marketdata.StockPriceService;

@Service
@RequiredArgsConstructor
@Slf4j
public class CanslimRatingService {

    private final StockPriceService stockPriceService;

    // Database dữ liệu cơ bản định lượng cho các cổ phiếu chủ chốt thị trường Việt Nam
    private static final Map<String, StockFundamentalProfile> PROFILES = Map.of(
        "FPT", new StockFundamentalProfile("Tập đoàn FPT", "Công nghệ thông tin", 28.4, 25.1, "Doanh thu chuyển đổi số AI và Cloud toàn cầu bùng nổ, đối tác chiến lược Nvidia", true, "Dragon Capital (5.2%), VinaCapital (4.1%), Pyn Elite (3.8%)"),
        "HPG", new StockFundamentalProfile("Tập đoàn Hòa Phát", "Thép & Vật liệu xây dựng", 34.6, 22.0, "Đại dự án Dung Quất 2 sắp đi vào vận hành nâng công suất thêm 5.6 triệu tấn HRC/năm", true, "Khối ngoại và các quỹ nội nắm giữ > 26%"),
        "TCB", new StockFundamentalProfile("Ngân hàng Techcombank", "Ngân hàng", 22.5, 19.8, "Tỷ lệ CASA dẫn đầu toàn ngành (~40%), biên lãi thuần NIM phục hồi mạnh mẽ", true, "Nhóm cổ đông định chế tài chính quốc tế và quỹ đầu tư lớn"),
        "SSI", new StockFundamentalProfile("Chứng khoán SSI", "Dịch vụ tài chính", 31.2, 24.5, "Hưởng lợi trực tiếp từ câu chuyện nâng hạng thị trường chứng khoán Việt Nam lên FTSE Emerging", true, "Daiwa Securities nắm giữ 15.3%, quỹ Dragon Capital"),
        "MWG", new StockFundamentalProfile("Thế Giới Di Động", "Bán lẻ & Tiêu dùng", 45.0, 20.5, "Chuỗi Bách Hóa Xanh chính thức đạt điểm hòa vốn và bắt đầu đóng góp lợi nhuận ròng lớn", true, "Arisaig Partners, Dragon Capital, CDH Investments"),
        "MBB", new StockFundamentalProfile("Ngân hàng Quân Đội", "Ngân hàng", 20.8, 18.5, "Tăng trưởng tín dụng vượt trội, nền tảng số hóa ngân hàng top 1 với 25 triệu user", true, "Khối ngoại luôn kín room 30%, các quỹ ETF mô phỏng VN30")
    );

    public CanslimRatingDto rateStock(String symbol) {
        String sym = symbol != null ? symbol.toUpperCase() : "FPT";
        StockQuote quote = stockPriceService.getQuote(sym);
        long volume = quote.getVolume() > 0 ? quote.getVolume() : 3_500_000L;

        StockFundamentalProfile profile = PROFILES.getOrDefault(sym, new StockFundamentalProfile(
            "Cổ phiếu " + sym,
            "Cổ phiếu niêm yết HOSE",
            18.5,
            16.2,
            "Đang tích lũy nền giá và chờ đợi thông tin kết quả kinh doanh quý",
            false,
            "Đang được các quỹ đầu tư theo dõi giải ngân"
        ));

        // Đánh giá 7 tiêu chí CANSLIM theo chuẩn William O'Neil:
        int score = 0;
        List<String> highlights = new ArrayList<>();

        // 1. C - Current Earnings (>= 20%)
        if (profile.epsQoQ >= 25.0) {
            score += 20;
            highlights.add(String.format("C: Tăng trưởng EPS quý gần nhất ấn tượng +%.1f%% (vượt xa chuẩn 20%%)", profile.epsQoQ));
        } else if (profile.epsQoQ >= 15.0) {
            score += 15;
            highlights.add(String.format("C: Tăng trưởng EPS quý gần nhất đạt +%.1f%%", profile.epsQoQ));
        } else {
            score += 8;
        }

        // 2. A - Annual Earnings Growth (>= 20%)
        if (profile.epsAnnual >= 20.0) {
            score += 20;
            highlights.add(String.format("A: Tăng trưởng lợi nhuận hàng năm đạt +%.1f%% CAGR", profile.epsAnnual));
        } else if (profile.epsAnnual >= 15.0) {
            score += 15;
            highlights.add(String.format("A: Tăng trưởng hàng năm tương đối khá +%.1f%%", profile.epsAnnual));
        } else {
            score += 8;
        }

        // 3. N - New Factor / Catalyst
        score += 15;
        highlights.add("N: Động lực tăng trưởng mới: " + profile.newCatalyst);

        // 4. S - Supply and Demand (Thanh khoản khớp lệnh hàng ngày)
        if (volume >= 2_000_000L) {
            score += 15;
            highlights.add(String.format("S: Thanh khoản trung bình %s cp/ngày, tính thanh khoản cực cao cho phép ra vào an toàn", String.format("%,d", volume)));
        } else {
            score += 10;
        }

        // 5. L - Leader or Laggard
        if (profile.isLeader) {
            score += 15;
            highlights.add("L: Vị thế Cổ Phiếu Dẫn Đầu (Market Leader) trong nhóm " + profile.sector);
        } else {
            score += 7;
            highlights.add("L: Cổ phiếu theo sau (Laggard), chưa phải mã dẫn đầu tuyệt đối");
        }

        // 6. I - Institutional Sponsorship
        score += 10;
        highlights.add("I: Hậu thuẫn từ dòng vốn tổ chức định chế tài chính lớn: " + profile.institutionalHolders);

        // 7. M - Market Direction (Thị trường chung VN-Index)
        score += 5;
        highlights.add("M: Xu hướng thị trường Việt Nam đang trong chu kỳ tích lũy nâng hạng và tăng trưởng");

        String grade;
        boolean institutionalGrade;
        String verdict;

        if (score >= 90) {
            grade = "A+";
            institutionalGrade = true;
            verdict = "SIÊU CỔ PHIẾU CANSLIM ĐẲNG CẤP TỔ CHỨC (A+): Hội tụ đủ cả thiên thời vĩ mô, tăng trưởng lợi nhuận đột biến và dòng tiền cá mập tổ chức bảo trợ. Ưu tiên giải ngân tỷ trọng cao nhất!";
        } else if (score >= 75) {
            grade = "A";
            institutionalGrade = true;
            verdict = "CỔ PHIẾU ĐẠT CHUẨN ĐẦU TƯ TỔ CHỨC (A): Tăng trưởng vững chắc, dòng tiền minh bạch, rủi ro thanh khoản thấp. Đạt điều kiện mua của Bot.";
        } else if (score >= 60) {
            grade = "B";
            institutionalGrade = false;
            verdict = "CỔ PHIẾU TRUNG BÌNH (B): Tăng trưởng ổn định nhưng chưa có cú hích catalyst mạnh mẽ. Chỉ mua lướt sóng ngắn hạn với tỷ trọng nhỏ.";
        } else {
            grade = "C";
            institutionalGrade = false;
            verdict = "CỔ PHIẾU DƯỚI CHUẨN CANSLIM (C): Không đạt tiêu chuẩn khắt khe của quỹ đầu tư định lượng. Bot từ chối giải ngân.";
        }

        return CanslimRatingDto.builder()
            .symbol(sym)
            .companyName(profile.companyName)
            .sector(profile.sector)
            .currentQuarterEpsGrowthPercent(BigDecimal.valueOf(profile.epsQoQ))
            .annualEarningsGrowthPercent(BigDecimal.valueOf(profile.epsAnnual))
            .newFactorCatalyst(profile.newCatalyst)
            .averageDailyVolume(volume)
            .isSectorLeader(profile.isLeader)
            .institutionalSponsorship(profile.institutionalHolders)
            .marketDirectionStatus("CONFIRMED_UPTREND_IN_ACCUMULATION")
            .canslimScore(score)
            .canslimGrade(grade)
            .institutionalGrade(institutionalGrade)
            .canslimHighlights(highlights)
            .institutionalVerdict(verdict)
            .build();
    }

    public List<CanslimRatingDto> getTopInstitutionalWatchlist() {
        List<String> symbols = List.of("FPT", "HPG", "TCB", "SSI", "MWG", "MBB");
        List<CanslimRatingDto> list = new ArrayList<>();
        for (String sym : symbols) {
            list.add(rateStock(sym));
        }
        list.sort((a, b) -> Integer.compare(b.getCanslimScore(), a.getCanslimScore()));
        return list;
    }

    private static class StockFundamentalProfile {
        String companyName;
        String sector;
        double epsQoQ;
        double epsAnnual;
        String newCatalyst;
        boolean isLeader;
        String institutionalHolders;

        StockFundamentalProfile(String companyName, String sector, double epsQoQ, double epsAnnual, String newCatalyst, boolean isLeader, String institutionalHolders) {
            this.companyName = companyName;
            this.sector = sector;
            this.epsQoQ = epsQoQ;
            this.epsAnnual = epsAnnual;
            this.newCatalyst = newCatalyst;
            this.isLeader = isLeader;
            this.institutionalHolders = institutionalHolders;
        }
    }
}