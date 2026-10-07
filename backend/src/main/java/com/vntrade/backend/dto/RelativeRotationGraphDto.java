package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO tổng thể đồ thị xoay tua tương đối RRG (Relative Rotation Graph):
 * - Benchmark: VN-INDEX (hoặc VN30)
 * - Danh sách cổ phiếu / ngành phân bổ trên 4 góc phần tư
 * - Đếm số lượng tài sản trên từng góc phần tư để nhận diện Market Breadth
 * - Kết luận xoay tua dòng tiền vĩ mô
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelativeRotationGraphDto {
    private String benchmark;               // VN-INDEX / VN30
    private LocalDate analysisDate;         // Ngày phân tích
    private int periodSessions;             // Chu kỳ tính toán (thường 14 hoặc 20 phiên)

    // ===== DANH SÁCH TÀI SẢN =====
    private List<RrgItemDto> items;

    // ===== PHÂN BỔ 4 GÓC PHẦN TƯ =====
    private int leadingCount;               // Số lượng tài sản ở góc Dẫn dắt
    private int weakeningCount;             // Số lượng tài sản ở góc Suy yếu
    private int laggingCount;               // Số lượng tài sản ở góc Tụt hậu
    private int improvingCount;             // Số lượng tài sản ở góc Cải thiện

    // ===== MARKET BREADTH & ROTATION VERDICT =====
    private String rotationVerdict;         // Nhận định xu thế xoay tua tổng quát
    private String dominantQuadrant;        // Góc phần tư chiếm ưu thế
    private String topLeadingSectors;       // Các ngành dẫn dắt dòng tiền mạnh nhất
    private String toxicLaggingSectors;     // Các ngành đang bị rút ròng, cần tránh
}
