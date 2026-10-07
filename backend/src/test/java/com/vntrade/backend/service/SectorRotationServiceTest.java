package com.vntrade.backend.service;

import com.vntrade.backend.dto.SectorRotationDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SectorRotationServiceTest {

    private final SectorRotationService service = new SectorRotationService();

    @Test
    @DisplayName("Kiểm tra phân tích dịch chuyển dòng tiền theo ngành: sắp xếp theo Relative Strength giảm dần")
    void testAnalyzeSectorRotation() {
        List<SectorRotationDto> sectors = service.analyzeSectorRotation();

        assertNotNull(sectors);
        assertFalse(sectors.isEmpty());
        assertEquals(7, sectors.size());

        // Phải được sắp xếp theo điểm sức mạnh tương đối giảm dần
        for (int i = 0; i < sectors.size() - 1; i++) {
            assertTrue(sectors.get(i).getRelativeStrengthScore() >= sectors.get(i + 1).getRelativeStrengthScore(),
                "Ngành đứng trước phải có RS score >= ngành đứng sau");
        }

        // Ngành dẫn đầu phải có khuyến nghị OVERWEIGHT
        assertTrue(sectors.get(0).getAllocationRecommendation().contains("OVERWEIGHT"));
    }
}
