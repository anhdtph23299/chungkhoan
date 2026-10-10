package com.vntrade.backend.service.decision;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.dto.VcpPatternDto;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.marketdata.CandleDataService;
import com.vntrade.backend.service.marketdata.StockPriceService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class VcpPatternDetectorServiceTest {

    @Mock
    private CandleDataService candleDataService;

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private VcpPatternDetectorService vcpDetectorService;

    @BeforeEach
    void setUp() {
        List<Candle> mockCandles = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            mockCandles.add(Candle.builder()
                .date(LocalDate.now().minusDays(i))
                .open(BigDecimal.valueOf(130000))
                .high(BigDecimal.valueOf(135000))
                .low(BigDecimal.valueOf(128000))
                .close(BigDecimal.valueOf(134000))
                .volume(2500000L)
                .build());
        }

        when(candleDataService.getHistoricalCandles(anyString(), anyInt())).thenReturn(mockCandles);

        StockQuote quote = StockQuote.builder()
            .symbol("FPT")
            .price(BigDecimal.valueOf(140000))
            .volume(3000000L)
            .changePercent(BigDecimal.valueOf(1.5))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);
    }

    @Test
    void testDetectVcpPattern() {
        VcpPatternDto vcp = vcpDetectorService.detectVcpPattern("FPT");

        assertNotNull(vcp);
        assertEquals("FPT", vcp.getSymbol());
        assertTrue(vcp.isVcpForming(), "Phải nhận diện được mẫu hình VCP đang hình thành");
        assertEquals(3, vcp.getNumberOfContractions(), "Mẫu hình chuẩn 3 lần thu hẹp (3T)");
        assertTrue(vcp.getRiskRewardRatio().doubleValue() >= 3.0, "Tỷ lệ R:R của VCP phải cao (≥ 3.0)");
        assertNotNull(vcp.getPivotPrice());
        assertNotNull(vcp.getSuggestedStopLoss());
        assertNotNull(vcp.getAnalysisNote());
    }

    @Test
    void testScanVcpAcrossWatchlist() {
        List<VcpPatternDto> list = vcpDetectorService.scanVcpAcrossWatchlist();

        assertNotNull(list);
        assertFalse(list.isEmpty());
    }
}