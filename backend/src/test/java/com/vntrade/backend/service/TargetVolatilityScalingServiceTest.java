package com.vntrade.backend.service;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.TargetVolatilityScalingDto;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TargetVolatilityScalingServiceTest {

    @Mock
    private CandleDataService candleDataService;

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private TargetVolatilityScalingService targetVolService;

    @BeforeEach
    void setUp() {
        List<Candle> mockCandles = new ArrayList<>();
        BigDecimal base = BigDecimal.valueOf(100000);
        for (int i = 0; i < 35; i++) {
            BigDecimal close = base.add(BigDecimal.valueOf(Math.sin(i) * 2000));
            mockCandles.add(Candle.builder()
                .date(LocalDate.of(2026, 9, (i % 28) + 1))
                .open(close.subtract(BigDecimal.valueOf(500)))
                .high(close.add(BigDecimal.valueOf(1000)))
                .low(close.subtract(BigDecimal.valueOf(1000)))
                .close(close)
                .volume(1500000L)
                .build());
        }
        when(candleDataService.getHistoricalCandles(anyString(), anyInt())).thenReturn(mockCandles);

        // Giả lập 2 vị thế: 1 vị thế T+1 (khóa) và 1 vị thế T+3 (khả dụng)
        List<Trade> trades = List.of(
            Trade.builder()
                .symbol("FPT")
                .price(BigDecimal.valueOf(130000))
                .quantity(500)
                .tradeDate(LocalDate.now().minusDays(1)) // T+1 -> Khóa
                .status("open")
                .build(),
            Trade.builder()
                .symbol("HPG")
                .price(BigDecimal.valueOf(28000))
                .quantity(2000)
                .tradeDate(LocalDate.now().minusDays(4)) // T+4 -> Khả dụng
                .status("open")
                .build()
        );
        when(tradeRepository.findByStatusOrderByTradeDateDesc("open")).thenReturn(trades);
    }

    @Test
    void testCalculateTargetVolatilityScaling() {
        BigDecimal nav = BigDecimal.valueOf(250_000_000);
        TargetVolatilityScalingDto result = targetVolService.calculateTargetVolatilityScaling(nav, 15.0);

        assertNotNull(result);
        assertEquals(nav, result.getPortfolioNav());
        assertEquals(0, result.getTargetAnnualizedVolatilityPercent().compareTo(BigDecimal.valueOf(15.0)));
        assertTrue(result.getCurrentRealizedVolatilityPercent().doubleValue() > 0);
        assertTrue(result.getOptimalEquityWeightPercent().doubleValue() <= 100.0);
        assertTrue(result.getOptimalCashWeightPercent().doubleValue() >= 0.0);
        assertTrue(result.getLockedT25EquityAmount().compareTo(BigDecimal.ZERO) > 0, "Phải nhận diện được vị thế FPT bị khóa T+1");
        assertTrue(result.getLiquidEquityAmount().compareTo(BigDecimal.ZERO) > 0, "Phải nhận diện được vị thế HPG khả dụng");
        assertNotNull(result.getRebalanceAction());
        assertNotNull(result.getInstitutionalVolTargetVerdict());
        assertTrue(result.getInstitutionalVolTargetVerdict().contains("QUẢN TRỊ BIẾN ĐỘNG MỤC TIÊU"));
    }
}
