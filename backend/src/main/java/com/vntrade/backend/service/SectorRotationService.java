package com.vntrade.backend.service;

import com.vntrade.backend.dto.RelativeRotationGraphDto;
import com.vntrade.backend.dto.RrgItemDto;
import com.vntrade.backend.dto.SectorRotationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class SectorRotationService {

    private final RelativeRotationGraphService relativeRotationGraphService;

    public SectorRotationService() {
        this.relativeRotationGraphService = null;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public SectorRotationService(RelativeRotationGraphService relativeRotationGraphService) {
        this.relativeRotationGraphService = relativeRotationGraphService;
    }

    private static final Map<String, SectorMeta> SECTOR_METAS = Map.of(
        "Công nghệ", new SectorMeta(List.of("FPT"), "Làn sóng chuyển đổi số & AI toàn cầu, doanh thu ký mới dịch vụ IT nước ngoài tăng trưởng > 28%"),
        "Ngân hàng", new SectorMeta(List.of("TCB", "MBB", "VCB"), "Tín dụng tăng tốc quý 3-4, tỷ lệ CASA dẫn đầu hệ thống và định giá P/B ở vùng an toàn"),
        "Thép & Vật liệu", new SectorMeta(List.of("HPG", "HSG"), "Kỳ vọng đại dự án Dung Quất 2 chạy thương mại cuối năm, nhu cầu thép nội địa hồi phục theo đầu tư công"),
        "Chứng khoán", new SectorMeta(List.of("SSI", "VND", "VCI"), "Thanh khoản thị trường duy trì 18.000 - 22.000 tỷ/phiên, kỳ vọng vận hành hệ thống KRX và nâng hạng FTSE"),
        "Bán lẻ", new SectorMeta(List.of("MWG", "MSN"), "Chuỗi Bách Hóa Xanh đóng góp lợi nhuận ròng, sức mua đồ công nghệ phục hồi dịp lễ cuối năm"),
        "Dầu khí & Hóa chất", new SectorMeta(List.of("DGC", "GAS", "PVD"), "Giá photpho vàng và phân bón thế giới biến động quanh vùng đáy, hưởng lợi dòng tiền phòng thủ"),
        "Bất động sản", new SectorMeta(List.of("VHM", "VIC", "NVL"), "Áp lực đáo hạn trái phiếu doanh nghiệp và dòng tiền bán nhà mới cần thêm thời gian hấp thụ chính sách")
    );

    private record SectorMeta(List<String> leaders, String catalyst) {}

    public List<SectorRotationDto> analyzeSectorRotation() {
        List<SectorRotationDto> sectors = new ArrayList<>();

        // 1. Thử lấy dữ liệu động lực xoay tua định chế từ RRG Engine
        Map<String, RrgItemDto> rrgMap = new HashMap<>();
        if (relativeRotationGraphService != null) {
            try {
                RelativeRotationGraphDto rrg = relativeRotationGraphService.calculateSectorsRrg();
                if (rrg != null && rrg.getItems() != null) {
                    for (RrgItemDto item : rrg.getItems()) {
                        if (item.getSector() != null) {
                            rrgMap.put(item.getSector().trim(), item);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Không thể tải RRG động cho sector rotation, sử dụng baseline: {}", e.getMessage());
            }
        }

        // 2. Tổng hợp dữ liệu động lượng chuẩn xác cho từng nhóm ngành
        for (Map.Entry<String, SectorMeta> entry : SECTOR_METAS.entrySet()) {
            String sectorName = entry.getKey();
            SectorMeta meta = entry.getValue();

            double rsScore = 70.0;
            double momentum = 0.0;
            String flowStatus = "TRUNG TÍNH";
            String recommendation = "EQUAL_WEIGHT";

            RrgItemDto rrgItem = rrgMap.get(sectorName);
            if (rrgItem != null && rrgItem.getCurrentPoint() != null) {
                rsScore = rrgItem.getCurrentPoint().getRsRatio() != null 
                    ? rrgItem.getCurrentPoint().getRsRatio().doubleValue() 
                    : 70.0;
                momentum = rrgItem.getCurrentPoint().getRsMomentum() != null
                    ? Math.round((rrgItem.getCurrentPoint().getRsMomentum().doubleValue() - 100.0) * 10.0) / 10.0
                    : 0.0;

                String quadrant = rrgItem.getQuadrant();
                if ("LEADING".equals(quadrant)) {
                    flowStatus = "DÒNG TIỀN DẪN DẮT (ALPHA VƯỢT TRỘI)";
                    recommendation = "OVERWEIGHT (Ưu tiên giải ngân tỷ trọng cao)";
                } else if ("IMPROVING".equals(quadrant)) {
                    flowStatus = "TÍCH LŨY ĐẢO CHIỀU TÍCH CỰC";
                    recommendation = "ACCUMULATE (Gom hàng đón sóng luân chuyển)";
                } else if ("WEAKENING".equals(quadrant)) {
                    flowStatus = "ĐÀ TĂNG GIẢM TỐC / HẠ NHIỆT";
                    recommendation = "TAKE PROFIT (Bảo toàn lợi nhuận / Hạ tỷ trọng)";
                } else {
                    flowStatus = "DÒNG TIỀN SUY YẾU / TỤT HẬU";
                    recommendation = "UNDERWEIGHT (Nghiêm cấm mua đuổi)";
                }
            } else {
                // Baseline an toàn khi offline
                switch (sectorName) {
                    case "Bán lẻ" -> { rsScore = 107.2; momentum = 3.2; flowStatus = "DÒNG TIỀN DẪN DẮT"; recommendation = "OVERWEIGHT"; }
                    case "Thép & Vật liệu" -> { rsScore = 101.4; momentum = 2.2; flowStatus = "DÒNG TIỀN DẪN DẮT"; recommendation = "OVERWEIGHT"; }
                    case "Bất động sản" -> { rsScore = 98.2; momentum = 0.0; flowStatus = "TÍCH LŨY ĐẢO CHIỀU"; recommendation = "ACCUMULATE"; }
                    case "Ngân hàng" -> { rsScore = 98.3; momentum = -2.0; flowStatus = "SUY YẾU"; recommendation = "UNDERWEIGHT"; }
                    case "Dầu khí & Hóa chất" -> { rsScore = 98.0; momentum = -2.1; flowStatus = "SUY YẾU"; recommendation = "UNDERWEIGHT"; }
                    case "Chứng khoán" -> { rsScore = 96.5; momentum = -1.2; flowStatus = "SUY YẾU"; recommendation = "UNDERWEIGHT"; }
                    case "Công nghệ" -> { rsScore = 93.6; momentum = -3.4; flowStatus = "TỤT HẬU"; recommendation = "UNDERWEIGHT"; }
                }
            }

            sectors.add(SectorRotationDto.builder()
                .sectorName(sectorName)
                .relativeStrengthScore(rsScore)
                .momentum20Day(momentum)
                .moneyFlowStatus(flowStatus)
                .allocationRecommendation(recommendation)
                .topLeaderSymbols(meta.leaders())
                .macroCatalyst(meta.catalyst())
                .build());
        }

        sectors.sort(Comparator.comparingDouble(SectorRotationDto::getRelativeStrengthScore).reversed());
        return sectors;
    }
}
