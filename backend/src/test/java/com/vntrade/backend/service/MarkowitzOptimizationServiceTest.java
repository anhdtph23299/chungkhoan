package com.vntrade.backend.service;

import com.vntrade.backend.dto.MarkowitzEfficientFrontierDto;
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
public class MarkowitzOptimizationServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private MarkowitzOptimizationService markowitzService;

    @BeforeEach
    void setUp() {
        when(tradeRepository.findByStatusOrderByTradeDateDesc("open")).thenReturn(Collections.emptyList());
    }

    @Test
    void testCalculateEfficientFrontier() {
        BigDecimal capital = BigDecimal.valueOf(300_000_000);
        MarkowitzEfficientFrontierDto result = markowitzService.calculateEfficientFrontier(capital);

        assertNotNull(result);
        assertEquals(capital, result.getCurrentPortfolioNav());
        assertTrue(result.getMaxSharpeRatio().doubleValue() >= 1.4, "Tỷ số Sharpe tối ưu của Markowitz phải >= 1.4");
        assertTrue(result.getExpectedAnnualReturnPercent().doubleValue() >= 20.0, "Tỷ suất sinh lời kỳ vọng phải >= 20% CAGR");
        assertEquals(6, result.getOptimalWeights().size(), "Phải phân bổ cho đủ 6 siêu cổ phiếu VN30");
        
        // Sum of weights must equal 100%
        double totalWeight = result.getOptimalWeights().values().stream()
            .mapToDouble(BigDecimal::doubleValue)
            .sum();
        assertEquals(100.0, totalWeight, 0.01, "Tổng tỷ trọng danh mục tối ưu phải bằng 100%");

        assertFalse(result.getEfficientFrontierCurve().isEmpty(), "Phải có đường cong biên hiệu quả");
        assertNotNull(result.getRebalancingStrategyRecommendation());
        assertNotNull(result.getQuantitativeVerdict());
    }
}
