package com.vntrade.backend.service.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.OrderBookDto.OrderBookLevel;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderBookService {

    private final StockPriceService stockPriceService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(2500))
        .build();

    private static final long CACHE_TTL_MS = 15_000L;
    private final Map<String, CachedOrderBook> cache = new ConcurrentHashMap<>();

    private record CachedOrderBook(OrderBookDto dto, long timestampMs) {}

    /**
     * Lấy sổ lệnh Level-2 thật (3 bước giá mua/bán), giá bình quân VWAP và dòng tiền khối ngoại từ 24hMoney API.
     * Nếu trước giờ giao dịch (08:30-09:00) hoặc API lỗi, đánh dấu rõ realOrderBookAvailable = false (không tạo số ảo).
     */
    public OrderBookDto getOrderBook(String symbol) {
        String sym = symbol != null ? symbol.toUpperCase().trim() : "FPT";
        long now = System.currentTimeMillis();
        CachedOrderBook cached = cache.get(sym);
        if (cached != null && (now - cached.timestampMs()) < CACHE_TTL_MS) {
            return cached.dto();
        }

        OrderBookDto realDto = fetchRealOrderBookFrom24hMoney(sym);
        if (realDto != null) {
            cache.put(sym, new CachedOrderBook(realDto, now));
            return realDto;
        }

        OrderBookDto fallback = buildEmptyDepthFallback(sym);
        cache.put(sym, new CachedOrderBook(fallback, now));
        return fallback;
    }

    private OrderBookDto fetchRealOrderBookFrom24hMoney(String sym) {
        try {
            String url = "https://api-finance-t19.24hmoney.vn/v1/ios/stock/detail?symbol=" + sym;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofMillis(2500))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode detail = root.path("data").path("share_detail");
            if (detail.isMissingNode() || detail.isNull()) {
                return null;
            }

            BigDecimal currentPrice = parsePriceVnd(detail, "match_price");
            if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                currentPrice = parsePriceVnd(detail, "basic_price");
            }
            if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                StockQuote q = stockPriceService.getQuote(sym);
                currentPrice = (q != null && q.getPrice() != null && q.getPrice().compareTo(BigDecimal.ZERO) > 0)
                    ? q.getPrice() : BigDecimal.valueOf(30000);
            }

            BigDecimal refPrice = parsePriceVnd(detail, "basic_price");
            if (refPrice.compareTo(BigDecimal.ZERO) <= 0) refPrice = currentPrice;

            BigDecimal ceiling = parsePriceVnd(detail, "ceiling_price");
            if (ceiling.compareTo(BigDecimal.ZERO) <= 0) {
                ceiling = refPrice.multiply(BigDecimal.valueOf(1.07)).setScale(0, RoundingMode.HALF_UP);
            }
            BigDecimal floor = parsePriceVnd(detail, "floor_price");
            if (floor.compareTo(BigDecimal.ZERO) <= 0) {
                floor = refPrice.multiply(BigDecimal.valueOf(0.93)).setScale(0, RoundingMode.HALF_UP);
            }

            BigDecimal avgPrice = parsePriceVnd(detail, "average_price");

            // 24hMoney trả accumylated_vol và bid_qtty/offer_qtty theo đơn vị lô 10 cổ phiếu
            long matchedVol = detail.path("accumylated_vol").asLong(0L) * 10L;
            if (matchedVol <= 0) {
                StockQuote q = stockPriceService.getQuote(sym);
                matchedVol = (q != null && q.getVolume() != null) ? q.getVolume() : 0L;
            }

            long foreignBuy = detail.path("buy_foreign_qtty").asLong(0L);
            long foreignSell = detail.path("sell_foreign_qtty").asLong(0L);
            long foreignNet = foreignBuy - foreignSell;
            long foreignRoom = detail.path("foreign_current_room").asLong(0L);

            List<OrderBookLevel> bids = new ArrayList<>();
            long totalBid = 0L;
            for (int lvl = 1; lvl <= 3; lvl++) {
                BigDecimal p = parsePriceVnd(detail, "bid_price0" + lvl);
                long v = detail.path("bid_qtty0" + lvl).asLong(0L) * 10L;
                if (p.compareTo(BigDecimal.ZERO) > 0 || v > 0) {
                    bids.add(OrderBookLevel.builder().level(lvl).price(p).volume(v).build());
                    totalBid += v;
                }
            }

            List<OrderBookLevel> asks = new ArrayList<>();
            long totalAsk = 0L;
            for (int lvl = 1; lvl <= 3; lvl++) {
                BigDecimal p = parsePriceVnd(detail, "offer_price0" + lvl);
                long v = detail.path("offer_qtty0" + lvl).asLong(0L) * 10L;
                if (p.compareTo(BigDecimal.ZERO) > 0 || v > 0) {
                    asks.add(OrderBookLevel.builder().level(lvl).price(p).volume(v).build());
                    totalAsk += v;
                }
            }

            boolean hasDepth = (totalBid > 0 || totalAsk > 0 || matchedVol > 0);
            String source = hasDepth ? "24HMONEY_REAL" : "PRE_MARKET_NO_DEPTH";

            BigDecimal tickSize = getTickSize(currentPrice);
            BigDecimal slippage = currentPrice.compareTo(BigDecimal.ZERO) > 0
                ? tickSize.divide(currentPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

            return OrderBookDto.builder()
                .symbol(sym)
                .currentPrice(currentPrice)
                .averagePrice(avgPrice)
                .referencePrice(refPrice)
                .ceilingPrice(ceiling)
                .floorPrice(floor)
                .totalMatchedVolume(matchedVol)
                .foreignBuyVolume(foreignBuy)
                .foreignSellVolume(foreignSell)
                .foreignNetVolume(foreignNet)
                .foreignRoomRemaining(foreignRoom)
                .realOrderBookAvailable(totalBid > 0 || totalAsk > 0)
                .dataSource(source)
                .estimatedSlippagePercent(slippage)
                .liquidityGrade(classifyLiquidityGrade(matchedVol))
                .bidLevels(bids)
                .askLevels(asks)
                .build();
        } catch (Exception e) {
            log.debug("24hMoney order book fetch error for {}: {}", sym, e.getMessage());
            return null;
        }
    }

    private OrderBookDto buildEmptyDepthFallback(String sym) {
        StockQuote quote = stockPriceService.getQuote(sym);
        BigDecimal currentPrice = (quote != null && quote.getPrice() != null && quote.getPrice().compareTo(BigDecimal.ZERO) > 0)
            ? quote.getPrice() : BigDecimal.valueOf(30000);
        BigDecimal refPrice = (quote != null && quote.getOpen() != null && quote.getOpen().compareTo(BigDecimal.ZERO) > 0)
            ? quote.getOpen() : currentPrice;
        BigDecimal ceiling = refPrice.multiply(BigDecimal.valueOf(1.07)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal floor = refPrice.multiply(BigDecimal.valueOf(0.93)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal tickSize = getTickSize(currentPrice);
        BigDecimal slippage = tickSize.divide(currentPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        long vol = (quote != null && quote.getVolume() != null) ? quote.getVolume() : 0L;

        return OrderBookDto.builder()
            .symbol(sym)
            .currentPrice(currentPrice)
            .averagePrice(BigDecimal.ZERO)
            .referencePrice(refPrice)
            .ceilingPrice(ceiling)
            .floorPrice(floor)
            .totalMatchedVolume(vol)
            .foreignBuyVolume(0L)
            .foreignSellVolume(0L)
            .foreignNetVolume(0L)
            .foreignRoomRemaining(0L)
            .realOrderBookAvailable(false)
            .dataSource("FALLBACK_NO_DEPTH")
            .estimatedSlippagePercent(slippage)
            .liquidityGrade(classifyLiquidityGrade(vol))
            .bidLevels(List.of())
            .askLevels(List.of())
            .build();
    }

    private BigDecimal parsePriceVnd(JsonNode detail, String field) {
        double raw = detail.path(field).asDouble(0.0);
        if (raw <= 0.0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(raw * 1000.0).setScale(0, RoundingMode.HALF_UP);
    }

    private String classifyLiquidityGrade(long vol) {
        if (vol >= 5_000_000L) return "AAA (Thanh khoản cực cao)";
        if (vol >= 2_000_000L) return "AA (Thanh khoản cao)";
        if (vol >= 500_000L) return "A (Thanh khoản tốt)";
        if (vol > 0L) return "B (Thanh khoản trung bình)";
        return "N/A (Chờ khớp lệnh phiên)";
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
