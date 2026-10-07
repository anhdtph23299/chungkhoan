package com.vntrade.backend.service;

import com.vntrade.backend.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit Test cho RelativeRotationGraphService (RRG & Mansfield Relative Strength).
 *
 * Kiểm tra:
 * - 4 Quadrants: LEADING, WEAKENING, LAGGING, IMPROVING
 * - Heading Angle (0 - 360 độ) và Heading Direction (NORTHEAST, NORTHWEST, SOUTHWEST, SOUTHEAST)
 * - Khuyến nghị định chế & Conviction Score
 * - Phân tích cổ phiếu đơn lẻ (Single Stock RRG & 5-session Trail)
 * - Phân tích Sector Rotation Radar (Đủ 7 nhóm ngành VN-Market)
 * - Phân tích VN30 và tính tổng hợp Market Breadth
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RelativeRotationGraphService — RRG Rotational Dynamics Tests")
class RelativeRotationGraphServiceTest {

    @Mock
    private CandleDataService candleDataService;

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private RelativeRotationGraphService rrgService;

    private List<Candle> mockBenchCandles;
    private List<Candle> mockStockCandles;

    @BeforeEach
    void setUp() {
        LocalDate today = LocalDate.now();
        mockBenchCandles = new ArrayList<>();
        mockStockCandles = new ArrayList<>();

        double benchBase = 1250.0;
        double stockBase = 100_000.0;

        for (int i = 50; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            benchBase += Math.sin(i * 0.2) * 5.0 + 1.0;
            stockBase += Math.cos(i * 0.2) * 400.0 + 150.0;

            mockBenchCandles.add(Candle.builder()
                .symbol("VNINDEX")
                .date(d)
                .open(BigDecimal.valueOf(benchBase - 2))
                .high(BigDecimal.valueOf(benchBase + 4))
                .low(BigDecimal.valueOf(benchBase - 3))
                .close(BigDecimal.valueOf(benchBase))
                .volume(700_000_000L)
                .build());

            mockStockCandles.add(Candle.builder()
                .symbol("FPT")
                .date(d)
                .open(BigDecimal.valueOf(stockBase - 200))
                .high(BigDecimal.valueOf(stockBase + 500))
                .low(BigDecimal.valueOf(stockBase - 300))
                .close(BigDecimal.valueOf(stockBase))
                .volume(3_000_000L)
                .build());
        }

        when(candleDataService.getHistoricalCandles(eq("VNINDEX"), anyInt())).thenReturn(mockBenchCandles);
        when(candleDataService.getHistoricalCandles(anyString(), anyInt())).thenReturn(mockStockCandles);

        when(stockPriceService.getQuote(anyString())).thenReturn(StockQuote.builder()
            .symbol("FPT")
            .price(BigDecimal.valueOf(135_000))
            .changePercent(BigDecimal.valueOf(1.8))
            .volume(2_500_000L)
            .build());
    }

    @Test
    @DisplayName("4 Quadrants xác định chính xác theo tọa độ (RS-Ratio, RS-Momentum)")
    void testQuadrantDetermination() {
        assertThat(RelativeRotationGraphService.determineQuadrant(105.0, 102.0)).isEqualTo("LEADING");
        assertThat(RelativeRotationGraphService.determineQuadrant(104.0, 97.5)).isEqualTo("WEAKENING");
        assertThat(RelativeRotationGraphService.determineQuadrant(96.0, 95.0)).isEqualTo("LAGGING");
        assertThat(RelativeRotationGraphService.determineQuadrant(98.0, 103.0)).isEqualTo("IMPROVING");

        // Edge case: Exactly at 100.0
        assertThat(RelativeRotationGraphService.determineQuadrant(100.0, 100.0)).isEqualTo("LEADING");
    }

    @Test
    @DisplayName("Heading Angle & Direction tính đúng theo véc-tơ dịch chuyển")
    void testHeadingAngleAndDirection() {
        // DeltaX > 0, DeltaY > 0 -> Đông Bắc (0 - 90 độ)
        double angleNE = RelativeRotationGraphService.calculateHeadingAngle(1.0, 1.0);
        assertThat(angleNE).isEqualTo(45.0);
        assertThat(RelativeRotationGraphService.determineHeadingDirection(angleNE)).isEqualTo("NORTHEAST");

        // DeltaX < 0, DeltaY > 0 -> Tây Bắc (90 - 180 độ)
        double angleNW = RelativeRotationGraphService.calculateHeadingAngle(-1.0, 1.0);
        assertThat(angleNW).isEqualTo(135.0);
        assertThat(RelativeRotationGraphService.determineHeadingDirection(angleNW)).isEqualTo("NORTHWEST");

        // DeltaX < 0, DeltaY < 0 -> Tây Nam (180 - 270 độ)
        double angleSW = RelativeRotationGraphService.calculateHeadingAngle(-1.0, -1.0);
        assertThat(angleSW).isEqualTo(225.0);
        assertThat(RelativeRotationGraphService.determineHeadingDirection(angleSW)).isEqualTo("SOUTHWEST");

        // DeltaX > 0, DeltaY < 0 -> Đông Nam (270 - 360 độ)
        double angleSE = RelativeRotationGraphService.calculateHeadingAngle(1.0, -1.0);
        assertThat(angleSE).isEqualTo(315.0);
        assertThat(RelativeRotationGraphService.determineHeadingDirection(angleSE)).isEqualTo("SOUTHEAST");
    }

    @Test
    @DisplayName("Khuyến nghị định chế & Conviction Score theo góc phần tư và hướng véc-tơ")
    void testActionAndConviction() {
        // LEADING + NORTHEAST -> Siêu alpha
        String actionLead = RelativeRotationGraphService.determineAction("LEADING", "NORTHEAST");
        assertThat(actionLead).isEqualTo("STRONG_OVERWEIGHT");
        int convLead = RelativeRotationGraphService.calculateConviction("LEADING", "NORTHEAST", 5.0);
        assertThat(convLead).isGreaterThanOrEqualTo(90);

        // LAGGING + SOUTHWEST -> Strict avoid
        String actionLag = RelativeRotationGraphService.determineAction("LAGGING", "SOUTHWEST");
        assertThat(actionLag).isEqualTo("STRICT_AVOID");
        int convLag = RelativeRotationGraphService.calculateConviction("LAGGING", "SOUTHWEST", 6.0);
        assertThat(convLag).isLessThanOrEqualTo(25);

        // WEAKENING -> Khóa lợi nhuận
        String actionWeak = RelativeRotationGraphService.determineAction("WEAKENING", "SOUTHEAST");
        assertThat(actionWeak).isEqualTo("HOLD_TRAILING_STOP");

        // IMPROVING + NORTHEAST -> Chớm vào sóng tăng
        String actionImp = RelativeRotationGraphService.determineAction("IMPROVING", "NORTHEAST");
        assertThat(actionImp).isEqualTo("OVERWEIGHT");
    }

    @Test
    @DisplayName("Phân tích RRG cổ phiếu đơn lẻ trả về đầy đủ tọa độ, đuôi trail và nhận định")
    void testCalculateSingleStockRrg() {
        RrgItemDto result = rrgService.calculateSingleStockRrg("FPT");

        assertThat(result).isNotNull();
        assertThat(result.getSymbol()).isEqualTo("FPT");
        assertThat(result.getCurrentPoint()).isNotNull();
        assertThat(result.getCurrentPoint().getRsRatio()).isNotNull();
        assertThat(result.getCurrentPoint().getRsMomentum()).isNotNull();
        assertThat(result.getQuadrant()).isNotNull();

        // Kiểm tra lịch sử đuôi vệt (trail history)
        assertThat(result.getTrailHistory()).isNotEmpty();
        assertThat(result.getTrailHistory().size()).isLessThanOrEqualTo(5);

        // Kiểm tra thông số động lực học
        assertThat(result.getHeadingAngle()).isNotNull();
        assertThat(result.getHeadingDirection()).isNotNull();
        assertThat(result.getRotationalVelocity()).isNotNull();
        assertThat(result.getDistanceToCenter()).isNotNull();
        assertThat(result.getQualitativeComment()).isNotEmpty();
    }

    @Test
    @DisplayName("Phân tích Sector Rotation Radar bao phủ các nhóm ngành kinh tế trọng điểm VN")
    void testCalculateSectorsRrg() {
        RelativeRotationGraphDto result = rrgService.calculateSectorsRrg();

        assertThat(result).isNotNull();
        assertThat(result.getBenchmark()).isEqualTo("VNINDEX");
        assertThat(result.getItems()).isNotEmpty();

        // Kiểm tra các ngành chủ chốt xuất hiện
        List<String> sectorNames = result.getItems().stream().map(RrgItemDto::getSector).toList();
        assertThat(sectorNames).contains("Công nghệ", "Ngân hàng", "Thép & Vật liệu", "Chứng khoán", "Bất động sản");

        // Kiểm tra phân bổ 4 góc phần tư hợp lệ
        int totalQuadrantCount = result.getLeadingCount() + result.getWeakeningCount()
            + result.getLaggingCount() + result.getImprovingCount();
        assertThat(totalQuadrantCount).isEqualTo(result.getItems().size());

        assertThat(result.getRotationVerdict()).isNotEmpty();
        assertThat(result.getDominantQuadrant()).isNotEmpty();
    }

    @Test
    @DisplayName("Phân tích RRG rổ VN30 hoạt động chính xác và không bị lỗi")
    void testCalculateVn30Rrg() {
        RelativeRotationGraphDto result = rrgService.calculateVn30Rrg();

        assertThat(result).isNotNull();
        assertThat(result.getItems()).isNotEmpty();
        assertThat(result.getPeriodSessions()).isEqualTo(14);
        assertThat(result.getAnalysisDate()).isNotNull();
    }

    @Test
    @DisplayName("Benchmark rỗng thì tự động fallback về VNINDEX")
    void testBenchmarkFallback() {
        RelativeRotationGraphDto result = rrgService.analyzeFullRrg(List.of("FPT", "VCB"), null, 0);

        assertThat(result.getBenchmark()).isEqualTo("VNINDEX");
        assertThat(result.getPeriodSessions()).isEqualTo(14);
    }
}
