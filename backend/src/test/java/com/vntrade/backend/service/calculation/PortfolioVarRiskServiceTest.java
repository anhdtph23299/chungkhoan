package com.vntrade.backend.service.calculation;

import com.vntrade.backend.dto.PortfolioVarRiskDto;
import com.vntrade.backend.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PortfolioVarRiskServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private PortfolioVarRiskService varRiskService;

    @BeforeEach
    void setUp() {
        when(tradeRepository.findByStatusOrderByTradeDateDesc("open")).thenReturn(Collections.emptyList());
    }

    @Test
    void testCalculatePortfolioVarRisk() {
        BigDecimal nav = BigDecimal.valueOf(200_000_000);
        PortfolioVarRiskDto report = varRiskService.calculatePortfolioVarRisk(nav);

        assertNotNull(report);
        assertEquals(nav, report.getCurrentPortfolioNav());
        assertTrue(report.getPortfolioBeta().doubleValue() > 0, "Beta danh mục phải dương");
        assertTrue(report.getParametricVar95Amount().compareTo(BigDecimal.ZERO) > 0, "VaR 95% phải dương");
        assertTrue(report.getConditionalVar99Amount().compareTo(report.getParametricVar95Amount()) > 0, "CVaR 99% (Expected Shortfall) phải lớn hơn VaR 95%");
        assertNotNull(report.getCornishFisherVar95Amount(), "Cornish-Fisher VaR phải được tính toán");
        assertTrue(report.getCornishFisherVar95Amount().compareTo(report.getParametricVar95Amount()) >= 0, "Cornish-Fisher VaR hiệu chỉnh đuôi béo phải >= Gaussian VaR");
        assertNotNull(report.getT25MultiDayHoldingVaR95Amount(), "Rủi ro kỳ hạn khóa T+2.5 phải được tính toán");
        assertTrue(report.getT25MultiDayHoldingVaR95Amount().compareTo(report.getCornishFisherVar95Amount()) > 0, "Rủi ro T+2.5 phải lớn hơn VaR 1 ngày");
        assertNotNull(report.getT25LiquidityReserveRequired(), "Quỹ đệm tiền mặt dự phòng T+2.5 phải được xác định");
        assertFalse(report.getCorrelationMatrix().isEmpty(), "Phải có ma trận tương quan giữa các cặp tài sản");
        assertNotNull(report.getRiskOfficerVerdict());
    }
}
