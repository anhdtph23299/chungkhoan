package com.vntrade.backend.service;

import com.vntrade.backend.dto.ExecutionAlgoDto;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExecutionAlgorithmService {

    private final StockPriceService stockPriceService;

    public ExecutionAlgoDto planInstitutionalExecution(String symbol, int totalQuantity, String algorithm) {
        StockQuote quote = stockPriceService.getQuote(symbol);
        BigDecimal price = quote.getPrice() != null ? quote.getPrice() : BigDecimal.valueOf(50000);

        // Quy tắc làm tròn lô 100 cp chuẩn sàn Việt Nam
        int qty = Math.max(500, (totalQuantity / 100) * 100);
        String algo = algorithm != null ? algorithm.toUpperCase() : "VWAP_SMART_SLICING";

        // Đường cong khối lượng giao dịch chuẩn của TTCK Việt Nam (Volume Profile Curve):
        // 9:15 - 9:45 (Mở phiên sôi động): 20%
        // 9:45 - 11:30 (Phiên sáng tích lũy): 25%
        // 13:00 - 14:00 (Đầu phiên chiều): 20%
        // 14:00 - 14:28 (Khung giờ tay to kéo xả): 25%
        // 14:30 - 14:45 (Phiên đóng cửa ATC): 10%

        List<ExecutionAlgoDto.OrderTranche> tranches = new ArrayList<>();
        double[] weights = { 0.20, 0.25, 0.20, 0.25, 0.10 };
        String[] windows = {
            "09:15 - 09:45 (Khởi động phiên)",
            "09:45 - 11:30 (Tích lũy sáng)",
            "13:00 - 14:00 (Đầu phiên chiều)",
            "14:00 - 14:28 (Sóng chiều cao trào)",
            "14:30 - 14:45 (Khớp lệnh ATC)"
        };
        String[] tactics = {
            "Hấp thụ lực bán mở phiên giá thấp, tránh đua lệnh ATO",
            "Chẻ nhỏ lệnh Limit gom âm thầm (Stealth mode), không để lộ vết chân tạo lập",
            "Hấp thụ lực xả T+2.5 của nhà đầu tư nhỏ lẻ phiên chiều",
            "Gia tăng tỷ trọng khi dòng tiền lớn kích hoạt điểm nổ",
            "Đặt lệnh ATC bảo đảm khớp đủ số lượng với trượt giá tối thiểu"
        };

        int allocatedSoFar = 0;
        for (int i = 0; i < weights.length; i++) {
            int trancheQty;
            if (i == weights.length - 1) {
                trancheQty = qty - allocatedSoFar; // Lô cuối bù tròn
            } else {
                trancheQty = ((int) Math.round(qty * weights[i]) / 100) * 100;
                allocatedSoFar += trancheQty;
            }

            BigDecimal limitPrice = price.multiply(BigDecimal.valueOf(1.000 + (i * 0.003))).setScale(0, RoundingMode.HALF_UP);

            tranches.add(ExecutionAlgoDto.OrderTranche.builder()
                .trancheIndex(i + 1)
                .timeWindow(windows[i])
                .trancheQuantity(trancheQty)
                .targetPriceLimit(limitPrice)
                .volumeWeightPercent(BigDecimal.valueOf(weights[i] * 100).setScale(1, RoundingMode.HALF_UP))
                .stealthReason(tactics[i])
                .build());
        }

        // Tính toán độ trượt giá tiết kiệm được
        // Mua MP một cục làm giá đội lên ~1.2% - 1.8%
        BigDecimal slippageNoAlgo = BigDecimal.valueOf(1.45);
        // Dùng VWAP chẻ lệnh thông minh chỉ trượt ~0.08%
        BigDecimal slippageWithAlgo = BigDecimal.valueOf(0.08);

        BigDecimal grossOrderValue = price.multiply(BigDecimal.valueOf(qty));
        BigDecimal slippageSavedPercent = slippageNoAlgo.subtract(slippageWithAlgo).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal costSavings = grossOrderValue.multiply(slippageSavedPercent).setScale(0, RoundingMode.HALF_UP);

        String note = String.format(
            "THUẬT TOÁN %s: Chẻ nhỏ %d cp %s thành %d đợt gom rải đều theo phân bổ thanh khoản thị trường. Tiết kiệm ước tính ~%s đ chi phí trượt giá so với đặt lệnh thị trường thô bạo!",
            algo, qty, symbol.toUpperCase(), tranches.size(), costSavings.toPlainString()
        );

        return ExecutionAlgoDto.builder()
            .symbol(symbol.toUpperCase())
            .totalOrderQuantity(qty)
            .marketPrice(price)
            .algorithmType(algo)
            .estimatedSlippageNoAlgo(slippageNoAlgo)
            .estimatedSlippageWithAlgo(slippageWithAlgo)
            .estimatedCostSavings(costSavings)
            .scheduledTranches(tranches)
            .executionStrategyNote(note)
            .build();
    }
}
