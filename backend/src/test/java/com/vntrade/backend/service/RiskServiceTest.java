package com.vntrade.backend.service;

import com.vntrade.backend.dto.PositionSizingRequest;
import com.vntrade.backend.dto.PositionSizingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class RiskServiceTest {

    private RiskService riskService;

    @BeforeEach
    void setUp() {
        riskService = new RiskService();
    }

    @Test
    @DisplayName("Kiểm tra tính toán kích thước vị thế chuẩn xác với quy tắc 2% NAV")
    void testCalculatePositionSize_Standard() {
        PositionSizingRequest request = PositionSizingRequest.builder()
            .symbol("HPG")
            .accountCapital(BigDecimal.valueOf(200_000_000)) // Vốn 200tr
            .maxRiskPercent(BigDecimal.valueOf(2.0))          // Rủi ro 2% = 4tr
            .entryPrice(BigDecimal.valueOf(30000))            // Mua 30,000
            .stopLossPrice(BigDecimal.valueOf(27900))         // SL 27,900 (-7%)
            .takeProfitPrice(BigDecimal.valueOf(34500))       // TP 34,500 (+15%)
            .build();

        PositionSizingResult result = riskService.calculatePositionSize(request);

        assertNotNull(result);
        assertEquals("HPG", result.getSymbol());
        // Max risk amount: 200,000,000 * 2% = 4,000,000
        assertEquals(0, BigDecimal.valueOf(4_000_000).compareTo(result.getMaxRiskAmount()));
        // Risk per share: 30,000 - 27,900 = 2,100
        assertEquals(0, BigDecimal.valueOf(2_100).compareTo(result.getRiskPerShare()));
        // Max shares: 4,000,000 / 2,100 = 1904.76 -> làm tròn lô 100 = 1900 cp
        assertEquals(1900, result.getMaxSharesToBuy());
        // Tỷ lệ R:R = (34,500 - 30,000) / (30,000 - 27,900) = 4,500 / 2,100 = 2.14
        assertTrue(result.getRiskRewardRatio().compareTo(BigDecimal.valueOf(2.0)) >= 0);
        assertTrue(result.isAcceptable());
        assertTrue(result.getVerdict().contains("ĐẠT CHUẨN"));
    }

    @Test
    @DisplayName("Kiểm tra từ chối lệnh khi tỷ lệ R:R < 2.0 hoặc Cắt lỗ > 8%")
    void testCalculatePositionSize_RejectBadRiskReward() {
        PositionSizingRequest request = PositionSizingRequest.builder()
            .symbol("VHM")
            .accountCapital(BigDecimal.valueOf(100_000_000))
            .maxRiskPercent(BigDecimal.valueOf(2.0))
            .entryPrice(BigDecimal.valueOf(40000))
            .stopLossPrice(BigDecimal.valueOf(35000)) // Lỗ 5,000 = 12.5% (> 8%)
            .takeProfitPrice(BigDecimal.valueOf(44000)) // Lãi 4,000 = 10% (R:R = 4000/5000 = 0.8 < 2.0)
            .build();

        PositionSizingResult result = riskService.calculatePositionSize(request);

        assertNotNull(result);
        assertFalse(result.isAcceptable());
        assertTrue(result.getVerdict().contains("CẢNH BÁO"));
    }

    @Test
    @DisplayName("Kiểm tra phân loại nhóm ngành chuẩn xác cho VN30")
    void testGetSectorForSymbol() {
        assertEquals("CÔNG NGHỆ", riskService.getSectorForSymbol("FPT"));
        assertEquals("NGÂN HÀNG", riskService.getSectorForSymbol("TCB"));
        assertEquals("THÉP & HÓA CHẤT", riskService.getSectorForSymbol("HPG"));
        assertEquals("CHỨNG KHOÁN", riskService.getSectorForSymbol("SSI"));
        assertEquals("BÁN LẺ & TIÊU DÙNG", riskService.getSectorForSymbol("MWG"));
        assertEquals("BẤT ĐỘNG SẢN", riskService.getSectorForSymbol("VHM"));
    }

    @Test
    @DisplayName("Kiểm tra báo cáo sức khỏe danh mục PortfolioHealthReport")
    void testEvaluatePortfolioHealth() {
        var report = riskService.evaluatePortfolioHealth();
        assertNotNull(report);
        assertNotNull(report.getStatus());
        assertEquals(4, report.getMaxAllowedPositions());
        assertNotNull(report.getRecommendedRiskPercent());
        assertFalse(report.getRiskChecklist().isEmpty());
    }
}
