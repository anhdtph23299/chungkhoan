package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.entity.BotDecisionAudit;
import com.vntrade.backend.repository.BotDecisionAuditRepository;
import com.vntrade.backend.service.marketdata.CandleDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BotDecisionAuditServiceTest {

    @Mock
    private BotDecisionAuditRepository auditRepository;

    @Mock
    private CandleDataService candleDataService;

    @InjectMocks
    private BotDecisionAuditService auditService;

    @Test
    @DisplayName("Ghi nhận quyết định từ chối vào DB với trạng thái PENDING")
    void testRecordDecision() {
        when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BotDecisionAudit audit = auditService.recordDecision(
            "PLX", "REJECTED_FILTER", BigDecimal.valueOf(37700),
            "OBI_ASK_WALL", "Tường bán đè gom > 45%", 85
        );

        assertNotNull(audit);
        assertEquals("PLX", audit.getSymbol());
        assertEquals("REJECTED_FILTER", audit.getDecision());
        assertEquals("OBI_ASK_WALL", audit.getRejectedByFilter());
        assertEquals("PENDING", audit.getAuditVerdict());
        verify(auditRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Đối soát sau 5 phiên: Giá giảm sâu (-10%) -> Xác nhận CORRECT_REJECTION (Cứu vốn thành công)")
    void testProcessSingleAudit_CorrectRejection() {
        LocalDate baseDate = LocalDate.of(2026, 10, 1);
        BotDecisionAudit audit = BotDecisionAudit.builder()
            .id(1L)
            .timestamp(LocalDateTime.of(2026, 10, 1, 10, 30))
            .symbol("PLX")
            .decision("REJECTED_FILTER")
            .priceAtDecision(BigDecimal.valueOf(100000))
            .rejectedByFilter("OBI_ASK_WALL")
            .auditVerdict("PENDING")
            .build();

        // Tạo 7 cây nến lịch sử: nến 0 là 100k, sau 5 phiên (nến 5) giá giảm về 90k (-10%)
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            candles.add(Candle.builder()
                .symbol("PLX")
                .date(baseDate.plusDays(i))
                .close(i == 5 ? BigDecimal.valueOf(90000) : BigDecimal.valueOf(98000))
                .build());
        }

        when(candleDataService.getHistoricalCandles("PLX", 180)).thenReturn(candles);
        when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        auditService.processSingleAudit(audit);

        assertEquals("CORRECT_REJECTION_SAVED_CAPITAL", audit.getAuditVerdict());
        assertEquals(BigDecimal.valueOf(90000), audit.getPriceAfter5Sessions());
        assertEquals(BigDecimal.valueOf(-10.0).setScale(2), audit.getReturn5SessionsPct());
        verify(auditRepository).save(audit);
    }

    @Test
    @DisplayName("Đối soát sau 5 phiên: Giá bứt phá mạnh (+12%) -> Nhận diện MISSED_OPPORTUNITY (False Negative)")
    void testProcessSingleAudit_MissedOpportunity() {
        LocalDate baseDate = LocalDate.of(2026, 10, 1);
        BotDecisionAudit audit = BotDecisionAudit.builder()
            .id(2L)
            .timestamp(LocalDateTime.of(2026, 10, 1, 10, 30))
            .symbol("FPT")
            .decision("REJECTED_FILTER")
            .priceAtDecision(BigDecimal.valueOf(100000))
            .rejectedByFilter("CANSLIM")
            .auditVerdict("PENDING")
            .build();

        // Sau 5 phiên giá tăng lên 112k (+12%)
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            candles.add(Candle.builder()
                .symbol("FPT")
                .date(baseDate.plusDays(i))
                .close(i == 5 ? BigDecimal.valueOf(112000) : BigDecimal.valueOf(102000))
                .build());
        }

        when(candleDataService.getHistoricalCandles("FPT", 180)).thenReturn(candles);
        when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        auditService.processSingleAudit(audit);

        assertEquals("MISSED_OPPORTUNITY_FALSE_NEGATIVE", audit.getAuditVerdict());
        assertEquals(BigDecimal.valueOf(112000), audit.getPriceAfter5Sessions());
        assertEquals(BigDecimal.valueOf(12.0).setScale(2), audit.getReturn5SessionsPct());
        verify(auditRepository).save(audit);
    }
}
