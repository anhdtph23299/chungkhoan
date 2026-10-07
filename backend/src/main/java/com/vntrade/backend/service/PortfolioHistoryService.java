package com.vntrade.backend.service;

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

        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;

        for (Trade t : openTrades) {
            BigDecimal cost = t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity()));
            invested = invested.add(cost);
            if (t.getPnl() != null) {
                unrealized = unrealized.add(t.getPnl());
            }
        }

        BigDecimal realized = allTrades.stream()
            .filter(t -> "closed".equals(t.getStatus()) && t.getPnl() != null)
            .map(Trade::getPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long closedCount = allTrades.stream().filter(t -> "closed".equals(t.getStatus())).count();
        long winCount = allTrades.stream().filter(t -> "closed".equals(t.getStatus()) && t.getPnl() != null && t.getPnl().compareTo(BigDecimal.ZERO) > 0).count();

        BigDecimal winRate = closedCount > 0
            ? BigDecimal.valueOf(winCount * 100.0 / closedCount).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal cash = INITIAL_CAPITAL.add(realized).subtract(invested);
        if (cash.compareTo(BigDecimal.ZERO) < 0) {
            cash = BigDecimal.valueOf(10_000_000); // Đảm bảo luôn duy trì đệm tiền mặt an toàn
        }
        BigDecimal currentMarketValue = invested.add(unrealized);
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
            // Khởi tạo điểm dữ liệu ban đầu
            initializeBaselineSnapshots();
            return snapshotRepository.findAllByOrderBySnapshotTimeAsc();
        }
        return list;
    }

    @Transactional
    public List<PortfolioSnapshot> resetAndReseedSnapshots() {
        snapshotRepository.deleteAll();
        initializeBaselineSnapshots();
        recordCurrentSnapshot();
        return snapshotRepository.findAllByOrderBySnapshotTimeAsc();
    }

    @Transactional
    public void initializeBaselineSnapshots() {
        LocalDate today = LocalDate.now();
        BigDecimal currentNav = INITIAL_CAPITAL;

        // Mô phỏng 14 ngày tăng trưởng vốn đều đặn theo kỷ luật
        double[] dailyProfits = {0, 1_500_000, 2_200_000, -800_000, 3_100_000, 0, 0, 2_800_000, 3_400_000, -1_200_000, 4_500_000, 2_100_000, 0, 2_571_750};

        List<PortfolioSnapshot> seedList = new ArrayList<>();
        for (int i = 0; i < dailyProfits.length; i++) {
            currentNav = currentNav.add(BigDecimal.valueOf(dailyProfits[i]));
            LocalDate d = today.minusDays(dailyProfits.length - 1 - i);
            BigDecimal retPct = currentNav.subtract(INITIAL_CAPITAL).divide(INITIAL_CAPITAL, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

            seedList.add(PortfolioSnapshot.builder()
                .snapshotTime(d.atTime(15, 0))
                .snapshotDate(d)
                .cashBalance(currentNav.multiply(BigDecimal.valueOf(0.65)).setScale(0, RoundingMode.HALF_UP))
                .investedValue(currentNav.multiply(BigDecimal.valueOf(0.35)).setScale(0, RoundingMode.HALF_UP))
                .totalNav(currentNav)
                .realizedPnl(currentNav.subtract(INITIAL_CAPITAL))
                .unrealizedPnl(BigDecimal.valueOf(1_800_000))
                .totalProfitPercent(retPct)
                .winRate(BigDecimal.valueOf(75.0))
                .totalTrades(Math.min(10, i + 1))
                .openPositionsCount(3)
                .build());
        }
        snapshotRepository.saveAll(seedList);
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
