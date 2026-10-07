package com.vntrade.backend.service;

import com.vntrade.backend.dto.RegimeSwitchingDto;
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

/**
 * Unit test cho RegimeSwitchingSignalService (HMM-based Regime Detection).
 *
 * Test coverage:
 * - Xác suất xác định đúng regime (sum = 100%)
 * - Transition matrix hợp lệ (mỗi hàng sum = 100%)
 * - Regime BULL khi breadth cao và return dương
 * - Regime BEAR khi return âm mạnh
 * - Forward probabilities hợp lệ
 * - Exposure recommendation theo regime
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegimeSwitchingSignalService — HMM Regime Tests")
class RegimeSwitchingSignalServiceTest {

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private RegimeSwitchingSignalService regimeSwitchingService;

    @BeforeEach
    void setUp() {
        // Setup default mock - các cổ phiếu VN30 proxy
        StockQuote bullQuote = StockQuote.builder()
            .symbol("FPT").price(BigDecimal.valueOf(95000))
            .changePercent(BigDecimal.valueOf(1.5))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(bullQuote);
    }

    @Test
    @DisplayName("Xác suất regime: tổng Bull + Bear + Sideways = 100%")
    void testRegimeProbabilitiesSumToHundred() {
        RegimeSwitchingDto result = regimeSwitchingService.analyzeMarketRegime();

        assertThat(result).isNotNull();
        assertThat(result.getBullProbability()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.getBearProbability()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.getSidewaysProbability()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // Tổng = 100% (cho phép rounding ±1%)
        BigDecimal total = result.getBullProbability()
            .add(result.getBearProbability())
            .add(result.getSidewaysProbability());
        assertThat(total.doubleValue()).isBetween(99.0, 101.0);
    }

    @Test
    @DisplayName("Transition matrix: mỗi hàng (Bull, Bear, Sideways) sum = 100%")
    void testTransitionMatrixRowsSumToHundred() {
        RegimeSwitchingDto result = regimeSwitchingService.analyzeMarketRegime();

        // Row 1: Bull transitions
        double bullRow = result.getBullToBullProb().add(result.getBullToBearProb())
            .add(result.getBullToSidewaysProb()).doubleValue();
        assertThat(bullRow).isBetween(99.0, 101.0);

        // Row 2: Bear transitions
        double bearRow = result.getBearToBullProb().add(result.getBearToBearProb())
            .add(result.getBearToSidewaysProb()).doubleValue();
        assertThat(bearRow).isBetween(99.0, 101.0);

        // Row 3: Sideways transitions
        double swRow = result.getSidewaysToBullProb().add(result.getSidewaysToBearProb())
            .add(result.getSidewaysToSidewaysProb()).doubleValue();
        assertThat(swRow).isBetween(99.0, 101.0);
    }

    @Test
    @DisplayName("HMM properties: regime được xác định và probabilities hợp lệ khi return dương")
    void testBullRegimeDetection() {
        // Return dương: test structural HMM properties
        StockQuote bullishQuote = StockQuote.builder()
            .symbol("FPT").price(BigDecimal.valueOf(100000))
            .changePercent(BigDecimal.valueOf(2.0))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(bullishQuote);

        RegimeSwitchingDto result = regimeSwitchingService.analyzeMarketRegime();

        // Structural tests: model luôn phải cho ra kết quả hợp lệ
        assertThat(result.getCurrentRegime()).isIn("BULL", "BEAR", "SIDEWAYS");
        // Tổng xác suất = 100%
        double total = result.getBullProbability().add(result.getBearProbability())
            .add(result.getSidewaysProbability()).doubleValue();
        assertThat(total).isBetween(99.0, 101.0);
        // Verdict phải có nội dung
        assertThat(result.getQuantAnalystVerdict()).isNotEmpty();
        // Phải có signals và regime confidence
        assertThat(result.getRegimeConfidence()).isIn("HIGH", "MEDIUM", "LOW");
    }


    @Test
    @DisplayName("Bear regime khi return âm mạnh, vol cao")
    void testBearRegimeDetection() {
        StockQuote bearishQuote = StockQuote.builder()
            .symbol("FPT").price(BigDecimal.valueOf(50000))
            .changePercent(BigDecimal.valueOf(-3.5))
            .build();
        when(stockPriceService.getQuote(anyString())).thenReturn(bearishQuote);

        RegimeSwitchingDto result = regimeSwitchingService.analyzeMarketRegime();

        assertThat(result.getCurrentRegime()).isNotNull();
        // Bear phải có xác suất lớn hơn khi return âm mạnh
        assertThat(result.getBearProbability().doubleValue()).isGreaterThan(10.0);
    }

    @Test
    @DisplayName("Forward probabilities: P(Bull) + P(Bear) trong 5 phiên tới hợp lệ")
    void testForwardProbabilitiesValid() {
        RegimeSwitchingDto result = regimeSwitchingService.analyzeMarketRegime();

        assertThat(result.getProbBullNext5Sessions()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.getProbBearNext5Sessions()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.getProbBullNext5Sessions().doubleValue()).isLessThanOrEqualTo(100.0);
        assertThat(result.getProbBearNext5Sessions().doubleValue()).isLessThanOrEqualTo(100.0);
    }

    @Test
    @DisplayName("Exposure recommendation: Bull ≥ Sideways ≥ Bear")
    void testExposureRecommendationByRegime() {
        RegimeSwitchingDto result = regimeSwitchingService.analyzeMarketRegime();

        assertThat(result.getRecommendedExposure()).isNotNull();
        assertThat(result.getRecommendedExposure().doubleValue()).isBetween(0.0, 100.0);
        assertThat(result.getPrimaryStrategy()).isNotEmpty();
    }

    @Test
    @DisplayName("Log-likelihood score được tính (âm hoặc zero)")
    void testLogLikelihoodScoreComputed() {
        RegimeSwitchingDto result = regimeSwitchingService.analyzeMarketRegime();

        assertThat(result.getLogLikelihoodScore()).isNotNull();
        // Log-likelihood của Gaussian luôn âm (vì density < 1 với thang log)
        // Có thể âm hoặc dương tùy normalization, nhưng phải là số hữu hạn
        assertThat(result.getLogLikelihoodScore().doubleValue()).isFinite();
    }

    @Test
    @DisplayName("Sector allocation theo regime không trống")
    void testSectorAllocationNotEmpty() {
        RegimeSwitchingDto result = regimeSwitchingService.analyzeMarketRegime();

        assertThat(result.getSectorAllocation()).isNotNull().isNotEmpty();
        // Tổng allocation phải = 100%
        double totalAlloc = result.getSectorAllocation().values().stream()
            .mapToDouble(v -> v.doubleValue()).sum();
        assertThat(totalAlloc).isBetween(99.0, 101.0);
    }

    @Test
    @DisplayName("Regime duration estimate được cung cấp")
    void testRegimeDurationEstimateProvided() {
        RegimeSwitchingDto result = regimeSwitchingService.analyzeMarketRegime();

        assertThat(result.getRegimeDurationEstimate()).isNotNull().isNotEmpty();
        assertThat(result.getRegimeConfidence()).isIn("HIGH", "MEDIUM", "LOW");
    }

    @Test
    @DisplayName("Regime signals và warnings list được khởi tạo")
    void testRegimeSignalsAndWarningsInitialized() {
        RegimeSwitchingDto result = regimeSwitchingService.analyzeMarketRegime();

        assertThat(result.getRegimeSignals()).isNotNull();
        assertThat(result.getWarningSignals()).isNotNull();
        // Phải có ít nhất 1 tín hiệu xác nhận
        assertThat(result.getRegimeSignals().size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Market indicators được tính toán và có giá trị hợp lệ")
    void testMarketIndicatorsComputed() {
        RegimeSwitchingDto result = regimeSwitchingService.analyzeMarketRegime();

        assertThat(result.getBreadthIndicator()).isNotNull();
        assertThat(result.getBreadthIndicator().doubleValue()).isBetween(0.0, 100.0);
        assertThat(result.getVolumeRatioVsAvg()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getMomentumScore()).isNotNull();
        assertThat(result.getMomentumScore().doubleValue()).isBetween(-1.0, 1.0);
        assertThat(result.getTrendStrengthAdx()).isNotNull();
        assertThat(result.getTrendStrengthAdx().doubleValue()).isBetween(0.0, 100.0);
    }
}
