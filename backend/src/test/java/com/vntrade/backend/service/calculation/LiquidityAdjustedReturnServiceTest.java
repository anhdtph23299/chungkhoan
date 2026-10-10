package com.vntrade.backend.service.calculation;

import com.vntrade.backend.dto.LiquidityAdjustedReturnDto;
import com.vntrade.backend.dto.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.marketdata.StockPriceService;

/**
 * Unit test cho LiquidityAdjustedReturnService.
 *
 * Test coverage:
 * - VN30 Blue Chip (Tier 1): slippage thấp, liquidity tốt
 * - Mid-cap (Tier 2): slippage trung bình
 * - Net return < gross return (chi phí luôn dương)
 * - Break-even threshold hợp lệ
 * - Illiquid stock: isLiquidEnough = false
 * - T+2.5 opportunity cost được tính
 * - Execution strategy theo participation rate
 * - Large order: multiple sessions recommended
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LiquidityAdjustedReturnService — Market Impact Tests")
class LiquidityAdjustedReturnServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private LiquidityAdjustedReturnService liquidityService;

    @BeforeEach
    void setUp() {
        StockQuote defaultQuote = StockQuote.builder()
            .symbol("FPT").price(BigDecimal.valueOf(95_000))
            .changePercent(BigDecimal.valueOf(1.0))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(defaultQuote);
    }

    @Test
    @DisplayName("VN30 Blue Chip (FPT): Tier 1, bid-ask spread = 0.05%")
    void testTier1BlueChipLiquidity() {
        LiquidityAdjustedReturnDto result = liquidityService.calculateLiquidityAdjustedReturn(
            "FPT", 1000, BigDecimal.valueOf(95_000), BigDecimal.valueOf(104_500),
            BigDecimal.valueOf(200_000_000)
        );

        assertThat(result.getLiquidityTier()).isEqualTo("TIER_1_BLUE_CHIP");
        assertThat(result.getBidAskSpreadPct().doubleValue()).isEqualTo(0.05);
        // Lombok @Data: boolean isLiquidEnough → getter isLiquidEnough()
        assertThat(result.isLiquidEnough()).isTrue();
        assertThat(result.getParticipationRate().doubleValue()).isLessThan(1.0); // 1000/5M = 0.02%

    }

    @Test
    @DisplayName("Net return phải luôn < gross return (chi phí thanh khoản > 0)")
    void testNetReturnAlwaysLessThanGross() {
        LiquidityAdjustedReturnDto result = liquidityService.calculateLiquidityAdjustedReturn(
            "FPT", 500, BigDecimal.valueOf(90_000), BigDecimal.valueOf(100_000),
            BigDecimal.valueOf(500_000_000)
        );

        assertThat(result.getGrossReturnPct()).isNotNull();
        assertThat(result.getNetReturnPct()).isNotNull();
        // Chi phí thanh khoản luôn dương → net < gross
        assertThat(result.getNetReturnPct().compareTo(result.getGrossReturnPct())).isLessThan(0);
        assertThat(result.getLiquidityPenaltyPct().doubleValue()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Break-even threshold: gross return phải >= break-even để có lợi nhuận")
    void testBreakEvenThreshold() {
        LiquidityAdjustedReturnDto result = liquidityService.calculateLiquidityAdjustedReturn(
            "VCB", 2000, BigDecimal.valueOf(85_000), BigDecimal.valueOf(93_500),
            BigDecimal.valueOf(500_000_000)
        );

        assertThat(result.getBreakEvenReturnPct()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        // Break-even = tổng chi phí liquidity penalty
        assertThat(result.getBreakEvenReturnPct().compareTo(result.getLiquidityPenaltyPct())).isEqualTo(0);
        // Net return = gross - break-even
        double expectedNet = result.getGrossReturnPct().doubleValue() - result.getBreakEvenReturnPct().doubleValue();
        assertThat(result.getNetReturnPct().doubleValue()).isCloseTo(expectedNet, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    @DisplayName("T+2.5 opportunity cost được tính (> 0)")
    void testT25OpportunityCostPositive() {
        LiquidityAdjustedReturnDto result = liquidityService.calculateLiquidityAdjustedReturn(
            "HPG", 1000, BigDecimal.valueOf(28_000), BigDecimal.valueOf(31_000),
            BigDecimal.valueOf(200_000_000)
        );

        assertThat(result.getT25LockupDays().doubleValue()).isEqualTo(2.5);
        assertThat(result.getOpportunityCostPct()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getOpportunityCostVnd()).isNotNull().isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Small participation rate: execution = MARKET_ORDER")
    void testSmallParticipationMarketOrder() {
        // 100 shares / 5M ADTV = 0.002% — rất nhỏ
        LiquidityAdjustedReturnDto result = liquidityService.calculateLiquidityAdjustedReturn(
            "FPT", 100, BigDecimal.valueOf(95_000), BigDecimal.valueOf(104_500),
            BigDecimal.valueOf(200_000_000)
        );

        assertThat(result.getExecutionStrategy()).containsIgnoringCase("MARKET_ORDER");
        assertThat(result.getEstimatedSessionsToFill()).isEqualTo(1);
    }

    @Test
    @DisplayName("Large order (> 20% ADTV): multi-session execution recommended")
    void testLargeOrderMultiSession() {
        // 2M shares với ADTV ~1M (Tier 2 mid-cap) = 200% ADTV → cần nhiều phiên
        LiquidityAdjustedReturnDto result = liquidityService.calculateLiquidityAdjustedReturn(
            "ABC", 2_000_000, BigDecimal.valueOf(10_000), BigDecimal.valueOf(11_000),
            BigDecimal.valueOf(20_000_000_000L)
        );

        assertThat(result.getEstimatedSessionsToFill()).isGreaterThan(1);
        assertThat(result.getExecutionStrategy()).containsIgnoringCase("TWAP");
    }

    @Test
    @DisplayName("Slippage tăng theo √(Q/ADTV) — larger position, higher impact")
    void testSlippageScalesWithPosition() {
        LiquidityAdjustedReturnDto small = liquidityService.calculateLiquidityAdjustedReturn(
            "FPT", 1_000, BigDecimal.valueOf(95_000), BigDecimal.valueOf(104_500),
            BigDecimal.valueOf(200_000_000)
        );
        LiquidityAdjustedReturnDto large = liquidityService.calculateLiquidityAdjustedReturn(
            "FPT", 100_000, BigDecimal.valueOf(95_000), BigDecimal.valueOf(104_500),
            BigDecimal.valueOf(200_000_000)
        );

        // Slippage lớn hơn cho position lớn hơn
        assertThat(large.getTotalSlippagePct().compareTo(small.getTotalSlippagePct())).isGreaterThan(0);
        assertThat(large.getParticipationRate().compareTo(small.getParticipationRate())).isGreaterThan(0);
    }

    @Test
    @DisplayName("ADTV theo VND = ADTV_shares × entry_price")
    void testAdtvVndCalculation() {
        BigDecimal entryPrice = BigDecimal.valueOf(95_000);
        LiquidityAdjustedReturnDto result = liquidityService.calculateLiquidityAdjustedReturn(
            "FPT", 1000, entryPrice, BigDecimal.valueOf(104_500),
            BigDecimal.valueOf(200_000_000)
        );

        // ADTV VND = ADTV_shares × price
        double expectedAdtvVnd = result.getAdtvShares().doubleValue() * entryPrice.doubleValue();
        assertThat(result.getAdtvVnd().doubleValue()).isCloseTo(expectedAdtvVnd, org.assertj.core.api.Assertions.within(1.0));
    }

    @Test
    @DisplayName("Symbol normalization và default capital")
    void testNormalization() {
        LiquidityAdjustedReturnDto result = liquidityService.calculateLiquidityAdjustedReturn(
            "fpt", 500, null, null, null
        );

        assertThat(result.getSymbol()).isEqualTo("FPT");
        assertThat(result.getAccountCapital()).isEqualTo(BigDecimal.valueOf(200_000_000));
        // Entry price lấy từ quote mock
        assertThat(result.getEntryPrice().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    @Test
    @DisplayName("Total transaction cost = brokerage + SSC (2 chiều) + spread")
    void testTransactionCostComponents() {
        LiquidityAdjustedReturnDto result = liquidityService.calculateLiquidityAdjustedReturn(
            "FPT", 1000, BigDecimal.valueOf(100_000), BigDecimal.valueOf(110_000),
            BigDecimal.valueOf(300_000_000)
        );

        assertThat(result.getBrokarageFee()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getSecuFee()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getTotalTransactionCost()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        // Total ≥ brokerage + SSC fee
        assertThat(result.getTotalTransactionCost().compareTo(result.getSecuFee())).isGreaterThan(0);
    }

    @Test
    @DisplayName("Liquidity verdict và risk factors không trống")
    void testVerdictAndRiskFactorsNotEmpty() {
        LiquidityAdjustedReturnDto result = liquidityService.calculateLiquidityAdjustedReturn(
            "VCB", 1000, BigDecimal.valueOf(85_000), BigDecimal.valueOf(93_500),
            BigDecimal.valueOf(200_000_000)
        );

        assertThat(result.getLiquidityVerdict()).isNotNull().isNotEmpty();
        assertThat(result.getRiskFactors()).isNotNull().isNotEmpty();
    }
}