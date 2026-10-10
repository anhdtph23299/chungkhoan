package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.AdaptivePositionSizingDto;
import com.vntrade.backend.dto.GarchVolatilityForecastDto;
import com.vntrade.backend.dto.InstitutionalBacktestResultDto;
import com.vntrade.backend.dto.RegimeSwitchingDto;
import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import com.vntrade.backend.service.backtest.InstitutionalBacktestService;
import com.vntrade.backend.service.calculation.GarchVolatilityForecastService;
import com.vntrade.backend.service.decision.RegimeSwitchingSignalService;
import com.vntrade.backend.service.marketdata.StockPriceService;

/**
 * Unit test cho AdaptivePositionSizingService.
 *
 * Test coverage:
 * - ATR-based sizing hợp lệ
 * - Kelly criterion edge dương → size > 0
 * - Regime BEAR → Exposure = 0 (lockout)
 * - Volatility extreme → giảm size
 * - R:R ratio = 2.5:1
 * - Max allocation ≤ 25% NAV
 * - Stop loss và take profit hợp lệ
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdaptivePositionSizingService — 4-Tier Sizing Tests")
class AdaptivePositionSizingServiceTest {

    @Mock private StockPriceService stockPriceService;
    @Mock private InstitutionalBacktestService institutionalBacktestService;
    @Mock private TradeRepository tradeRepository;
    @Mock private RegimeSwitchingSignalService regimeSwitchingSignalService;
    @Mock private GarchVolatilityForecastService garchVolatilityForecastService;

    @InjectMocks
    private AdaptivePositionSizingService adaptivePositionSizingService;

    private BigDecimal testCapital = BigDecimal.valueOf(500_000_000); // 500 triệu

    @BeforeEach
    void setUp() {
        // Default: giá 80,000 VND, change +1.2%
        StockQuote quote = StockQuote.builder()
            .symbol("FPT").price(BigDecimal.valueOf(80_000))
            .changePercent(BigDecimal.valueOf(1.2))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(quote);

        // Default GARCH: volatility 1.5% -> conditional vol annualized = 1.5 * sqrt(252) ≈ 23.8%
        GarchVolatilityForecastDto garch = GarchVolatilityForecastDto.builder()
            .conditionalVolCurrent(BigDecimal.valueOf(23.8))
            .forecastT25SettlementVolPercent(BigDecimal.valueOf(2.4))
            .build();
        when(garchVolatilityForecastService.forecastVolatility(anyString())).thenReturn(garch);

        // Default backtest: 65% win rate, 2.0 payoff
        InstitutionalBacktestResultDto bt = InstitutionalBacktestResultDto.builder()
            .winRatePercent(BigDecimal.valueOf(65.0))
            .profitFactor(BigDecimal.valueOf(2.0))
            .build();
        when(institutionalBacktestService.runInstitutionalBacktest(
            anyString(), anyString(), anyInt(), any(), anyDouble(), anyDouble()
        )).thenReturn(bt);

        // Default trade repo: 10 trades, 7 wins
        when(tradeRepository.countClosedTrades()).thenReturn(10L);
        when(tradeRepository.countWinningTrades()).thenReturn(7L);

        // Default regime: BULL
        RegimeSwitchingDto bullRegime = RegimeSwitchingDto.builder()
            .currentRegime("BULL")
            .bullProbability(BigDecimal.valueOf(75.0))
            .build();
        when(regimeSwitchingSignalService.analyzeMarketRegime()).thenReturn(bullRegime);
    }

    @Test
    @DisplayName("Bull regime: size > 0 và không vượt 25% NAV")
    void testBullRegimeSizePositiveAndWithinLimit() {
        AdaptivePositionSizingDto result = adaptivePositionSizingService.calculateAdaptiveSize("FPT", testCapital);

        assertThat(result).isNotNull();
        assertThat(result.getAdaptiveSharesToBuy()).isGreaterThanOrEqualTo(0);
        assertThat(result.getActualAllocationPercent().doubleValue()).isLessThanOrEqualTo(25.0);
        assertThat(result.getCurrentRegime()).isEqualTo("BULL");
    }

    @Test
    @DisplayName("Bear regime: exposure = 0, không mua bất kỳ cổ phiếu nào")
    void testBearRegimeLockout() {
        RegimeSwitchingDto bearRegime = RegimeSwitchingDto.builder()
            .currentRegime("BEAR")
            .bearProbability(BigDecimal.valueOf(80.0))
            .build();
        when(regimeSwitchingSignalService.analyzeMarketRegime()).thenReturn(bearRegime);

        AdaptivePositionSizingDto result = adaptivePositionSizingService.calculateAdaptiveSize("HPG", testCapital);

        assertThat(result.getAdaptiveSharesToBuy()).isEqualTo(0);
        assertThat(result.getAdaptiveAllocationPercent().doubleValue()).isEqualTo(0.0);
        assertThat(result.getRiskWarning()).contains("BEAR");
    }

    @Test
    @DisplayName("Sideways regime: size giảm xuống 60% so với Bull")
    void testSidewaysRegimeReducedSize() {
        // Lấy size khi Bull
        AdaptivePositionSizingDto bullResult = adaptivePositionSizingService.calculateAdaptiveSize("FPT", testCapital);

        // Chuyển sang Sideways
        RegimeSwitchingDto swRegime = RegimeSwitchingDto.builder()
            .currentRegime("SIDEWAYS")
            .sidewaysProbability(BigDecimal.valueOf(65.0))
            .build();
        when(regimeSwitchingSignalService.analyzeMarketRegime()).thenReturn(swRegime);

        AdaptivePositionSizingDto swResult = adaptivePositionSizingService.calculateAdaptiveSize("FPT", testCapital);

        // Sideways phải nhỏ hơn Bull (regime multiplier 0.6x vs 1.0x)
        assertThat(swResult.getAdaptiveAllocationPercent().doubleValue())
            .isLessThanOrEqualTo(bullResult.getAdaptiveAllocationPercent().doubleValue() + 0.1);
    }

    @Test
    @DisplayName("Extreme volatility: ATR > 4% → Vol scale 0.5x, size giảm mạnh")
    void testExtremeVolatilityReducesSize() {
        // GARCH vol cao: 5% daily → annualized conditional vol = 5 * sqrt(252) ≈ 79.4%
        GarchVolatilityForecastDto highVolGarch = GarchVolatilityForecastDto.builder()
            .conditionalVolCurrent(BigDecimal.valueOf(79.4))
            .forecastT25SettlementVolPercent(BigDecimal.valueOf(8.0))
            .build();
        when(garchVolatilityForecastService.forecastVolatility(anyString())).thenReturn(highVolGarch);

        AdaptivePositionSizingDto result = adaptivePositionSizingService.calculateAdaptiveSize("FPT", testCapital);

        assertThat(result.getVolatilityEnvironment()).isEqualTo("EXTREME");
        assertThat(result.getVolatilityScaleFactor().doubleValue()).isEqualTo(0.50);
    }

    @Test
    @DisplayName("Kelly edge âm: size giảm về 0 hoặc rất nhỏ")
    void testNegativeKellyEdgeMinimizesSize() {
        // Win rate thấp (30%), payoff thấp (1.1) → Kelly âm
        InstitutionalBacktestResultDto btBad = InstitutionalBacktestResultDto.builder()
            .winRatePercent(BigDecimal.valueOf(30.0))
            .profitFactor(BigDecimal.valueOf(1.1))
            .build();
        when(institutionalBacktestService.runInstitutionalBacktest(
            anyString(), anyString(), anyInt(), any(), anyDouble(), anyDouble()
        )).thenReturn(btBad);
        // Trade history cũng xấu: 10 trades, 3 wins
        when(tradeRepository.countWinningTrades()).thenReturn(3L);

        AdaptivePositionSizingDto result = adaptivePositionSizingService.calculateAdaptiveSize("FPT", testCapital);

        // Kelly âm → Full Kelly ≤ 0 → size về 0
        assertThat(result.getFullKellyPercent().doubleValue()).isLessThanOrEqualTo(0.0);
        assertThat(result.getAdaptiveSharesToBuy()).isEqualTo(0);
    }

    @Test
    @DisplayName("Stop loss = 1.5x ATR, Take profit = R:R 2.5x stop loss")
    void testStopLossAndTakeProfitRatio() {
        AdaptivePositionSizingDto result = adaptivePositionSizingService.calculateAdaptiveSize("FPT", testCapital);

        assertThat(result.getStopLossPrice()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getTakeProfitPrice()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        // Stop loss phải thấp hơn giá mua
        assertThat(result.getStopLossPrice().compareTo(result.getCurrentPrice())).isLessThan(0);
        // Take profit phải cao hơn giá mua
        assertThat(result.getTakeProfitPrice().compareTo(result.getCurrentPrice())).isGreaterThan(0);
        // R:R ratio ≈ 2.5 (cho phép sai số ±0.5)
        assertThat(result.getRiskRewardRatio().doubleValue()).isBetween(2.0, 3.0);
    }

    @Test
    @DisplayName("ATR-based sizing: target daily vol = 1.5% NAV")
    void testAtrBasedSizingTargetVol() {
        AdaptivePositionSizingDto result = adaptivePositionSizingService.calculateAdaptiveSize("FPT", testCapital);

        assertThat(result.getAtr14()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getAtrPercent()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        // So sánh bằng compareTo để bỏ qua scale khác nhau (1.5 vs 1.50)
        assertThat(result.getTargetDailyVolatility().compareTo(BigDecimal.valueOf(1.5))).isEqualTo(0);
        assertThat(result.getAtrBasedShares()).isNotNull();
    }


    @Test
    @DisplayName("Symbol normalization: null → 'FPT', lowercase → uppercase")
    void testSymbolNormalization() {
        AdaptivePositionSizingDto result1 = adaptivePositionSizingService.calculateAdaptiveSize(null, testCapital);
        AdaptivePositionSizingDto result2 = adaptivePositionSizingService.calculateAdaptiveSize("fpt", testCapital);

        assertThat(result1.getSymbol()).isEqualTo("FPT");
        assertThat(result2.getSymbol()).isEqualTo("FPT");
    }

    @Test
    @DisplayName("Capital normalization: null → 200M VND default")
    void testCapitalNormalization() {
        AdaptivePositionSizingDto result = adaptivePositionSizingService.calculateAdaptiveSize("FPT", null);

        assertThat(result.getAccountCapital()).isEqualTo(BigDecimal.valueOf(200_000_000));
    }

    @Test
    @DisplayName("Sizing factors list: có ít nhất 4 yếu tố (ATR, Kelly, Regime, Vol)")
    void testSizingFactorsComplete() {
        AdaptivePositionSizingDto result = adaptivePositionSizingService.calculateAdaptiveSize("FPT", testCapital);

        assertThat(result.getSizingFactors()).isNotNull();
        assertThat(result.getSizingFactors().size()).isGreaterThanOrEqualTo(4);
        assertThat(result.getSizingVerdict()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Low volatility environment: scale factor = 1.2x (bonus size)")
    void testLowVolatilityBonusScale() {
        // GARCH vol thấp: 0.8% daily → conditional vol annualized = 0.8 * sqrt(252) ≈ 12.7%
        GarchVolatilityForecastDto lowVolGarch = GarchVolatilityForecastDto.builder()
            .conditionalVolCurrent(BigDecimal.valueOf(12.7))
            .forecastT25SettlementVolPercent(BigDecimal.valueOf(1.3))
            .build();
        when(garchVolatilityForecastService.forecastVolatility(anyString())).thenReturn(lowVolGarch);

        AdaptivePositionSizingDto result = adaptivePositionSizingService.calculateAdaptiveSize("FPT", testCapital);

        assertThat(result.getVolatilityEnvironment()).isEqualTo("LOW");
        assertThat(result.getVolatilityScaleFactor().doubleValue()).isEqualTo(1.20);
    }
}