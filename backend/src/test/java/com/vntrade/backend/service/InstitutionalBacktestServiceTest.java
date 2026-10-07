package com.vntrade.backend.service;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.InstitutionalBacktestResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class InstitutionalBacktestServiceTest {

    private CandleDataService candleDataService;
    private TechnicalIndicatorService technicalIndicatorService;
    private InstitutionalBacktestService institutionalBacktestService;

    @BeforeEach
    void setUp() {
        candleDataService = Mockito.mock(CandleDataService.class);
        technicalIndicatorService = new TechnicalIndicatorService();
        institutionalBacktestService = new InstitutionalBacktestService(candleDataService, technicalIndicatorService);

        List<Candle> mockCandles = new ArrayList<>();
        BigDecimal price = BigDecimal.valueOf(100000);
        for (int i = 0; i < 150; i++) {
            price = price.multiply(BigDecimal.valueOf(1.002));
            mockCandles.add(Candle.builder()
                .date(LocalDate.now().minusDays(150 - i))
                .open(price.multiply(BigDecimal.valueOf(0.99)))
                .high(price.multiply(BigDecimal.valueOf(1.02)))
                .low(price.multiply(BigDecimal.valueOf(0.98)))
                .close(price)
                .volume(3000000L)
                .build());
        }

        when(candleDataService.getHistoricalCandles(anyString(), anyInt())).thenReturn(mockCandles);
    }

    @Test
    void testRunInstitutionalBacktest() {
        BigDecimal capital = BigDecimal.valueOf(100_000_000);
        InstitutionalBacktestResultDto result = institutionalBacktestService.runInstitutionalBacktest(
            "FPT", "VCP_INSTITUTIONAL_BREAKOUT", 150, capital, 7.0, 15.0);

        assertNotNull(result);
        assertEquals("FPT", result.getSymbol());
        assertEquals("VCP_INSTITUTIONAL_BREAKOUT", result.getStrategyName());
        assertTrue(result.isT25Enforced(), "Ràng buộc chu kỳ T+2.5 bắt buộc phải được kích hoạt");
        assertNotNull(result.getWalkForwardEfficiencyPercent());
        assertNotNull(result.getOverfittingRisk());
        assertNotNull(result.getInstitutionalAuditVerdict());
        assertTrue(result.getInstitutionalAuditVerdict().contains("KIỂM TOÁN ĐỊNH LƯỢNG BACKTEST ĐỊNH CHẾ"));
    }

    @Test
    void testRunVn30InstitutionalMatrix() {
        com.vntrade.backend.dto.Vn30BacktestMatrixDto matrix = institutionalBacktestService.runVn30InstitutionalMatrix("VCP_INSTITUTIONAL_BREAKOUT");

        assertNotNull(matrix);
        assertEquals(8, matrix.getTestedStocksCount());
        assertNotNull(matrix.getRecommendedTopPick());
        assertEquals(8, matrix.getRankings().size());
        assertTrue(matrix.getRankings().get(0).getRank() == 1);
        assertNotNull(matrix.getInstitutionalAuditSummary());
        assertTrue(matrix.getInstitutionalAuditSummary().contains("MA TRẬN KIỂM TOÁN ĐỊNH LƯỢNG VN30"));
    }

    @Test
    void testRealCandleBacktestMatrix() {
        StockPriceService stockPriceService = new StockPriceService();
        CandleDataService realCandleService = new CandleDataService(stockPriceService);
        TechnicalIndicatorService realTechnicalService = new TechnicalIndicatorService();
        InstitutionalBacktestService realBacktestService = new InstitutionalBacktestService(realCandleService, realTechnicalService);

        com.vntrade.backend.dto.Vn30BacktestMatrixDto matrix = realBacktestService.runVn30InstitutionalMatrix("VCP_INSTITUTIONAL_BREAKOUT");

        assertNotNull(matrix);
        System.out.println("=========================================================================================");
        System.out.println(" KẾT QUẢ BACKTEST ĐỊNH LƯỢNG INSTITUTIONAL MATRIX - 8 MÃ VN30 ĐẦU NGÀNH");
        System.out.println("=========================================================================================");
        System.out.printf("%-6s | %-20s | %-10s | %-8s | %-8s | %-8s | %-8s | %-12s%n",
            "MÃ", "NGÀNH", "RETURN%", "WIN%", "P.FACTOR", "MAX DD%", "SHARPE", "ALLOCATION");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (var item : matrix.getRankings()) {
            System.out.printf("%-6s | %-20s | %9.2f%% | %7.1f%% | %8.2f | %7.2f%% | %8.2f | %-12s%n",
                item.getSymbol(), item.getSector(),
                item.getTotalReturnPercent(), item.getWinRatePercent(),
                item.getProfitFactor(), item.getMaxDrawdownPercent(),
                item.getSharpeRatio(), item.getAllocationRecommendation());
        }
        System.out.println("=========================================================================================");
        System.out.println("Top Khuyến nghị: " + matrix.getRecommendedTopPick());
        System.out.println("Win Rate Trung Bình: " + matrix.getAverageWinRatePercent() + "%");
        System.out.println("Tỷ Suất TB: " + matrix.getAverageReturnPercent() + "%");
        System.out.println("Sharpe Trung Bình: " + matrix.getAverageSharpeRatio());
        System.out.println("=========================================================================================");
    }
}
