package com.vntrade.backend.service.marketdata;

import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.OrderBookDto.OrderBookLevel;
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
public class OrderBookService {

    private final StockPriceService stockPriceService;

    /**
     * Mô phỏng độ sâu sổ lệnh 3 bước giá chuẩn sàn HOSE (Khớp lệnh liên tục)
     */
    public OrderBookDto getOrderBook(String symbol) {
        String sym = symbol.toUpperCase().trim();
        StockQuote quote = stockPriceService.getQuote(sym);
        BigDecimal currentPrice = (quote.getPrice() != null && quote.getPrice().compareTo(BigDecimal.ZERO) > 0)
            ? quote.getPrice()
            : BigDecimal.valueOf(30000);

        // Biên độ trần sàn HOSE 7%
        BigDecimal refPrice = quote.getOpen() != null ? quote.getOpen() : currentPrice;
        BigDecimal ceiling = refPrice.multiply(BigDecimal.valueOf(1.07)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal floor = refPrice.multiply(BigDecimal.valueOf(0.93)).setScale(0, RoundingMode.HALF_UP);

        // Bước giá sàn HOSE:
        // Giá < 10.000: bước 10đ
        // 10.000 - 49.950: bước 50đ
        // >= 50.000: bước 100đ
        BigDecimal tickSize = getTickSize(currentPrice);

        // 3 mức Dư Mua (Bids): Giá 1 = currentPrice hoặc currentPrice - 1 tick
        List<OrderBookLevel> bids = new ArrayList<>();
        bids.add(OrderBookLevel.builder()
            .level(1)
            .price(currentPrice)
            .volume(125_400L)
            .build());
        bids.add(OrderBookLevel.builder()
            .level(2)
            .price(currentPrice.subtract(tickSize))
            .volume(248_900L)
            .build());
        bids.add(OrderBookLevel.builder()
            .level(3)
            .price(currentPrice.subtract(tickSize.multiply(BigDecimal.valueOf(2))))
            .volume(412_000L)
            .build());

        // 3 mức Dư Bán (Asks): Giá 1 = currentPrice + 1 tick
        List<OrderBookLevel> asks = new ArrayList<>();
        asks.add(OrderBookLevel.builder()
            .level(1)
            .price(currentPrice.add(tickSize))
            .volume(98_200L)
            .build());
        asks.add(OrderBookLevel.builder()
            .level(2)
            .price(currentPrice.add(tickSize.multiply(BigDecimal.valueOf(2))))
            .volume(186_500L)
            .build());
        asks.add(OrderBookLevel.builder()
            .level(3)
            .price(currentPrice.add(tickSize.multiply(BigDecimal.valueOf(3))))
            .volume(310_700L)
            .build());

        // Độ trượt giá ước tính: bước giá / thị giá (%)
        BigDecimal slippage = tickSize.divide(currentPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        // Phân hạng thanh khoản
        long vol = quote.getVolume() != null ? quote.getVolume() : 3_500_000L;
        String grade = vol >= 5_000_000L ? "AAA (Thanh khoản cực cao)"
                     : vol >= 2_000_000L ? "AA (Thanh khoản cao)"
                     : vol >= 500_000L ? "A (Thanh khoản tốt)"
                     : "B (Thanh khoản trung bình)";

        return OrderBookDto.builder()
            .symbol(sym)
            .currentPrice(currentPrice)
            .referencePrice(refPrice)
            .ceilingPrice(ceiling)
            .floorPrice(floor)
            .totalMatchedVolume(vol)
            .estimatedSlippagePercent(slippage)
            .liquidityGrade(grade)
            .bidLevels(bids)
            .askLevels(asks)
            .build();
    }

    public BigDecimal getTickSize(BigDecimal price) {
        if (price.compareTo(BigDecimal.valueOf(10_000)) < 0) {
            return BigDecimal.valueOf(10);
        } else if (price.compareTo(BigDecimal.valueOf(50_000)) < 0) {
            return BigDecimal.valueOf(50);
        } else {
            return BigDecimal.valueOf(100);
        }
    }
}
