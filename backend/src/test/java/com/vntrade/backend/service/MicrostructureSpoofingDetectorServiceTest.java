package com.vntrade.backend.service;

import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.OrderBookDto.OrderBookLevel;
import com.vntrade.backend.dto.SpoofingDetectorDto;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MicrostructureSpoofingDetectorServiceTest {

    @Mock
    private OrderBookService orderBookService;

    @InjectMocks
    private MicrostructureSpoofingDetectorService spoofingDetectorService;

    @Test
    void testDetectSpoofing_OrganicDepth() {
        OrderBookDto organicBook = OrderBookDto.builder()
                .symbol("FPT")
                .currentPrice(BigDecimal.valueOf(140000))
                .bidLevels(List.of(
                        new OrderBookLevel(1, BigDecimal.valueOf(139900), 50000L),
                        new OrderBookLevel(2, BigDecimal.valueOf(139800), 30000L),
                        new OrderBookLevel(3, BigDecimal.valueOf(139700), 20000L)
                ))
                .askLevels(List.of(
                        new OrderBookLevel(1, BigDecimal.valueOf(140100), 45000L),
                        new OrderBookLevel(2, BigDecimal.valueOf(140200), 35000L),
                        new OrderBookLevel(3, BigDecimal.valueOf(140300), 20000L)
                ))
                .build();

        when(orderBookService.getOrderBook(eq("FPT"))).thenReturn(organicBook);

        SpoofingDetectorDto result = spoofingDetectorService.detectSpoofing("FPT");

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertEquals("GENUINE_ORGANIC_DEPTH", result.getLayeringPattern());
        assertTrue(result.isSafeToBuy(), "Organic depth should be safe to buy");
        assertTrue(result.getSpoofingRiskScore() < 40);
        assertNotNull(result.getInstitutionalActionVerdict());
    }

    @Test
    void testDetectSpoofing_PhantomBidWall_FlagsBullTrap() {
        // Kê mua ảo tầng 3: Tầng 1 chỉ 10.000 CP (10%), tầng 3 kê 60.000 CP (60%)
        OrderBookDto fakeBidBook = OrderBookDto.builder()
                .symbol("HPG")
                .currentPrice(BigDecimal.valueOf(30000))
                .bidLevels(List.of(
                        new OrderBookLevel(1, BigDecimal.valueOf(29950), 10000L),
                        new OrderBookLevel(2, BigDecimal.valueOf(29900), 30000L),
                        new OrderBookLevel(3, BigDecimal.valueOf(29850), 60000L)
                ))
                .askLevels(List.of(
                        new OrderBookLevel(1, BigDecimal.valueOf(30050), 20000L),
                        new OrderBookLevel(2, BigDecimal.valueOf(30100), 20000L),
                        new OrderBookLevel(3, BigDecimal.valueOf(30150), 20000L)
                ))
                .build();

        when(orderBookService.getOrderBook(eq("HPG"))).thenReturn(fakeBidBook);

        SpoofingDetectorDto result = spoofingDetectorService.detectSpoofing("HPG");

        assertNotNull(result);
        assertEquals("PHANTOM_BID_SUPPORT_LAYER", result.getLayeringPattern());
        assertFalse(result.isSafeToBuy(), "Bot must refuse market buy into a phantom bid wall bull trap");
        assertTrue(result.getSpoofingRiskScore() >= 60, "Risk score must be high for spoofing");
        assertEquals(60000L, result.getSuspectedPhantomVolume());
        assertTrue(result.getCancelHazardProbability().compareTo(BigDecimal.valueOf(70.0)) >= 0);
    }

    @Test
    void testDetectSpoofing_PhantomAskWall_FlagsBearTrap() {
        // Đè bán ảo tầng 3 để ép gom hàng: Tầng 1 10.000 CP, Tầng 3 đè 70.000 CP
        OrderBookDto fakeAskBook = OrderBookDto.builder()
                .symbol("SSI")
                .currentPrice(BigDecimal.valueOf(35000))
                .bidLevels(List.of(
                        new OrderBookLevel(1, BigDecimal.valueOf(34950), 20000L),
                        new OrderBookLevel(2, BigDecimal.valueOf(34900), 20000L),
                        new OrderBookLevel(3, BigDecimal.valueOf(34850), 20000L)
                ))
                .askLevels(List.of(
                        new OrderBookLevel(1, BigDecimal.valueOf(35050), 10000L),
                        new OrderBookLevel(2, BigDecimal.valueOf(35100), 20000L),
                        new OrderBookLevel(3, BigDecimal.valueOf(35150), 70000L)
                ))
                .build();

        when(orderBookService.getOrderBook(eq("SSI"))).thenReturn(fakeAskBook);

        SpoofingDetectorDto result = spoofingDetectorService.detectSpoofing("SSI");

        assertNotNull(result);
        assertEquals("PHANTOM_ASK_RESISTANCE_LAYER", result.getLayeringPattern());
        assertTrue(result.isSafeToBuy(), "Bear trap intimidation allows passive limit buying");
        assertTrue(result.getSpoofingRiskScore() >= 50);
        assertEquals(70000L, result.getSuspectedPhantomVolume());
    }
}
