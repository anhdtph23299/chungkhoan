package com.vntrade.backend.service.risk;

import com.vntrade.backend.dto.StressTestReportDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class RiskStressTestServiceTest {

    private final RiskStressTestService stressTestService = new RiskStressTestService();

    @Test
    void testConductHistoricalStressTests() {
        BigDecimal nav = BigDecimal.valueOf(250_000_000);
        StressTestReportDto report = stressTestService.conductHistoricalStressTests(nav);

        assertNotNull(report);
        assertEquals(nav, report.getCurrentNav());
        assertEquals(3, report.getScenarioResults().size(), "Phải kiểm tra đủ 3 cuộc khủng hoảng lớn của chứng khoán Việt Nam");

        for (StressTestReportDto.CrisisScenarioResult scenario : report.getScenarioResults()) {
            assertNotNull(scenario.getScenarioName());
            assertNotNull(scenario.getTimePeriod());
            assertTrue(scenario.getMarketDropPercent().doubleValue() < 0, "Thị trường chung phải sụt giảm trong kịch bản khủng hoảng");
            assertTrue(scenario.getBotProtectedLossPercent().doubleValue() > scenario.getBuyAndHoldLossPercent().doubleValue(),
                "Tổn thất của Bot phải thấp hơn rất nhiều so với nhà đầu tư Buy & Hold");
            assertTrue(scenario.getCapitalPreservedPercent().doubleValue() >= 90.0,
                "Bot phải bảo toàn được ít nhất 90% vốn qua khủng hoảng");
            assertNotNull(scenario.getDefenseMechanismTriggered());
            assertNotNull(scenario.getRecoveryOutcome());
        }

        assertNotNull(report.getExecutiveStressVerdict());
        assertTrue(report.getExecutiveStressVerdict().contains("STRESS TEST VERDICT"));
    }
}
