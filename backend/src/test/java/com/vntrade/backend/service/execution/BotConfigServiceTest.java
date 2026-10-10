package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.BotConfigDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class BotConfigServiceTest {

    private AutoTradingBotService botService;
    private BotConfigService configService;

    @BeforeEach
    void setUp() {
        botService = Mockito.mock(AutoTradingBotService.class);
        when(botService.isRunning()).thenReturn(true);
        when(botService.getMode()).thenReturn("LIVE_PAPER_MONEY");
        when(botService.getAccountCapital()).thenReturn(BigDecimal.valueOf(20_000_000));
        when(botService.getDailyProfitTarget()).thenReturn(BigDecimal.valueOf(300_000));
        when(botService.getDailyMaxLossLimit()).thenReturn(BigDecimal.valueOf(400_000));
        configService = new BotConfigService(botService);
    }

    @Test
    @DisplayName("Kiểm tra lấy và cập nhật cấu hình tham số bot tự động")
    void testGetAndUpdateConfig() {
        BotConfigDto config = configService.getConfig();
        assertNotNull(config);
        assertTrue(config.isRunning());
        assertEquals("LIVE_PAPER_MONEY", config.getMode());
        assertEquals(0, config.getStopLossPercent().compareTo(BigDecimal.valueOf(7.0)));

        // Cập nhật target lên 5.000.000 đ và risk 2.0%
        BotConfigDto updateReq = BotConfigDto.builder()
            .dailyProfitTarget(BigDecimal.valueOf(5_000_000))
            .maxRiskPerTradePercent(BigDecimal.valueOf(2.0))
            .stopLossPercent(BigDecimal.valueOf(6.5))
            .sectorCapPercent(BigDecimal.valueOf(30.0))
            .maxConcurrentPositions(3)
            .build();

        BotConfigDto updated = configService.updateConfig(updateReq);

        assertEquals(0, updated.getDailyProfitTarget().compareTo(BigDecimal.valueOf(5_000_000)));
        assertEquals(0, updated.getMaxRiskPerTradePercent().compareTo(BigDecimal.valueOf(2.0)));
        assertEquals(0, updated.getStopLossPercent().compareTo(BigDecimal.valueOf(6.5)));
        assertEquals(0, updated.getSectorCapPercent().compareTo(BigDecimal.valueOf(30.0)));
        assertEquals(3, updated.getMaxConcurrentPositions());
    }
}
