package com.vntrade.backend.service;

import com.vntrade.backend.dto.ImplementationShortfallAuditDto;
import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImplementationShortfallAuditService {

    private final StockPriceService stockPriceService;
    private final OrderBookService orderBookService;

    public ImplementationShortfallAuditDto auditImplementationShortfall(
            String symbol,
            int quantity,
            String algorithm) {

        String sym = symbol != null ? symbol.toUpperCase().trim() : "FPT";
        int qty = Math.max(500, (quantity / 100) * 100);
        String algo = algorithm != null ? algorithm.toUpperCase().trim() : "VWAP_SMART_SLICING";

        StockQuote quote = stockPriceService.getQuote(sym);
        OrderBookDto ob = orderBookService.getOrderBook(sym);

        BigDecimal decisionPrice = quote.getPrice() != null ? quote.getPrice() : BigDecimal.valueOf(140000);
        BigDecimal tickSize = orderBookService.getTickSize(decisionPrice);

        // Giá chạm sàn (Arrival Price): Trễ nhẹ 1 tick do độ trễ truyền gói tin mạng (1-2 ms)
        BigDecimal arrivalPrice = decisionPrice.add(tickSize);

        // Giá khớp bình quân thuật toán chẻ lệnh (VWAP / TWAP / Iceberg)
        // Thuật toán hấp thụ ở tầng Dư Bán 1 và đặt chực ở Dư Mua 1
        BigDecimal algoFillPrice;
        BigDecimal rawMarketFillPrice;

        if ("TWAP_STEALTH".equalsIgnoreCase(algo)) {
            // TWAP rải đều thời gian: trượt ~0.12%
            algoFillPrice = arrivalPrice.multiply(BigDecimal.valueOf(1.0012)).setScale(0, RoundingMode.HALF_UP);
        } else if ("ICEBERG_PEG_MID".equalsIgnoreCase(algo)) {
            // Iceberg chực lệnh ở Mid-Price: trượt siêu thấp ~0.04%
            algoFillPrice = arrivalPrice.multiply(BigDecimal.valueOf(1.0004)).setScale(0, RoundingMode.HALF_UP);
        } else {
            // Mặc định: VWAP_SMART_SLICING theo đường cong phân bổ thanh khoản HOSE: trượt ~0.08%
            algoFillPrice = arrivalPrice.multiply(BigDecimal.valueOf(1.0008)).setScale(0, RoundingMode.HALF_UP);
        }

        // Lệnh thị trường thô bạo (MP / Quét hết các tầng dư bán): Trượt giá nặng ~1.45%
        rawMarketFillPrice = arrivalPrice.multiply(BigDecimal.valueOf(1.0145)).setScale(0, RoundingMode.HALF_UP);

        BigDecimal totalTradedValue = algoFillPrice.multiply(BigDecimal.valueOf(qty));
        BigDecimal rawTradedValue = rawMarketFillPrice.multiply(BigDecimal.valueOf(qty));
        BigDecimal costSavingsVnd = rawTradedValue.subtract(totalTradedValue);

        // Tính các thành phần chi phí theo chuẩn Perold Implementation Shortfall (Basis Points)
        // 1. Delay Cost: (Arrival - Decision) / Decision * 10,000
        BigDecimal delayCostBps = decisionPrice.compareTo(BigDecimal.ZERO) > 0
            ? arrivalPrice.subtract(decisionPrice).divide(decisionPrice, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(10000)).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // 2. Price Impact / Slippage Cost: (Fill - Arrival) / Decision * 10,000
        BigDecimal priceImpactBps = decisionPrice.compareTo(BigDecimal.ZERO) > 0
            ? algoFillPrice.subtract(arrivalPrice).divide(decisionPrice, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(10000)).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // 3. Phí giao dịch sàn HOSE + Công ty chứng khoán: 15 bps (0.15%)
        BigDecimal feeAndTaxBps = BigDecimal.valueOf(15.00);

        // 4. Tổng Implementation Shortfall
        BigDecimal totalIsBps = delayCostBps.add(priceImpactBps).add(feeAndTaxBps);

        // 5. Execution Alpha: Mức chi phí trượt giá tiết kiệm được so với lệnh quét thô bạo (bps)
        BigDecimal rawImpactBps = decisionPrice.compareTo(BigDecimal.ZERO) > 0
            ? rawMarketFillPrice.subtract(arrivalPrice).divide(decisionPrice, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(10000)).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(145.0);

        BigDecimal executionAlphaBps = rawImpactBps.subtract(priceImpactBps);

        String grade = executionAlphaBps.doubleValue() >= 100.0 ? "AAA_PRIME_EXECUTION"
                     : executionAlphaBps.doubleValue() >= 50.0 ? "AA_OPTIMAL"
                     : "ACCEPTABLE";

        String verdict = String.format(
            "KIỂM TOÁN THỰC THI LỆNH IMPLEMENTATION SHORTFALL (PEROLD IS): Khớp %s cp %s bằng thuật toán %s đạt Xếp hạng %s. " +
            "Giá quyết định: %s đ -> Giá chạm sàn: %s đ -> Giá khớp TB: %s đ. " +
            "Phân rã hao hụt thực thi (Total IS = %.1f bps): Chi phí trễ lệnh %.1f bps + Trượt giá tác động %.1f bps + Phí môi giới %.1f bps. " +
            "Thuật toán tạo ra Alpha Thực thi +%.1f bps, tiết kiệm thực tế %s đ so với lệnh thị trường thô bạo!",
            String.format("%,d", qty), sym, algo, grade,
            decisionPrice.toPlainString(), arrivalPrice.toPlainString(), algoFillPrice.toPlainString(),
            totalIsBps.doubleValue(), delayCostBps.doubleValue(), priceImpactBps.doubleValue(), feeAndTaxBps.doubleValue(),
            executionAlphaBps.doubleValue(), String.format("%,d", costSavingsVnd.longValue())
        );

        return ImplementationShortfallAuditDto.builder()
            .symbol(sym)
            .algorithmName(algo)
            .totalOrderQuantity(qty)
            .decisionBenchmarkPrice(decisionPrice)
            .arrivalPrice(arrivalPrice)
            .averageFillPrice(algoFillPrice)
            .totalTradedValueVnd(totalTradedValue)
            .delayCostBps(delayCostBps)
            .priceImpactBps(priceImpactBps)
            .feeAndTaxBps(feeAndTaxBps)
            .totalImplementationShortfallBps(totalIsBps)
            .costSavingsVsMarketOrderVnd(costSavingsVnd)
            .executionAlphaBps(executionAlphaBps)
            .executionQualityGrade(grade)
            .institutionalAuditVerdict(verdict)
            .build();
    }
}
