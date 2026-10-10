package com.vntrade.backend.service.marketdata;

import com.vntrade.backend.dto.StockQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service lấy giá cổ phiếu thời gian thực từ các nguồn API sàn chứng khoán Việt Nam:
 * 1. VNDirect Finfo API (Primary realtime)
 * 2. VNDirect Dchart API (OHLCV fallback)
 * 3. TCBS Public API (Secondary fallback)
 * 4. SSI Public API (Tertiary fallback)
 * 
 * NGUYÊN TẮC AN TOÀN ĐỊNH LƯỢNG (REALITY-FIRST):
 * - Gắn cờ dataSource = "REAL" cho toàn bộ báo giá lấy thành công từ sàn thật.
 * - Khi toàn bộ API sàn mất kết nối / rút mạng, gắn cờ dataSource = "STALE" hoặc ném trạng thái mất kết nối.
 * - TUYỆT ĐỐI KHÔNG dùng giá tham chiếu giả lập / fake ticks để che giấu lỗi!
 */
@Service
@Slf4j
public class StockPriceService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Cache thread-safe cho xử lý song song nhiều mã
    private final Map<String, StockQuote> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTime = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000; // 1 phút

    public StockPriceService() {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2500);
        requestFactory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper();
    }

    // Constructor dùng cho Mock / Test
    public StockPriceService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Lấy giá cổ phiếu thời gian thực.
     * Luôn gắn cờ dataSource ("REAL" hoặc "STALE").
     */
    public StockQuote getQuote(String symbol) {
        String upperSymbol = symbol.toUpperCase().trim();

        // 0. Kiểm tra cache hợp lệ
        if (isCacheValid(upperSymbol)) {
            StockQuote cached = cache.get(upperSymbol);
            if (cached != null) {
                log.debug("Returning cached [{}] quote for {}", cached.getDataSource(), upperSymbol);
                return cached;
            }
        }

        // 1. Thử VNDirect Finfo API (Nguồn chính thời gian thực)
        StockQuote quote = getQuoteFromVndirect(upperSymbol);
        if (quote != null) {
            cacheQuote(upperSymbol, quote);
            log.info("📊 [REAL] Báo giá {} từ VNDIRECT: {} đ ({}%)", upperSymbol, quote.getPrice(), quote.getChangePercent());
            return quote;
        }

        // 2. Thử VNDirect Dchart (100% dữ liệu giá thật từ sàn)
        quote = getQuoteFromVndirectDchart(upperSymbol);
        if (quote != null) {
            cacheQuote(upperSymbol, quote);
            log.info("📊 [REAL] Báo giá {} từ VNDIRECT_DCHART: {} đ ({}%)", upperSymbol, quote.getPrice(), quote.getChangePercent());
            return quote;
        }

        // 3. Thử TCBS Public API (Nguồn dự phòng thứ 2)
        quote = getQuoteFromTcbs(upperSymbol);
        if (quote != null) {
            cacheQuote(upperSymbol, quote);
            log.info("📊 [REAL] Báo giá {} từ TCBS: {} đ ({}%)", upperSymbol, quote.getPrice(), quote.getChangePercent());
            return quote;
        }

        // 4. Thử SSI Public API (Nguồn dự phòng thứ 3)
        quote = getQuoteFromSsi(upperSymbol);
        if (quote != null) {
            cacheQuote(upperSymbol, quote);
            log.info("📊 [REAL] Báo giá {} từ SSI: {} đ ({}%)", upperSymbol, quote.getPrice(), quote.getChangePercent());
            return quote;
        }

        // 5. TOÀN BỘ NGUỒN CẤP DỮ LIỆU SÀN THẤT BẠI:
        // TUYỆT ĐỐI KHÔNG DÙNG GIÁ GIẢ! Trả về STALE để bot dừng mở lệnh và cảnh báo FE.
        return getStaleOrDisconnectedQuote(upperSymbol);
    }

    private StockQuote getQuoteFromVndirect(String symbol) {
        try {
            String url = "https://finfo-api.vndirect.com.vn/v4/stocks?q=code:" + symbol + "&size=1&fields=code,close,change,pctChange,open,high,low,nmVolume,exchange";
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) return null;
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode s = data.get(0);
                double close = s.path("close").asDouble(0);
                double change = s.path("change").asDouble(0);
                double pct = s.path("pctChange").asDouble(0);
                if (close <= 0) return null;
                return StockQuote.builder()
                    .symbol(symbol)
                    .price(BigDecimal.valueOf(close))
                    .change(BigDecimal.valueOf(change))
                    .changePercent(BigDecimal.valueOf(pct))
                    .open(BigDecimal.valueOf(s.path("open").asDouble(close)))
                    .high(BigDecimal.valueOf(s.path("high").asDouble(close)))
                    .low(BigDecimal.valueOf(s.path("low").asDouble(close)))
                    .volume(s.path("nmVolume").asLong(0))
                    .exchange(s.path("exchange").asText("HOSE"))
                    .source("VNDIRECT")
                    .dataSource("REAL")
                    .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .build();
            }
        } catch (Exception e) {
            log.debug("VNDirect API failed for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    private StockQuote getQuoteFromVndirectDchart(String symbol) {
        try {
            long nowSec = System.currentTimeMillis() / 1000;
            long fromSec = nowSec - (86400 * 7);
            String url = String.format("https://dchart-api.vndirect.com.vn/dchart/history?resolution=D&symbol=%s&from=%d&to=%d",
                symbol, fromSec, nowSec);

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)");
            headers.set(HttpHeaders.ACCEPT, "*/*");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            var resp = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                if ("ok".equalsIgnoreCase(root.path("s").asText())) {
                    JsonNode cArr = root.path("c");
                    JsonNode oArr = root.path("o");
                    JsonNode hArr = root.path("h");
                    JsonNode lArr = root.path("l");
                    JsonNode vArr = root.path("v");
                    int len = cArr.size();
                    if (len > 0) {
                        double closeVal = cArr.get(len - 1).asDouble();
                        double prevClose = len > 1 ? cArr.get(len - 2).asDouble() : oArr.get(len - 1).asDouble();
                        double openVal = oArr.get(len - 1).asDouble();
                        double highVal = hArr.get(len - 1).asDouble();
                        double lowVal = lArr.get(len - 1).asDouble();
                        long vol = vArr.size() > 0 ? vArr.get(len - 1).asLong() : 0L;

                        // Giá nến < 1000 là đơn vị nghìn đồng
                        double scale = (closeVal > 0 && closeVal < 1000) ? 1000.0 : 1.0;
                        double price = closeVal * scale;
                        double change = (closeVal - prevClose) * scale;
                        double changePct = prevClose > 0 ? ((closeVal - prevClose) / prevClose) * 100.0 : 0.0;

                        return StockQuote.builder()
                            .symbol(symbol)
                            .price(BigDecimal.valueOf(price))
                            .change(BigDecimal.valueOf(change))
                            .changePercent(BigDecimal.valueOf(changePct))
                            .open(BigDecimal.valueOf(openVal * scale))
                            .high(BigDecimal.valueOf(highVal * scale))
                            .low(BigDecimal.valueOf(lowVal * scale))
                            .volume(vol)
                            .exchange("HOSE")
                            .source("VNDIRECT_DCHART")
                            .dataSource("REAL")
                            .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            .build();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("VNDirect Dchart quote fetch failed for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    private StockQuote getQuoteFromTcbs(String symbol) {
        try {
            String url = "https://apipubaws.tcbs.com.vn/stock-insight/v1/stock/search?query=" + symbol + "&size=1";
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    JsonNode stock = data.get(0);
                    BigDecimal price = toBigDecimal(stock.path("p").asText("0"));
                    if (price.compareTo(BigDecimal.ZERO) > 0) {
                        return StockQuote.builder()
                            .symbol(symbol)
                            .price(price)
                            .change(toBigDecimal(stock.path("delta").asText("0")))
                            .changePercent(toBigDecimal(stock.path("percentChange").asText("0")))
                            .open(toBigDecimal(stock.path("o").asText("0")))
                            .high(toBigDecimal(stock.path("h").asText("0")))
                            .low(toBigDecimal(stock.path("l").asText("0")))
                            .volume(stock.path("vol").asLong(0))
                            .exchange(stock.path("e").asText("HOSE"))
                            .source("TCBS")
                            .dataSource("REAL")
                            .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            .build();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("TCBS API unavailable for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    private StockQuote getQuoteFromSsi(String symbol) {
        try {
            String url = "https://fc-data.ssi.com.vn/api/v2/Market/StockRealtimeBySymbol?symbols=" + symbol;
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    JsonNode stock = data.get(0);
                    BigDecimal price = toBigDecimal(stock.path("lastPrice").asText("0"));
                    if (price.compareTo(BigDecimal.ZERO) > 0) {
                        return StockQuote.builder()
                            .symbol(symbol)
                            .price(price)
                            .change(toBigDecimal(stock.path("priceChange").asText("0")))
                            .changePercent(toBigDecimal(stock.path("priceChangePercent").asText("0")))
                            .open(toBigDecimal(stock.path("open").asText("0")))
                            .high(toBigDecimal(stock.path("highest").asText("0")))
                            .low(toBigDecimal(stock.path("lowest").asText("0")))
                            .volume(stock.path("totalMatchVolume").asLong(0))
                            .exchange(stock.path("exchange").asText("HOSE"))
                            .source("SSI")
                            .dataSource("REAL")
                            .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            .build();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("SSI API failed for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    /**
     * Khi toàn bộ kết nối sàn mất, trả về dữ liệu STALE.
     * TUYỆT ĐỐI KHÔNG dùng giá tham chiếu giả lập / fake ticks!
     */
    private StockQuote getStaleOrDisconnectedQuote(String symbol) {
        StockQuote cached = cache.get(symbol);
        if (cached != null && cached.getPrice() != null) {
            log.warn("🚨 [DỮ LIỆU STALE] Mất kết nối sàn cho mã {}. Trả về giá cũ từ cache nhưng gắn cờ STALE.", symbol);
            return StockQuote.builder()
                .symbol(symbol)
                .price(cached.getPrice())
                .change(cached.getChange())
                .changePercent(cached.getChangePercent())
                .open(cached.getOpen())
                .high(cached.getHigh())
                .low(cached.getLow())
                .volume(cached.getVolume())
                .exchange(cached.getExchange())
                .source(cached.getSource() + "_STALE")
                .dataSource("STALE")
                .timestamp(cached.getTimestamp())
                .build();
        }

        log.error("🚨 [MẤT KẾT NỐI SÀN] Không thể lấy giá cho mã {} từ bất kỳ API thật nào (VNDirect, TCBS, SSI). Không có cache cũ -> Báo STALE không có giá!", symbol);
        return StockQuote.builder()
            .symbol(symbol)
            .price(null)
            .change(BigDecimal.ZERO)
            .changePercent(BigDecimal.ZERO)
            .open(null)
            .high(null)
            .low(null)
            .volume(0L)
            .exchange("HOSE")
            .source("DISCONNECTED")
            .dataSource("STALE")
            .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .build();
    }

    /**
     * Kiểm tra trạng thái kết nối nguồn dữ liệu thị trường thật
     */
    public boolean isMarketDataConnected() {
        try {
            StockQuote q = getQuote("VNINDEX");
            if (q != null && q.isReal() && q.getPrice() != null && q.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
            StockQuote fpt = getQuote("FPT");
            return fpt != null && fpt.isReal() && fpt.getPrice() != null && fpt.getPrice().compareTo(BigDecimal.ZERO) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private BigDecimal toBigDecimal(String value) {
        try {
            if (value == null || value.isBlank() || value.equals("null")) return BigDecimal.ZERO;
            double d = Double.parseDouble(value);
            if (d > 0 && d < 100) d = d * 1000;
            return BigDecimal.valueOf(d);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private boolean isCacheValid(String symbol) {
        Long time = cacheTime.get(symbol);
        return time != null && (System.currentTimeMillis() - time) < CACHE_TTL_MS;
    }

    /**
     * Hủy hiệu lực cache thời gian để ép buộc quét mới từ sàn
     */
    public void invalidateCache(String symbol) {
        if (symbol != null) {
            cacheTime.remove(symbol.toUpperCase().trim());
        }
    }

    private void cacheQuote(String symbol, StockQuote quote) {
        cache.put(symbol, quote);
        cacheTime.put(symbol, System.currentTimeMillis());
    }

    /**
     * Cập nhật giá thị trường mô phỏng (gắn cờ STALE/SIMULATED để bot không nhầm với sàn thật)
     */
    public StockQuote updateMarketPrice(String symbol, BigDecimal newPrice) {
        String upperSymbol = symbol.toUpperCase().trim();
        StockQuote current = getQuote(upperSymbol);
        BigDecimal oldPrice = (current != null && current.getPrice() != null) ? current.getPrice() : newPrice;
        BigDecimal diff = newPrice.subtract(oldPrice);
        BigDecimal pct = oldPrice.compareTo(BigDecimal.ZERO) > 0
            ? diff.divide(oldPrice, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        StockQuote updated = StockQuote.builder()
            .symbol(upperSymbol)
            .price(newPrice)
            .change(diff)
            .changePercent(pct)
            .open(current != null && current.getOpen() != null ? current.getOpen() : oldPrice)
            .high(current != null && current.getHigh() != null && newPrice.compareTo(current.getHigh()) > 0 ? newPrice : (current != null ? current.getHigh() : newPrice))
            .low(current != null && current.getLow() != null && newPrice.compareTo(current.getLow()) < 0 ? newPrice : (current != null ? current.getLow() : newPrice))
            .volume((current != null && current.getVolume() != null ? current.getVolume() : 1_000_000L) + 50_000L)
            .exchange("HOSE")
            .source("SIMULATED_TICK")
            .dataSource("STALE")
            .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .build();

        cacheQuote(upperSymbol, updated);
        log.info("📊 [STALE/SIMULATED] Giá thị trường {} cập nhật mô phỏng: {} đ ({}%)", upperSymbol, newPrice, pct);
        return updated;
    }
}
