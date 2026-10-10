package com.vntrade.backend.service.risk;

import com.vntrade.backend.dto.RealMoneyAuditDto;
import com.vntrade.backend.dto.TradeAnalytics;
import com.vntrade.backend.entity.PortfolioSnapshot;
import com.vntrade.backend.repository.PortfolioSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.portfolio.JournalService;

class RealMoneyAuditServiceTest {

    private JournalService journalService;
    private PortfolioSnapshotRepository snapshotRepository;
    private RealMoneyAuditService auditService;

    @BeforeEach
    void setUp() {
        journalService = Mockito.mock(JournalService.class);
        snapshotRepository = Mockito.mock(PortfolioSnapshotRepository.class);
        auditService = new RealMoneyAuditService(journalService, snapshotRepository);
    }

    @Test
    @DisplayName("Kiểm tra bộ tiêu chuẩn kiểm định đánh tiền thật: Đánh giá 7 tiêu chuẩn an toàn vốn")
    void testConductAudit() {
        TradeAnalytics mockAnalytics = TradeAnalytics.builder()
            .totalTrades(12)
            .winningTrades(9)
            .losingTrades(3)
            .winRate(BigDecimal.valueOf(75.0))
            .profitFactor(BigDecimal.valueOf(4.5))
            .mathematicalExpectancy(BigDecimal.valueOf(2_500_000))
            .winLossRatio(BigDecimal.valueOf(2.8))
            .netProfit(BigDecimal.valueOf(25_000_000))
            .build();

        PortfolioSnapshot snap1 = PortfolioSnapshot.builder()
            .snapshotTime(LocalDateTime.now().minusDays(5))
            .totalNav(BigDecimal.valueOf(200_000_000))
            .build();
        PortfolioSnapshot snap2 = PortfolioSnapshot.builder()
            .snapshotTime(LocalDateTime.now())
            .totalNav(BigDecimal.valueOf(225_000_000))
            .build();

        when(journalService.computeAnalytics()).thenReturn(mockAnalytics);
        when(snapshotRepository.findAllByOrderBySnapshotTimeAsc()).thenReturn(List.of(snap1, snap2));

        RealMoneyAuditDto audit = auditService.conductAudit();

        assertNotNull(audit);
        assertEquals(7, audit.getCriteria().size());
        assertTrue(audit.isCertifiedForRealMoney());
        assertTrue(audit.getReadinessScorePercent() >= 85);
        assertTrue(audit.getExecutiveSummary().contains("ĐÃ SẴN SÀNG ĐÁNH TIỀN THẬT"));
        assertEquals(0, audit.getCurrentNav().compareTo(BigDecimal.valueOf(225_000_000)));
    }
}