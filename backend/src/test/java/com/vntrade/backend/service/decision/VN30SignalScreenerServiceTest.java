package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.SignalScreenerDto;
import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.dto.StockScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.marketdata.StockPriceService;

class VN30SignalScreenerServiceTest {

    private StrategyService strategyService;
    private StockPriceService stockPriceService;
    private VN30SignalScreenerService screenerService;

    @BeforeEach
    void setUp() {
        strategyService = Mockito.mock(StrategyService.class);
        stockPriceService = Mockito.mock(StockPriceService.class);
        screenerService = new VN30SignalScreenerService(strategyService, stockPriceService);
    }

    @Test
    @DisplayName("Kiểm tra bộ lọc tín hiệu VN30: tính đúng Stop Loss 7%, Target 1 (+10%), Target 2 (+18%), sắp xếp theo điểm số")
    void testScreenVN30Signals() {
        StockScanResult fptScan = StockScanResult.builder()
            .symbol("FPT")
            .exchange("HOSE")
            .price(BigDecimal.valueOf(140000))
            .confidenceScore(85)
            .action("BUY")
            .signalTitle("Breakout đỉnh thời đại")
            .signalDescription("Khối lượng vượt trội MA20")
            .build();

        StockScanResult hpgScan = StockScanResult.builder()
            .symbol("HPG")
            .exchange("HOSE")
            .price(BigDecimal.valueOf(30000))
            .confidenceScore(75)
            .action("BUY")
            .signalTitle("Pullback MA50")
            .signalDescription("Thanh khoản cạn kiệt")
            .build();

        when(strategyService.scanAllStocks()).thenReturn(List.of(fptScan, hpgScan));
        when(stockPriceService.getQuote("FPT")).thenReturn(StockQuote.builder().symbol("FPT").price(BigDecimal.valueOf(140000)).build());
        when(stockPriceService.getQuote("HPG")).thenReturn(StockQuote.builder().symbol("HPG").price(BigDecimal.valueOf(30000)).build());

        List<SignalScreenerDto> signals = screenerService.screenVN30Signals();

        assertNotNull(signals);
        assertEquals(2, signals.size());

        SignalScreenerDto topSignal = signals.get(0);
        assertEquals("FPT", topSignal.getSymbol());
        assertEquals(85, topSignal.getCompositeScore());
        assertTrue(topSignal.getRecommendation().contains("MUA MẠNH"));

        // FPT Stop loss = 140000 * 0.93 = 130200
        assertEquals(0, topSignal.getStopLossPrice().compareTo(BigDecimal.valueOf(130200)));

        // FPT Target 1 = 140000 * 1.10 = 154000
        assertEquals(0, topSignal.getTarget1DailyProfit().compareTo(BigDecimal.valueOf(154000)));

        // FPT Target 2 = 140000 * 1.18 = 165200
        assertEquals(0, topSignal.getTarget2TrendRide().compareTo(BigDecimal.valueOf(165200)));

        assertTrue(topSignal.getRiskRewardRatio() > 1.4);
    }
}