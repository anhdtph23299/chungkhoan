package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Điểm tọa độ RRG (Relative Rotation Graph) tại một thời điểm:
 * - X: JdK RS-Ratio (sức mạnh tương đối so với benchmark, mốc chuẩn 100)
 * - Y: JdK RS-Momentum (động lượng thay đổi của sức mạnh tương đối, mốc chuẩn 100)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RrgPointDto {
    private LocalDate date;
    private BigDecimal rsRatio;       // X-axis: > 100 = Outperforming, < 100 = Underperforming
    private BigDecimal rsMomentum;    // Y-axis: > 100 = Accelerating, < 100 = Decelerating
    private String quadrant;          // LEADING, WEAKENING, LAGGING, IMPROVING
}
