package com.vntrade.backend.service.risk;

import com.vntrade.backend.dto.BlackSwanStressScenarioDto;
import com.vntrade.backend.dto.BlackSwanStressScenarioDto.StressScenarioDetail;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import com.vntrade.backend.service.marketdata.StockPriceService;

@Service
@Slf4j
@RequiredArgsConstructor
public class BlackSwanStressScenarioService {

    private final TradeRepository tradeRepository;
    private final StockPriceService stockPriceService;

    private static final BigDecimal DEFAULT_CAPITAL = BigDecimal.valueOf(300_000_000); // 300 triệu
    private static final double VN30_INDEX_VALUE = 1320.0;
    private static final double VN30_FUTURES_MULTIPLIER = 100_000.0; // 100.000 đ / điểm hợp đồng

    /**
     * Mô phỏng kiểm tra sức chịu đựng danh mục trước các kịch bản Thiên nga đen (Black Swan)
     */
    public BlackSwanStressScenarioDto simulateBlackSwanScenarios(BigDecimal capital) {
        BigDecimal nav = (capital != null && capital.compareTo(BigDecimal.ZERO) > 0) ? capital : DEFAULT_CAPITAL;

        // 1. Tính toán danh mục và Beta thị trường
        List<Trade> openTrades = tradeRepository != null ? tradeRepository.findByStatusOrderByTradeDateDesc("open") : List.of();
        double portfolioBeta = calculatePortfolioBeta(openTrades);

        // 2. Định nghĩa và mô phỏng 4 kịch bản Thiên nga đen lịch sử & cấu trúc thị trường Việt Nam
        List<StressScenarioDetail> scenarios = new ArrayList<>();

        // Kịch bản 1: Cú sốc thanh khoản Covid-19 (Tháng 3/2020)
        // VN-Index giảm -28.5%, bán tháo hoảng loạn toàn cầu
        double shockCovid = 28.5;
        double lossPctCovid = Math.min(20.0, portfolioBeta * shockCovid * 0.60); // Nhờ stop-loss và hạ tỷ trọng
        scenarios.add(buildScenario(
                "COVID_2020",
                "Cú Sốc Hoảng Loạn Thiên Nga Đen Covid-19 (Tháng 3/2020)",
                "VN-Index sập 28.5% trong 4 tuần, thanh khoản cạn kiệt, bán tháo chéo trên toàn bảng điện.",
                shockCovid,
                lossPctCovid,
                nav,
                "Áp lực trượt giá lớn khi mở phiên ATO. Cổ phiếu T+0/T+1 chịu rủi ro gap-down qua đêm."
        ));

        // Kịch bản 2: Khủng hoảng thanh khoản Trái phiếu & Margin Call Chéo (Tháng 10-11/2022)
        // VN-Index giảm -34.0%, hiện tượng múa bên trăng / trắng bên mua hàng loạt
        double shockBond2022 = 34.0;
        double lossPctBond2022 = Math.min(23.5, portfolioBeta * shockBond2022 * 0.62);
        scenarios.add(buildScenario(
                "BOND_CRUNCH_2022",
                "Khủng Hoảng Trái Phiếu & Margin Call Chéo (Tháng 10-11/2022)",
                "VN-Index giảm 34% về mốc 873 điểm. Hàng loạt cổ phiếu mất thanh khoản, công ty chứng khoán bán giải chấp chéo danh mục.",
                shockBond2022,
                lossPctBond2022,
                nav,
                "Hiện tượng mất thanh khoản sàn lan rộng. Cần dự phòng tiền mặt tối thiểu 30% để không bị bán cưỡng bức."
        ));

        // Kịch bản 3: Sốc Tỷ Giá & Dòng Vốn Ngoại Rút Ròng Kỷ Lục (FII Capital Flight)
        // VN-Index giảm -15.0%, USD/VND tăng vọt, khối ngoại bán ròng 15 phiên liên tiếp
        double shockFii = 15.0;
        double lossPctFii = Math.min(13.5, portfolioBeta * shockFii * 0.80);
        scenarios.add(buildScenario(
                "FII_EXODUS_FX_SHOCK",
                "Sốc Tỷ Giá USD/VND & Khối Ngoại Bán Ròng Quy Mô Lớn",
                "DXY tăng vọt, SBV hút tín phiếu thắt chặt thanh khoản liên ngân hàng. Khối ngoại bán ròng mạnh nhóm VN30.",
                shockFii,
                lossPctFii,
                nav,
                "Thanh khoản tập trung rút ở các mã Blue-chip lớn. Trượt giá lệnh lớn tăng từ 15 lên 60 bps."
        ));

        // Kịch bản 4: Kẹt Thanh Khoản Sàn T+2.5 Bắt Buộc (T+2.5 Limit-Down Lockout)
        // 2 phiên giảm sàn liên tiếp (-7% x 2) trong khi hàng chưa về tài khoản (daysHeld = 0, 1)
        double shockT25 = 13.51; // (1 - 0.07)^2 - 1 = -13.51%
        double lossPctT25 = shockT25 + 0.40; // Cộng thêm 0.40% thuế phí bán
        scenarios.add(buildScenario(
                "T25_LIMIT_DOWN_LOCKOUT",
                "Khóa Thanh Khoản Sàn T+2.5 Trắng Bên Mua (Cổ Phiếu Kẹt Chưa Về)",
                "2 phiên giảm sàn liên tiếp (-7%/phiên) khi lệnh mua vừa khớp hôm nay hoặc hôm qua. Nhà đầu tư bất khả kháng không thể cắt lỗ.",
                shockT25,
                lossPctT25,
                nav,
                "Rủi ro cấu trúc nghiêm trọng nhất của TTCK Việt Nam. Lỗ cố định 13.91% không thể can thiệp bằng lệnh thường."
        ));

        // 3. Đánh giá kịch bản xấu nhất (Worst Case)
        StressScenarioDetail worstScenario = scenarios.stream()
                .max((s1, s2) -> s1.getPortfolioLossPercent().compareTo(s2.getPortfolioLossPercent()))
                .orElse(scenarios.get(0));

        BigDecimal worstDrawdownPct = worstScenario.getPortfolioLossPercent();
        BigDecimal worstLossAmount = worstScenario.getPortfolioLossVnd();

        // 4. Kiểm tra nguy cơ Call Margin (nếu lỗ danh mục > 22% NAV)
        boolean marginCall = worstDrawdownPct.compareTo(BigDecimal.valueOf(22.0)) >= 0;

        // 5. Điểm số bền vững danh mục (Overall Resilience Score: 0 - 100)
        int resilienceScore = (int) Math.max(10, Math.min(95, 100.0 - (worstDrawdownPct.doubleValue() * 2.8)));

        // 6. Tỷ lệ tiền mặt phòng vệ tối thiểu khuyến nghị
        BigDecimal cashBuffer = BigDecimal.valueOf(Math.min(45.0, Math.max(20.0, worstDrawdownPct.doubleValue() * 1.3)))
                .setScale(1, RoundingMode.HALF_UP);

        // 7. Tính số hợp đồng phái sinh VN30F1M cần Short phòng hộ (Hedge Ratio):
        // H = (NAV * Beta) / (VN30 * 100.000)
        double contractValue = VN30_INDEX_VALUE * VN30_FUTURES_MULTIPLIER; // 132.000.000 đ
        int hedgeContracts = (int) Math.round((nav.doubleValue() * portfolioBeta) / contractValue);
        if (hedgeContracts < 1) hedgeContracts = 1;

        // 8. Kết luận định chế
        String verdict;
        if (resilienceScore >= 70) {
            verdict = String.format("Danh mục có khả năng chống sốc vững chắc (Điểm chịu đựng: %d/100). Trong kịch bản xấu nhất (%s), mức tổn thất kiểm soát ở mức -%.2f%% (-%,d đ). Khuyến nghị duy trì %.1f%% tiền mặt phòng vệ và sẵn sàng Short %d HĐ VN30F1M khi DEFCON kích hoạt.",
                    resilienceScore, worstScenario.getScenarioName(), worstDrawdownPct, worstLossAmount.longValue(), cashBuffer, hedgeContracts);
        } else {
            verdict = String.format("CẢNH BÁO RỦI RO THIÊN NGA ĐEN: Điểm chịu đựng đạt mức trung bình (%d/100). Trong kịch bản hoảng loạn diện rộng, danh mục có thể sụt giảm tới -%.2f%% (-%,d đ). Cần hạ đòn bẩy Margin ngay lập tức và tăng tỷ trọng tiền mặt lên %.1f%%.",
                    resilienceScore, worstDrawdownPct, worstLossAmount.longValue(), cashBuffer);
        }

        return BlackSwanStressScenarioDto.builder()
                .portfolioCapital(nav)
                .overallResilienceScore(resilienceScore)
                .worstCaseDrawdownPercent(worstDrawdownPct)
                .worstCaseLossAmount(worstLossAmount)
                .marginCallTriggered(marginCall)
                .recommendedCashBufferPercent(cashBuffer)
                .recommendedVn30FuturesHedgeContracts(hedgeContracts)
                .institutionalStressVerdict(verdict)
                .scenarioResults(scenarios)
                .build();
    }

    private StressScenarioDetail buildScenario(String id, String name, String desc, double marketShock, double portfolioLoss, BigDecimal nav, String t25Impact) {
        BigDecimal lossPct = BigDecimal.valueOf(portfolioLoss).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lossVnd = nav.multiply(lossPct).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal capitalAfter = nav.subtract(lossVnd);

        String survival;
        if (portfolioLoss <= 14.0) {
            survival = "SURVIVED_HEALTHY";
        } else if (portfolioLoss <= 21.0) {
            survival = "SURVIVED_DRAWDOWN";
        } else if (portfolioLoss <= 25.0) {
            survival = "MARGIN_CALL_RISK";
        } else {
            survival = "INSOLVENCY_CRITICAL";
        }

        return StressScenarioDetail.builder()
                .scenarioId(id)
                .scenarioName(name)
                .historicalEventDescription(desc)
                .marketShockPercent(BigDecimal.valueOf(marketShock).setScale(1, RoundingMode.HALF_UP))
                .portfolioLossPercent(lossPct)
                .portfolioLossVnd(lossVnd)
                .capitalAfterShock(capitalAfter)
                .t25LiquidityFreezeImpact(t25Impact)
                .survivalStatus(survival)
                .build();
    }

    private double calculatePortfolioBeta(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 1.15; // Mặc định rổ VN30
        }
        double weightedBetaSum = 0.0;
        double totalValue = 0.0;

        for (Trade t : trades) {
            double value = t.getPrice().doubleValue() * t.getQuantity();
            double beta = estimateStockBeta(t.getSymbol());
            weightedBetaSum += beta * value;
            totalValue += value;
        }

        return totalValue > 0 ? Math.max(0.70, Math.min(1.60, weightedBetaSum / totalValue)) : 1.15;
    }

    private double estimateStockBeta(String symbol) {
        String sym = symbol.toUpperCase().trim();
        return switch (sym) {
            case "HPG", "HSG", "NKG" -> 1.30;
            case "SSI", "VND", "VCI", "HCM" -> 1.35;
            case "TCB", "MBB", "CTG", "VPB" -> 1.18;
            case "FPT", "MWG" -> 1.05;
            case "VCB", "VNM", "GAS" -> 0.85;
            default -> 1.15;
        };
    }
}