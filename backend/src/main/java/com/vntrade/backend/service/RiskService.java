package com.vntrade.backend.service;

import com.vntrade.backend.dto.PortfolioHealthReport;
import com.vntrade.backend.dto.PositionSizingRequest;
import com.vntrade.backend.dto.PositionSizingResult;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Slf4j
public class RiskService {

    private final TradeRepository tradeRepository;

    @Autowired
    public RiskService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    public RiskService() {
        this.tradeRepository = null;
    }

    /**
     * Xác định nhóm ngành của mã cổ phiếu
     */
    public String getSectorForSymbol(String symbol) {
        if (symbol == null) return "KHÁC";
        return switch (symbol.toUpperCase().trim()) {
            case "FPT", "CMG", "ELC" -> "CÔNG NGHỆ";
            case "TCB", "VCB", "MBB", "STB", "CTG", "ACB", "VPB", "TPB", "HDB" -> "NGÂN HÀNG";
            case "HPG", "HSG", "NKG", "DGC" -> "THÉP & HÓA CHẤT";
            case "SSI", "VND", "VIX", "HCM", "VCI", "SHS" -> "CHỨNG KHOÁN";
            case "MWG", "MSN", "PNJ", "FRT" -> "BÁN LẺ & TIÊU DÙNG";
            case "VHM", "VIC", "VRE", "KBC", "NVL", "PDR" -> "BẤT ĐỘNG SẢN";
            default -> "KHÁC";
        };
    }

    /**
     * Tính toán tỷ lệ rủi ro động theo chuỗi thắng/thua (Anti-Martingale)
     */
    public BigDecimal getDynamicRiskPercent() {
        if (tradeRepository == null) return BigDecimal.valueOf(1.5);

        List<Trade> closed = tradeRepository.findByStatusOrderByTradeDateDesc("closed");
        if (closed.isEmpty()) return BigDecimal.valueOf(1.5);

        int consecutiveLosses = 0;
        int consecutiveWins = 0;

        for (Trade t : closed) {
            if (t.getPnl() == null) continue;
            if (t.getPnl().compareTo(BigDecimal.ZERO) < 0) {
                if (consecutiveWins > 0) break;
                consecutiveLosses++;
            } else if (t.getPnl().compareTo(BigDecimal.ZERO) > 0) {
                if (consecutiveLosses > 0) break;
                consecutiveWins++;
            }
        }

        if (consecutiveLosses >= 3) {
            log.info("Anti-Martingale: 3 lệnh lỗ liên tiếp. Hạ tỷ lệ rủi ro xuống 0.75% NAV.");
            return BigDecimal.valueOf(0.75);
        } else if (consecutiveLosses >= 2) {
            log.info("Anti-Martingale: 2 lệnh lỗ liên tiếp. Hạ tỷ lệ rủi ro phòng thủ xuống 1.0% NAV.");
            return BigDecimal.valueOf(1.0);
        } else if (consecutiveWins >= 2) {
            log.info("Anti-Martingale: Chuỗi thắng liên tiếp. Duy trì tỷ lệ rủi ro tiêu chuẩn 1.75% NAV.");
            return BigDecimal.valueOf(1.75);
        }

        return BigDecimal.valueOf(1.5);
    }

    /**
     * Kiểm tra giới hạn phân bổ nhóm ngành (Tối đa 35% NAV cho 1 ngành)
     */
    public boolean isSectorAllocationAllowed(String symbol, BigDecimal proposedAmount, BigDecimal totalNav) {
        if (tradeRepository == null || totalNav == null || totalNav.compareTo(BigDecimal.ZERO) <= 0) return true;

        String targetSector = getSectorForSymbol(symbol);
        List<Trade> openTrades = tradeRepository.findOpenBuyTrades();

        BigDecimal currentSectorTotal = BigDecimal.ZERO;
        for (Trade t : openTrades) {
            if (targetSector.equalsIgnoreCase(getSectorForSymbol(t.getSymbol()))) {
                currentSectorTotal = currentSectorTotal.add(t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity())));
            }
        }

        BigDecimal afterAmount = currentSectorTotal.add(proposedAmount != null ? proposedAmount : BigDecimal.ZERO);
        BigDecimal sectorPercent = afterAmount.divide(totalNav, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        if (sectorPercent.compareTo(BigDecimal.valueOf(35.0)) > 0) {
            log.warn("Sector Cap Violated: Ngành {} chiếm {}% NAV (vượt trần an toàn 35%). Từ chối mở thêm vị thế.", targetSector, sectorPercent);
            return false;
        }
        return true;
    }

    /**
     * Đánh giá sức khỏe và phòng thủ rủi ro toàn danh mục
     */
    public PortfolioHealthReport evaluatePortfolioHealth() {
        BigDecimal totalNav = BigDecimal.valueOf(200_000_000);
        List<Trade> openTrades = tradeRepository != null ? tradeRepository.findOpenBuyTrades() : List.of();
        List<Trade> closedTrades = tradeRepository != null ? tradeRepository.findByStatusOrderByTradeDateDesc("closed") : List.of();

        BigDecimal invested = BigDecimal.ZERO;
        Map<String, BigDecimal> sectorAmounts = new HashMap<>();

        for (Trade t : openTrades) {
            BigDecimal val = t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity()));
            invested = invested.add(val);
            String sec = getSectorForSymbol(t.getSymbol());
            sectorAmounts.put(sec, sectorAmounts.getOrDefault(sec, BigDecimal.ZERO).add(val));
        }

        Map<String, BigDecimal> sectorPercents = new HashMap<>();
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> e : sectorAmounts.entrySet()) {
            BigDecimal pct = invested.compareTo(BigDecimal.ZERO) > 0
                ? e.getValue().divide(invested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
            sectorPercents.put(e.getKey(), pct);
            if (pct.compareTo(BigDecimal.valueOf(35.0)) > 0) {
                warnings.add(String.format("Ngành %s chiếm %s%% danh mục (vượt trần 35%%). Đề xuất tái cân bằng.", e.getKey(), pct));
            }
        }

        BigDecimal cashPct = totalNav.compareTo(BigDecimal.ZERO) > 0
            ? totalNav.subtract(invested).divide(totalNav, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.valueOf(100);

        BigDecimal dynamicRisk = getDynamicRiskPercent();
        String streak = "NEUTRAL";
        int streakCount = 0;

        if (!closedTrades.isEmpty()) {
            boolean isWin = closedTrades.get(0).getPnl() != null && closedTrades.get(0).getPnl().compareTo(BigDecimal.ZERO) > 0;
            streak = isWin ? "WIN_STREAK" : "LOSS_STREAK";
            for (Trade t : closedTrades) {
                if (t.getPnl() == null) continue;
                boolean currentWin = t.getPnl().compareTo(BigDecimal.ZERO) > 0;
                if (currentWin == isWin) streakCount++;
                else break;
            }
        }

        String status = "HEALTHY";
        if (!warnings.isEmpty() || streakCount >= 2 && "LOSS_STREAK".equals(streak)) {
            status = "CAUTION";
        }
        if (openTrades.size() >= 4 && cashPct.compareTo(BigDecimal.valueOf(20.0)) < 0) {
            status = "DEFENSIVE";
        }

        List<String> checklist = List.of(
            "✓ Giới hạn Stop Loss tối đa 7% - 8% toàn danh mục",
            "✓ Tỷ lệ Lợi nhuận / Rủi ro R:R tối thiểu 1:2.0",
            warnings.isEmpty() ? "✓ Phân bổ ngành an toàn (< 35% mỗi ngành)" : "⚠ Cảnh báo phân bổ tập trung ngành cao",
            "✓ Quy tắc vị thế Anti-Martingale: rủi ro " + dynamicRisk + "% NAV cho lệnh tiếp theo"
        );

        return PortfolioHealthReport.builder()
            .status(status)
            .currentNav(totalNav)
            .cashPercent(cashPct)
            .investedPercent(BigDecimal.valueOf(100).subtract(cashPct))
            .openPositionsCount(openTrades.size())
            .maxAllowedPositions(4)
            .sectorAllocations(sectorPercents)
            .sectorWarnings(warnings)
            .streakStatus(streak)
            .streakCount(streakCount)
            .recommendedRiskPercent(dynamicRisk)
            .allowNewPurchases(openTrades.size() < 4 && !"DEFENSIVE".equals(status))
            .riskChecklist(checklist)
            .build();
    }

    /**
     * Tính toán kích thước vị thế chuẩn xác theo phương pháp quản trị vốn của các quỹ đầu tư định lượng
     */
    public PositionSizingResult calculatePositionSize(PositionSizingRequest req) {
        BigDecimal capital = req.getAccountCapital() != null && req.getAccountCapital().compareTo(BigDecimal.ZERO) > 0
            ? req.getAccountCapital() : BigDecimal.valueOf(200_000_000);

        BigDecimal riskPct = req.getMaxRiskPercent() != null && req.getMaxRiskPercent().compareTo(BigDecimal.ZERO) > 0
            ? req.getMaxRiskPercent() : getDynamicRiskPercent();

        BigDecimal entry = req.getEntryPrice() != null ? req.getEntryPrice() : BigDecimal.valueOf(35000);

        // Mặc định SL = -7% nếu không nhập
        BigDecimal sl = req.getStopLossPrice() != null && req.getStopLossPrice().compareTo(BigDecimal.ZERO) > 0
            ? req.getStopLossPrice()
            : entry.multiply(BigDecimal.valueOf(0.93)).setScale(0, RoundingMode.HALF_UP);

        // Mặc định TP = +15% nếu không nhập
        BigDecimal tp = req.getTakeProfitPrice() != null && req.getTakeProfitPrice().compareTo(BigDecimal.ZERO) > 0
            ? req.getTakeProfitPrice()
            : entry.multiply(BigDecimal.valueOf(1.15)).setScale(0, RoundingMode.HALF_UP);

        // Tiền rủi ro tối đa = Capital * (RiskPct / 100)
        BigDecimal maxRiskAmount = capital.multiply(riskPct).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

        // Khoảng cách rủi ro trên 1 cổ phiếu
        BigDecimal riskPerShare = entry.subtract(sl);
        if (riskPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            riskPerShare = entry.multiply(BigDecimal.valueOf(0.07));
        }

        // Khoảng cách lợi nhuận trên 1 cổ phiếu
        BigDecimal profitPerShare = tp.subtract(entry);
        if (profitPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            profitPerShare = BigDecimal.ZERO;
        }

        // % Cắt lỗ & % Chốt lời
        BigDecimal slPct = riskPerShare.divide(entry, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        BigDecimal tpPct = profitPerShare.divide(entry, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        // Tỷ lệ R:R
        BigDecimal rrRatio = riskPerShare.compareTo(BigDecimal.ZERO) > 0
            ? profitPerShare.divide(riskPerShare, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Số cổ phiếu tối đa = maxRiskAmount / riskPerShare, làm tròn xuống lô 100 của sàn VN
        int rawShares = maxRiskAmount.divide(riskPerShare, 0, RoundingMode.FLOOR).intValue();
        int maxShares = (rawShares / 100) * 100;
        if (maxShares < 100) maxShares = 100; // Tối thiểu 1 lô 100 cp

        // Tổng vốn giải ngân
        BigDecimal totalCapital = BigDecimal.valueOf(maxShares).multiply(entry);
        BigDecimal allocPct = totalCapital.divide(capital, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        BigDecimal expectedLoss = BigDecimal.valueOf(maxShares).multiply(riskPerShare);
        BigDecimal expectedProfit = BigDecimal.valueOf(maxShares).multiply(profitPerShare);

        // Đánh giá 5 quy tắc kỷ luật
        List<String> rules = new ArrayList<>();
        boolean acceptable = true;

        if (rrRatio.compareTo(BigDecimal.valueOf(2.0)) >= 0) {
            rules.add("✓ Tỷ lệ R:R = 1:" + rrRatio + " (ĐẠT CHUẨN: Lợi nhuận kỳ vọng gấp đôi rủi ro)");
        } else {
            acceptable = false;
            rules.add("✕ Tỷ lệ R:R = 1:" + rrRatio + " (KHÔNG ĐẠT: R:R tối thiểu phải từ 1:2.0 trở lên)");
        }

        if (slPct.compareTo(BigDecimal.valueOf(8.0)) <= 0) {
            rules.add("✓ Ngưỡng cắt lỗ = -" + slPct.setScale(1, RoundingMode.HALF_UP) + "% (ĐẠT CHUẨN: Dưới mức trần 8% theo nguyên tắc O'Neil)");
        } else {
            acceptable = false;
            rules.add("✕ Ngưỡng cắt lỗ = -" + slPct.setScale(1, RoundingMode.HALF_UP) + "% (QUÁ LỚN: Không được để mức lỗ vượt quá 8% cho bất kỳ thương vụ nào)");
        }

        if (allocPct.compareTo(BigDecimal.valueOf(30.0)) <= 0) {
            rules.add("✓ Tỷ trọng vốn = " + allocPct.setScale(1, RoundingMode.HALF_UP) + "% NAV (AN TOÀN: Không vượt quá 30% tài khoản cho 1 mã)");
        } else {
            rules.add("⚠ Cảnh báo tỷ trọng vốn = " + allocPct.setScale(1, RoundingMode.HALF_UP) + "% NAV (Hơi cao: Nên hạ bớt số lượng cổ phiếu để tránh rủi ro tập trung)");
        }

        String verdict;
        if (acceptable) {
            verdict = "✓ LỆNH ĐẠT CHUẨN KỶ LUẬT ĐẦU TƯ";
        } else {
            verdict = "✕ CẢNH BÁO VI PHẠM KỶ LUẬT QUẢN TRỊ RỦI RO";
        }

        return PositionSizingResult.builder()
            .symbol(req.getSymbol() != null ? req.getSymbol().toUpperCase() : "STOCK")
            .maxRiskAmount(maxRiskAmount)
            .riskPerShare(riskPerShare)
            .profitPerShare(profitPerShare)
            .stopLossPercent(slPct)
            .takeProfitPercent(tpPct)
            .maxSharesToBuy(maxShares)
            .totalCapitalRequired(totalCapital)
            .allocationPercent(allocPct)
            .riskRewardRatio(rrRatio)
            .acceptable(acceptable)
            .verdict(verdict)
            .rulesEvaluated(rules)
            .expectedLossAmount(expectedLoss)
            .expectedProfitAmount(expectedProfit)
            .build();
    }
}

