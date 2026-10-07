package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorRotationDto {

    private String sectorName;                  // Tên nhóm ngành (Công nghệ, Ngân hàng, Thép, Chứng khoán, ...)
    private double relativeStrengthScore;       // Điểm sức mạnh giá tương đối (RS 0 - 100)
    private double momentum20Day;               // Đà tăng giá 20 phiên (%)
    private String moneyFlowStatus;             // Trạng thái dòng tiền (DÒNG TIỀN MẠNH, TÍCH LŨY, TRUNG TÍNH, THẬN TRỌNG)
    private String allocationRecommendation;    // Khuyến nghị phân bổ (OVERWEIGHT, EQUAL_WEIGHT, UNDERWEIGHT)
    private List<String> topLeaderSymbols;      // Các cổ phiếu đầu ngành đại diện
    private String macroCatalyst;               // Câu chuyện vĩ mô & xúc tác tăng trưởng
}
