package com.vntrade.backend.service;

import com.vntrade.backend.dto.VeteranDisciplineAuditDto;
import com.vntrade.backend.entity.Trade;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class VietnamVeteranRulesServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private RiskService riskService;

    @InjectMocks
    private VietnamVeteranRulesService veteranRulesService;

    @BeforeEach
    void setUp() {
        Trade t = Trade.builder()
            .id(1L)
            .symbol("FPT")
            .exchange("HOSE")
            .status("closed")
            .price(BigDecimal.valueOf(130000))
            .closePrice(BigDecimal.valueOf(145000))
            .quantity(500)
            .pnl(BigDecimal.valueOf(7500000))
            .pnlPercent(BigDecimal.valueOf(11.54))
            .tradeDate(LocalDate.now().minusDays(5))
            .closeDate(LocalDate.now())
            .strategy("Breakout Pivot")
            .reason("Tuân thủ điểm Pivot VCP")
            .build();

        when(tradeRepository.findAllByOrderByTradeDateDesc()).thenReturn(List.of(t));
        when(tradeRepository.findByStatusOrderByTradeDateDesc("open")).thenReturn(List.of());
        when(tradeRepository.findByStatusOrderByTradeDateDesc("closed")).thenReturn(List.of(t));
    }

    @Test
    void testAuditVeteranDiscipline() {
        VeteranDisciplineAuditDto audit = veteranRulesService.auditVeteranDiscipline();

        assertNotNull(audit);
        assertEquals(100, audit.getDisciplineScore(), "Điểm kỷ luật phải đạt 100%");
        assertEquals(7, audit.getRuleChecks().size(), "Phải kiểm tra đủ 7 nguyên tắc vàng");
        assertTrue(audit.getRuleChecks().stream().allMatch(VeteranDisciplineAuditDto.DisciplineRuleCheck::isCompliant));
        assertNotNull(audit.getOverallVerdict());
    }
}
