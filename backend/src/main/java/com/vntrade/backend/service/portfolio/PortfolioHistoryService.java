package com.vntrade.backend.service.portfolio;

import com.vntrade.backend.dto.PerformanceComparisonDto;
import com.vntrade.backend.entity.PortfolioSnapshot;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.PortfolioSnapshotRepository;
import com.vntrade.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.vntrade.backend.service.marketdata.SseStreamService;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioHistoryService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final TradeRepository tradeRepository;
    private final SseStreamService sseStreamService;

    private static final BigDecimal INITIAL_CAPITAL = BigDecimal.valueOf(100_000_000); // Vốn 100tr chuẩn Ngày 1

    @Transactional
    public PortfolioSnapshot recordCurrentSnapshot() {
        List<Trade> openTrades = tradeRepository.findOpenBuyTrades();
        List<Trade> allTrades = tradeRepository.findAllByOrderByTradeDateDesc();

        BigDecimal investedCostWithFee = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        BigDecimal partialRealized = BigDecimal.ZERO;

        for (Trade t : openTrades) {
            BigDecimal cost = t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity()));
            BigDecimal fee = t.getFee() != null ? t.getFee() : BigDecimal.ZERO;
            investedCostWithFee = investedCostWithFee.add(cost).add(fee);
            if (t.getPnl() != null) {
                unrealized = unrealized.add(t.getPnl());
            }
            if (t.getPartialRealizedPnl() != null) {
                partialRealized = partialRealized.add(t.getPartialRealizedPnl());
            }
        }

        BigDecimal realized = allTrades.stream()
            .filter(t -> "closed".equalsIgnoreCase(t.getStatus()))
            .map(t -> (t.getPnl() != null ? t.getPnl() : BigDecimal.ZERO)
                .add(t.getPartialRealizedPnl() != null ? t.getPartialRealizedPnl() : BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(partialRealized);

        long closedCount = allTrades.stream().filter(t -> "closed".equalsIgnoreCase(t.getStatus())).count();
        long winCount = allTrades.stream()
            .filter(t -> "closed".equalsIgnoreCase(t.getStatus()) && t.getPnl() != null && t.getPnl().compareTo(BigDecimal.ZERO) > 0)
            .count();

        BigDecimal winRate = closedCount > 0
            ? BigDecimal.valueOf(winCount * 100.0 / closedCount).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal cash = INITIAL_CAPITAL.add(realized).subtract(investedCostWithFee);
        BigDecimal investedCost = openTrades.stream()
            .map(t -> t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentMarketValue = investedCost.add(unrealized);
        BigDecimal totalNav = cash.add(currentMarketValue);

        BigDecimal totalReturn = INITIAL_CAPITAL.compareTo(BigDecimal.ZERO) > 0
            ? totalNav.subtract(INITIAL_CAPITAL).divide(INITIAL_CAPITAL, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        PortfolioSnapshot snapshot = PortfolioSnapshot.builder()
            .snapshotTime(LocalDateTime.now())
            .snapshotDate(LocalDate.now())
            .cashBalance(cash)
            .investedValue(currentMarketValue)
            .totalNav(totalNav)
            .realizedPnl(realized)
            .unrealizedPnl(unrealized)
            .totalProfitPercent(totalReturn)
            .winRate(winRate)
            .totalTrades((int) closedCount)
            .openPositionsCount(openTrades.size())
            .build();

        PortfolioSnapshot saved = snapshotRepository.save(snapshot);

        try {
            sseStreamService.broadcast("EQUITY_SNAPSHOT", saved);
        } catch (Exception ignored) {}

        log.info("Recorded Portfolio Snapshot: NAV = {} đ (Lợi nhuận ròng: {}% | Tỷ lệ thắng: {}%)",
            totalNav, totalReturn, winRate);
        return saved;
    }

    public List<PortfolioSnapshot> getEquityCurve() {
        List<PortfolioSnapshot> list = snapshotRepository.findAllByOrderBySnapshotTimeAsc();
        if (list.isEmpty()) {
            initializeBaselineSnapshots();
            return snapshotRepository.findAllByOrderBySnapshotTimeAsc();
        }
        return list;
    }

    @Transactional
    public List<PortfolioSnapshot> resetAndReseedSnapshots() {
        snapshotRepository.deleteAll();
        initializeBaselineSnapshots();
        return snapshotRepository.findAllByOrderBySnapshotTimeAsc();
    }

    /**
     * Khởi tạo đúng 1 điểm dữ liệu gốc trung thực (100,000,000 đ tiền mặt, 0% lãi giả lập).
     */
    @Transactional
    public void initializeBaselineSnapshots() {
        LocalDate today = LocalDate.now();
        PortfolioSnapshot initial = PortfolioSnapshot.builder()
            .snapshotTime(LocalDateTime.now())
            .snapshotDate(today)
            .cashBalance(INITIAL_CAPITAL)
            .investedValue(BigDecimal.ZERO)
            .totalNav(INITIAL_CAPITAL)
            .realizedPnl(BigDecimal.ZERO)
            .unrealizedPnl(BigDecimal.ZERO)
            .totalProfitPercent(BigDecimal.ZERO)
            .winRate(BigDecimal.ZERO)
            .totalTrades(0)
            .openPositionsCount(0)
            .build();
        snapshotRepository.save(initial);
    }

    /**
     * So sánh hiệu suất danh mục đa kỳ: Hôm nay vs 1 Ngày trước (DoD), 7 Ngày trước (WoW), 30 Ngày trước (MoM), 3 Tháng trước (QoQ)
     * với toàn bộ số liệu tính toán trung thực từ dữ liệu DB thực tế.
     */
    public PerformanceComparisonDto getPerformanceComparison() {
        List<Trade> openTrades = tradeRepository.findOpenBuyTrades();
        List<Trade> allTrades = tradeRepository.findAllByOrderByTradeDateDesc();

        BigDecimal investedCost = BigDecimal.ZERO;
        BigDecimal investedCostWithFee = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        BigDecimal partialRealized = BigDecimal.ZERO;

        for (Trade t : openTrades) {
            BigDecimal cost = t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity()));
            BigDecimal fee = t.getFee() != null ? t.getFee() : BigDecimal.ZERO;
            investedCost = investedCost.add(cost);
            investedCostWithFee = investedCostWithFee.add(cost).add(fee);
            if (t.getPnl() != null) unrealized = unrealized.add(t.getPnl());
            if (t.getPartialRealizedPnl() != null) partialRealized = partialRealized.add(t.getPartialRealizedPnl());
        }

        List<Trade> closedTrades = allTrades.stream()
            .filter(t -> "closed".equalsIgnoreCase(t.getStatus()))
            .toList();

        BigDecimal realized = closedTrades.stream()
            .map(t -> (t.getPnl() != null ? t.getPnl() : BigDecimal.ZERO)
                .add(t.getPartialRealizedPnl() != null ? t.getPartialRealizedPnl() : BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(partialRealized);

        BigDecimal cash = INITIAL_CAPITAL.add(realized).subtract(investedCostWithFee);
        BigDecimal currentMarketValue = investedCost.add(unrealized);
        BigDecimal currentNav = cash.add(currentMarketValue);

        long closedCount = closedTrades.size();
        long winCount = closedTrades.stream()
            .filter(t -> t.getPnl() != null && t.getPnl().compareTo(BigDecimal.ZERO) > 0)
            .count();
        BigDecimal actualWinRate = closedCount > 0
            ? BigDecimal.valueOf(winCount * 100.0 / closedCount).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        List<PortfolioSnapshot> snapshots = snapshotRepository.findAllByOrderBySnapshotTimeAsc();
        if (snapshots.isEmpty()) {
            initializeBaselineSnapshots();
            snapshots = snapshotRepository.findAllByOrderBySnapshotTimeAsc();
        }

        PortfolioSnapshot latest = snapshots.get(snapshots.size() - 1);
        LocalDate today = LocalDate.now();

        if (latest.getSnapshotDate().isEqual(today)) {
            latest.setTotalNav(currentNav);
            latest.setUnrealizedPnl(unrealized);
            latest.setRealizedPnl(realized);
            latest.setInvestedValue(currentMarketValue);
            latest.setCashBalance(cash);
            latest.setWinRate(actualWinRate);
            latest.setTotalTrades((int) closedCount);
            latest.setOpenPositionsCount(openTrades.size());
            BigDecimal retPct = INITIAL_CAPITAL.compareTo(BigDecimal.ZERO) > 0
                ? currentNav.subtract(INITIAL_CAPITAL).divide(INITIAL_CAPITAL, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
            latest.setTotalProfitPercent(retPct);
            snapshotRepository.save(latest);
        }

        PortfolioSnapshot oneDaySnap = findClosestSnapshot(snapshots, today.minusDays(1));
        PortfolioSnapshot oneWeekSnap = findClosestSnapshot(snapshots, today.minusDays(7));
        PortfolioSnapshot oneMonthSnap = findClosestSnapshot(snapshots, today.minusDays(30));
        PortfolioSnapshot threeMonthsSnap = findClosestSnapshot(snapshots, today.minusDays(90));

        PerformanceComparisonDto.PeriodComparisonItem dod = buildPeriodComparison("1 Ngày Trước (DoD)", oneDaySnap, currentNav);
        PerformanceComparisonDto.PeriodComparisonItem wow = buildPeriodComparison("7 Ngày Trước (WoW)", oneWeekSnap, currentNav);
        PerformanceComparisonDto.PeriodComparisonItem mom = buildPeriodComparison("30 Ngày Trước (MoM)", oneMonthSnap, currentNav);
        PerformanceComparisonDto.PeriodComparisonItem qoq = buildPeriodComparison("3 Tháng Trước (QoQ)", threeMonthsSnap, currentNav);

        List<PerformanceComparisonDto.MonthlyPerformanceItem> monthlyBreakdown = buildMonthlyBreakdown(snapshots);
        List<PerformanceComparisonDto.DailyPerformanceItem> dailyBreakdown = buildDailyBreakdown(snapshots);

        BigDecimal profitVnd = currentNav.subtract(INITIAL_CAPITAL);
        BigDecimal profitPct = INITIAL_CAPITAL.compareTo(BigDecimal.ZERO) > 0
            ? profitVnd.divide(INITIAL_CAPITAL, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        // Tính thực tế Profit Factor & Max Drawdown từ lịch sử thật
        double grossProfit = closedTrades.stream()
            .filter(t -> t.getPnl() != null && t.getPnl().compareTo(BigDecimal.ZERO) > 0)
            .mapToDouble(t -> t.getPnl().doubleValue()).sum();
        double grossLoss = Math.abs(closedTrades.stream()
            .filter(t -> t.getPnl() != null && t.getPnl().compareTo(BigDecimal.ZERO) < 0)
            .mapToDouble(t -> t.getPnl().doubleValue()).sum());
        BigDecimal realProfitFactor = grossLoss > 0
            ? BigDecimal.valueOf(grossProfit / grossLoss).setScale(2, RoundingMode.HALF_UP)
            : (grossProfit > 0 ? BigDecimal.valueOf(9.99) : BigDecimal.ZERO);

        double peakNav = INITIAL_CAPITAL.doubleValue();
        double maxDd = 0.0;
        for (PortfolioSnapshot s : snapshots) {
            if (s.getTotalNav() != null) {
                double nav = s.getTotalNav().doubleValue();
                if (nav > peakNav) peakNav = nav;
                if (peakNav > 0) {
                    double dd = ((peakNav - nav) / peakNav) * 100.0;
                    if (dd > maxDd) maxDd = dd;
                }
            }
        }

        String rating = closedCount < 5 ? "CHỜ KIỂM CHỨNG (< 5 LỆNH ĐÓNG)"
            : (profitPct.doubleValue() > 0 && realProfitFactor.doubleValue() >= 1.5 ? "ĐẠT CHUẨN ĐỊNH LƯỢNG" : "ĐANG TÍCH LŨY DỮ LIỆU");

        String summary = String.format(
            "TỔNG KẾT HIỆU SUẤT VỐN: NAV hiện tại %,.0f đ (Lãi lũy kế %s%% so với vốn gốc %,.0f đ). " +
            "So với 1 ngày trước: %s%s đ (%s%%) | Tỷ lệ thắng thực tế %s%% qua %d lệnh đóng.",
            currentNav.doubleValue(),
            profitPct.setScale(2, RoundingMode.HALF_UP),
            INITIAL_CAPITAL.doubleValue(),
            dod.getDeltaNavVnd().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "",
            dod.getDeltaNavVnd().setScale(0, RoundingMode.HALF_UP).toPlainString(),
            dod.getDeltaNavPercent().setScale(2, RoundingMode.HALF_UP),
            actualWinRate,
            closedCount
        );

        return PerformanceComparisonDto.builder()
            .asOfDate(today)
            .currentNav(currentNav)
            .initialCapital(INITIAL_CAPITAL)
            .totalProfitVnd(profitVnd)
            .totalProfitPercent(profitPct)
            .currentWinRate(actualWinRate)
            .currentTotalTrades((int) closedCount)
            .openPositionsCount(openTrades.size())
            .oneDayAgo(dod)
            .oneWeekAgo(wow)
            .oneMonthAgo(mom)
            .threeMonthsAgo(qoq)
            .monthlyBreakdown(monthlyBreakdown)
            .dailyBreakdown(dailyBreakdown)
            .sharpeRatio(BigDecimal.ZERO)
            .maxDrawdownPercent(BigDecimal.valueOf(maxDd).setScale(2, RoundingMode.HALF_UP))
            .profitFactor(realProfitFactor)
            .hedgeFundRating(rating)
            .executiveSummary(summary)
            .build();
    }

    private PortfolioSnapshot findClosestSnapshot(List<PortfolioSnapshot> snapshots, LocalDate targetDate) {
        if (snapshots.isEmpty()) return null;
        PortfolioSnapshot best = snapshots.get(0);
        long minDiff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(best.getSnapshotDate(), targetDate));

        for (PortfolioSnapshot s : snapshots) {
            long diff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(s.getSnapshotDate(), targetDate));
            if (diff < minDiff) {
                minDiff = diff;
                best = s;
            }
        }
        return best;
    }

    private PerformanceComparisonDto.PeriodComparisonItem buildPeriodComparison(
            String label, PortfolioSnapshot baseline, BigDecimal currentNav) {
        if (baseline == null) {
            return PerformanceComparisonDto.PeriodComparisonItem.builder()
                .periodLabel(label)
                .baselineDate(LocalDate.now())
                .nav(currentNav)
                .deltaNavVnd(BigDecimal.ZERO)
                .deltaNavPercent(BigDecimal.ZERO)
                .winRate(BigDecimal.ZERO)
                .totalTrades(0)
                .performanceVerdict("Chưa có đủ dữ liệu lịch sử")
                .build();
        }

        BigDecimal deltaVnd = currentNav.subtract(baseline.getTotalNav());
        BigDecimal deltaPct = baseline.getTotalNav().compareTo(BigDecimal.ZERO) > 0
            ? deltaVnd.divide(baseline.getTotalNav(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        String verdict;
        if (deltaPct.compareTo(BigDecimal.ZERO) > 0) {
            verdict = String.format("TĂNG TRƯỞNG (+%s%%)", deltaPct.setScale(2, RoundingMode.HALF_UP));
        } else if (deltaPct.compareTo(BigDecimal.ZERO) < 0) {
            verdict = String.format("ĐIỀU CHỈNH (%s%%)", deltaPct.setScale(2, RoundingMode.HALF_UP));
        } else {
            verdict = "BẢO TOÀN VỐN (0.00%)";
        }

        return PerformanceComparisonDto.PeriodComparisonItem.builder()
            .periodLabel(label)
            .baselineDate(baseline.getSnapshotDate())
            .nav(baseline.getTotalNav())
            .deltaNavVnd(deltaVnd)
            .deltaNavPercent(deltaPct)
            .winRate(baseline.getWinRate() != null ? baseline.getWinRate() : BigDecimal.ZERO)
            .totalTrades(baseline.getTotalTrades() != null ? baseline.getTotalTrades() : 0)
            .performanceVerdict(verdict)
            .build();
    }

    private List<PerformanceComparisonDto.MonthlyPerformanceItem> buildMonthlyBreakdown(List<PortfolioSnapshot> snapshots) {
        java.util.Map<java.time.YearMonth, List<PortfolioSnapshot>> grouped = snapshots.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                s -> java.time.YearMonth.from(s.getSnapshotDate()),
                java.util.LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));

        List<PerformanceComparisonDto.MonthlyPerformanceItem> list = new ArrayList<>();
        List<java.time.YearMonth> sortedMonths = new ArrayList<>(grouped.keySet());
        sortedMonths.sort(java.util.Collections.reverseOrder());

        for (java.time.YearMonth ym : sortedMonths) {
            List<PortfolioSnapshot> monthSnaps = grouped.get(ym);
            if (monthSnaps == null || monthSnaps.isEmpty()) continue;

            PortfolioSnapshot firstSnap = monthSnaps.get(0);
            PortfolioSnapshot lastSnap = monthSnaps.get(monthSnaps.size() - 1);

            BigDecimal startNav = firstSnap.getTotalNav();
            BigDecimal endNav = lastSnap.getTotalNav();
            BigDecimal netProfit = endNav.subtract(startNav);
            BigDecimal retPct = startNav.compareTo(BigDecimal.ZERO) > 0
                ? netProfit.divide(startNav, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

            int tradesInMonth = Math.max(0, (lastSnap.getTotalTrades() != null ? lastSnap.getTotalTrades() : 0) -
                (firstSnap.getTotalTrades() != null ? firstSnap.getTotalTrades() : 0));

            String badge = retPct.compareTo(BigDecimal.ZERO) > 0 ? "PROFITABLE" : (retPct.compareTo(BigDecimal.ZERO) < 0 ? "DRAWDOWN" : "FLAT");
            String monthLabel = String.format("Tháng %02d/%d", ym.getMonthValue(), ym.getYear());

            list.add(PerformanceComparisonDto.MonthlyPerformanceItem.builder()
                .monthLabel(monthLabel)
                .startNav(startNav)
                .endNav(endNav)
                .netProfitVnd(netProfit)
                .returnPercent(retPct)
                .winRate(lastSnap.getWinRate() != null ? lastSnap.getWinRate() : BigDecimal.ZERO)
                .tradesCount(tradesInMonth)
                .statusBadge(badge)
                .build());
        }
        return list;
    }

    private List<PerformanceComparisonDto.DailyPerformanceItem> buildDailyBreakdown(List<PortfolioSnapshot> snapshots) {
        java.util.Map<LocalDate, List<PortfolioSnapshot>> grouped = snapshots.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                PortfolioSnapshot::getSnapshotDate,
                java.util.LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));

        List<PerformanceComparisonDto.DailyPerformanceItem> list = new ArrayList<>();
        List<LocalDate> sortedDates = new ArrayList<>(grouped.keySet());
        sortedDates.sort(java.util.Collections.reverseOrder()); // Ngày gần nhất lên trước

        LocalDate today = LocalDate.now();
        for (int i = 0; i < sortedDates.size(); i++) {
            LocalDate d = sortedDates.get(i);
            List<PortfolioSnapshot> daySnaps = grouped.get(d);
            if (daySnaps == null || daySnaps.isEmpty()) continue;

            PortfolioSnapshot lastSnap = daySnaps.get(daySnaps.size() - 1);
            BigDecimal endNav = lastSnap.getTotalNav();

            // startNav là endNav của phiên trước liền kề (nếu có), hoặc snapshot đầu tiên của ngày
            BigDecimal startNav;
            if (i + 1 < sortedDates.size()) {
                LocalDate prevDate = sortedDates.get(i + 1);
                List<PortfolioSnapshot> prevDaySnaps = grouped.get(prevDate);
                startNav = (prevDaySnaps != null && !prevDaySnaps.isEmpty())
                    ? prevDaySnaps.get(prevDaySnaps.size() - 1).getTotalNav()
                    : daySnaps.get(0).getTotalNav();
            } else {
                startNav = daySnaps.get(0).getTotalNav();
            }

            BigDecimal netProfit = endNav.subtract(startNav);
            BigDecimal retPct = startNav.compareTo(BigDecimal.ZERO) > 0
                ? netProfit.divide(startNav, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

            String label;
            if (d.isEqual(today)) {
                label = d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " (Hôm nay)";
            } else if (d.isEqual(today.minusDays(1))) {
                label = d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " (Hôm qua)";
            } else {
                label = d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }

            String badge = retPct.compareTo(BigDecimal.ZERO) > 0 ? "PROFITABLE" : (retPct.compareTo(BigDecimal.ZERO) < 0 ? "DRAWDOWN" : "FLAT");

            list.add(PerformanceComparisonDto.DailyPerformanceItem.builder()
                .date(d)
                .dateLabel(label)
                .startNav(startNav)
                .endNav(endNav)
                .netProfitVnd(netProfit)
                .returnPercent(retPct)
                .winRate(lastSnap.getWinRate() != null ? lastSnap.getWinRate() : BigDecimal.ZERO)
                .tradesCount(lastSnap.getTotalTrades() != null ? lastSnap.getTotalTrades() : 0)
                .openPositionsCount(lastSnap.getOpenPositionsCount() != null ? lastSnap.getOpenPositionsCount() : 0)
                .statusBadge(badge)
                .build());
        }
        return list;
    }

    /**
     * Tự động ghi nhận snapshot định kỳ mỗi 5 phút
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 15_000)
    public void scheduledSnapshot() {
        try {
            recordCurrentSnapshot();
        } catch (Exception e) {
            log.error("Error in scheduledSnapshot: {}", e.getMessage());
        }
    }
}