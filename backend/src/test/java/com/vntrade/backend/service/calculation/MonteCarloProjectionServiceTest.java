package com.vntrade.backend.service.calculation;

import com.vntrade.backend.dto.DailyIncomeDto;
import com.vntrade.backend.dto.WealthProjectionDto;
import com.vntrade.backend.entity.PortfolioSnapshot;
import com.vntrade.backend.repository.PortfolioSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.execution.DailyIncomeService;
import com.vntrade.backend.service.risk.RiskService;

@ExtendWith(MockitoExtension.class)
public class MonteCarloProjectionServiceTest {

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private DailyIncomeService dailyIncomeService;

    @Mock
    private RiskService riskService;

    @InjectMocks
    private MonteCarloProjectionService monteCarloService;

    @BeforeEach
    void setUp() {
        PortfolioSnapshot snap = PortfolioSnapshot.builder()
            .totalNav(BigDecimal.valueOf(220_000_000))
            .realizedPnl(BigDecimal.valueOf(20_000_000))
            .build();
        when(snapshotRepository.findAllByOrderBySnapshotTimeAsc()).thenReturn(List.of(snap));

        DailyIncomeDto income = DailyIncomeDto.builder()
            .dailyRealizedProfit(BigDecimal.valueOf(1_500_000))
            .winRateToday(BigDecimal.valueOf(75.0))
            .build();
        when(dailyIncomeService.getTodayIncomeReport()).thenReturn(income);
    }

    @Test
    void testCalculate90DayWealthProjection() {
        WealthProjectionDto dto = monteCarloService.calculate90DayWealthProjection();

        assertNotNull(dto);
        assertEquals(BigDecimal.valueOf(200_000_000), dto.getInitialCapital());
        assertEquals(BigDecimal.valueOf(220_000_000), dto.getCurrentNav());
        assertTrue(dto.getProjectedNav90Days().compareTo(dto.getCurrentNav()) > 0, "NAV sau 90 ngày phải tăng trưởng");
        assertTrue(dto.getProjectedWithdrawableCash90Days().compareTo(BigDecimal.ZERO) > 0, "Tiền mặt rút tiêu xài phải > 0");
        assertTrue(dto.getEstimatedDaysToDoubleNav() > 0, "Thời gian nhân đôi tài khoản phải dương");
        assertNotNull(dto.getFinancialIndependenceVerdict());
        assertFalse(dto.getProjectionPoints().isEmpty(), "Chuỗi điểm mô phỏng đường cong tăng trưởng không được rỗng");
    }
}