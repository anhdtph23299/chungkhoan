package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Một đối tượng (cổ phiếu hoặc nhóm ngành) được định vị trên đồ thị xoay tua RRG:
 * - Tọa độ hiện tại (currentPoint)
 * - Quỹ đạo xoay tua (trail history 5-10 phiên gần nhất)
 * - Véc-tơ vận tốc góc & hướng di chuyển (Heading & Rotational Velocity)
 * - Khuyến nghị định chế (STRONG_OVERWEIGHT, OVERWEIGHT, NEUTRAL, UNDERWEIGHT, STRICT_AVOID)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RrgItemDto {
    private String symbol;                  // Mã cổ phiếu hoặc mã ngành (e.g. "FPT" hoặc "SECTOR_BANK")
    private String name;                    // Tên đầy đủ
    private String sector;                  // Nhóm ngành
    private BigDecimal currentPrice;        // Giá hiện tại
    private BigDecimal changePercent;       // Biến động giá gần nhất (%)

    // ===== TỌA ĐỘ RRG HIỆN TẠI =====
    private RrgPointDto currentPoint;       // Tọa độ (RS-Ratio, RS-Momentum)
    private String quadrant;                // LEADING / WEAKENING / LAGGING / IMPROVING

    // ===== VÉC-TƠ ĐỘNG LỰC HỌC XOAY TUA =====
    private BigDecimal headingAngle;        // Góc di chuyển (0 - 360 độ): 0-90 = Đông Bắc (Leading), 180-270 = Tây Nam (Lagging)
    private String headingDirection;        // NORTHEAST, SOUTHEAST, SOUTHWEST, NORTHWEST
    private BigDecimal rotationalVelocity;  // Vận tốc góc xoay tua (khoảng cách dịch chuyển mỗi phiên)
    private BigDecimal distanceToCenter;    // Khoảng cách tới điểm cân bằng (100, 100)

    // ===== QUỸ ĐẠO LỊCH SỬ (TRAIL) =====
    private List<RrgPointDto> trailHistory; // 5 phiên gần nhất để vẽ đuôi vệt sao chổi

    // ===== KHUYẾN NGHỊ ĐỊNH CHẾ =====
    private String institutionalAction;     // STRONG_OVERWEIGHT / OVERWEIGHT / HOLD_TRAILING_STOP / UNDERWEIGHT / STRICT_AVOID
    private int convictionScore;            // 0 - 100
    private String qualitativeComment;      // Nhận định định chế chuyên sâu
}
