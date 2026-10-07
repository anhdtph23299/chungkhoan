package com.vntrade.backend.service;

import com.vntrade.backend.dto.BlackSwanStressScenarioDto;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BlackSwanStressScenarioServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private BlackSwanStressScenarioService blackSwanService;

    @Test
    void testSimulateBlackSwanScenarios_DefaultCapital() {
        when(tradeRepository.findByStatusOrderByTradeDateDesc("open")).thenReturn(List.of());

        BlackSwanStressScenarioDto result = blackSwanService.simulateBlackSwanScenarios(BigDecimal.valueOf(300_000_000));

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(300_000_000), result.getPortfolioCapital());
        assertTrue(result.getOverallResilienceScore() >= 0 && result.getOverallResilienceScore() <= 100);
        assertNotNull(result.getWorstCaseDrawdownPercent());
        assertTrue(result.getWorstCaseDrawdownPercent().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(result.getWorstCaseLossAmount());
        assertNotNull(result.getRecommendedCashBufferPercent());
        assertTrue(result.getRecommendedVn30FuturesHedgeContracts() >= 1);
        assertNotNull(result.getInstitutionalStressVerdict());

        // Verify all 4 Black Swan scenarios are generated
        assertNotNull(result.getScenarioResults());
        assertEquals(4, result.getScenarioResults().size());

        // Verify structural T+2.5 lockout scenario
        assertTrue(result.getScenarioResults().stream()
                .anyMatch(s -> "T25_LIMIT_DOWN_LOCKOUT".equals(s.getScenarioId())));
    }

    @Test
    void testSimulateBlackSwanScenarios_WithOpenPositions() {
        Trade tradeHpg = Trade.builder()
                .symbol("HPG")
                .price(BigDecimal.valueOf(30000))
                .quantity(5000)
                .status("open")
                .build();
        Trade tradeSsi = Trade.builder()
                .symbol("SSI")
                .price(BigDecimal.valueOf(35000))
                .quantity(4000)
                .status("open")
                .build();

        when(tradeRepository.findByStatusOrderByTradeDateDesc("open")).thenReturn(List.of(tradeHpg, tradeSsi));

        BlackSwanStressScenarioDto result = blackSwanService.simulateBlackSwanScenarios(BigDecimal.valueOf(500_000_000));

        assertNotNull(result);
        assertEquals(4, result.getScenarioResults().size());
        assertTrue(result.getWorstCaseLossAmount().compareTo(BigDecimal.ZERO) > 0);
    }
}
