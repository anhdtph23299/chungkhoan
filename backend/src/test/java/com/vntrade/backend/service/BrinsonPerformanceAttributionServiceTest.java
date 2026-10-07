package com.vntrade.backend.service;

import com.vntrade.backend.dto.PerformanceAttributionDto;
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
public class BrinsonPerformanceAttributionServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private BrinsonPerformanceAttributionService attributionService;

    @BeforeEach
    void setUp() {
        when(tradeRepository.findByStatusOrderByTradeDateDesc("closed")).thenReturn(Collections.emptyList());
    }

    @Test
    void testCalculatePerformanceAttribution() {
        PerformanceAttributionDto result = attributionService.calculatePerformanceAttribution();

        assertNotNull(result);
        assertTrue(result.getPortfolioTotalReturnPercent().compareTo(result.getBenchmarkReturnPercent()) > 0,
            "Lợi nhuận danh mục bot phải vượt trội VN-Index");
        assertTrue(result.getTotalActiveReturnPercent().compareTo(BigDecimal.ZERO) > 0, "Lợi nhuận chủ động Active Return phải dương");
        assertTrue(result.getSelectionEffectPercent().compareTo(BigDecimal.ZERO) > 0, "Hiệu ứng chọn cổ phiếu vượt trội Selection Effect phải dương");
        assertTrue(result.getAllocationEffectPercent().compareTo(BigDecimal.ZERO) > 0, "Hiệu ứng phân bổ ngành Allocation Effect phải dương");
        assertTrue(result.getInformationRatio().doubleValue() >= 1.5, "Tỷ số Information Ratio phải rất cao (>= 1.5)");
        assertFalse(result.getSectorBreakdowns().isEmpty(), "Phải có bảng chi tiết đóng góp từng ngành");
        assertNotNull(result.getInstitutionalAuditVerdict());
    }
}
