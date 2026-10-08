package com.vntrade.backend.service;

import com.vntrade.backend.dto.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OversoldBounceDetectorServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @Mock
    private CandleDataService candleDataService;

    @Mock
    private TechnicalIndicatorService indicatorService;

    @Mock
    private RiskService riskService;

    @InjectMocks
    private OversoldBounceDetectorService bounceService;

    @BeforeEach
    void setUp() {
        StockQuote quote = StockQuote.builder()
            .symbol("SSI")
            .price(BigDecimal.valueOf(28000))
            .changePercent(BigDecimal.valueOf(-4.5))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);
        when(riskService.getSectorForSymbol(anyString())).thenReturn("CHỨNG KHOÁN");

        List<Candle> candles = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(50);
        for (int i = 0; i < 50; i++) {
            candles.add(Candle.builder()
                .date(baseDate.plusDays(i))
                .open(BigDecimal.valueOf(30000 - i * 40))
                .high(BigDecimal.valueOf(30200 - i * 40))
                .low(BigDecimal.valueOf(27500 - i * 40))
                .close(BigDecimal.valueOf(28000 - i * 40))
                .volume(2_000_000L)
                .build());
        }
        when(candleDataService.getHistoricalCandles(anyString(), anyInt())).thenReturn(candles);

        List<BigDecimal> rsiList = new ArrayList<>();
        for (int i = 0; i < 50; i++) rsiList.add(BigDecimal.valueOf(28.5)); // RSI quá bán sâu
        when(indicatorService.calculateRSI(anyList(), anyInt())).thenReturn(rsiList);

        List<BollingerBandsPoint> bbList = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            bbList.add(BollingerBandsPoint.builder()
                .upper(BigDecimal.valueOf(32000))
                .middle(BigDecimal.valueOf(30000))
                .lower(BigDecimal.valueOf(29000))
                .bandwidth(BigDecimal.valueOf(10.0))
                .build());
        }
        when(indicatorService.calculateBollingerBands(anyList(), anyInt(), anyDouble())).thenReturn(bbList);

        List<BigDecimal> smaList = new ArrayList<>();
        for (int i = 0; i < 50; i++) smaList.add(BigDecimal.valueOf(29000));
        when(indicatorService.calculateSMA(anyList(), anyInt())).thenReturn(smaList);
    }

    @Test
    void testScanOversoldBounceCandidates() {
        OversoldBounceDto dto = bounceService.scanOversoldBounceCandidates();

        assertNotNull(dto);
        assertNotNull(dto.getScanTime());
        assertTrue(dto.getTotalSymbolsScanned() > 0);
        assertNotNull(dto.getMarketPanicStatus());
        assertNotNull(dto.getMacroVerdict());
        assertFalse(dto.getCandidates().isEmpty(), "Phải phát hiện ứng viên quá bán");

        OversoldBounceDto.OversoldCandidate first = dto.getCandidates().get(0);
        assertTrue(first.getRsi14().doubleValue() <= 36.0, "RSI phải nằm trong vùng quá bán");
        assertNotNull(first.getTacticalStopLoss());
        assertNotNull(first.getTacticalTargetPrice());
        assertTrue(first.getRiskRewardRatio() >= 1.5, "Tỷ lệ R:R phải thỏa mãn");
    }
}
