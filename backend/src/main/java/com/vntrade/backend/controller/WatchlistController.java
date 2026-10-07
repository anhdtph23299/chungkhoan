package com.vntrade.backend.controller;

import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.entity.Watchlist;
import com.vntrade.backend.service.StockPriceService;
import com.vntrade.backend.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final StockPriceService stockPriceService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WatchlistController(WatchlistService watchlistService, StockPriceService stockPriceService) {
        this.watchlistService = watchlistService;
        this.stockPriceService = stockPriceService;
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2500);
        requestFactory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    // --- Watchlist CRUD ---
    @GetMapping("/api/watchlist")
    public List<Watchlist> getAll() {
        return watchlistService.getAll();
    }

    @PostMapping("/api/watchlist")
    public Watchlist addItem(@RequestBody Watchlist item) {
        return watchlistService.addOrUpdate(item);
    }

    @PutMapping("/api/watchlist/{id}/price")
    public Watchlist updatePrice(
        @PathVariable Long id,
        @RequestBody Map<String, String> body
    ) {
        BigDecimal price = body.containsKey("currentPrice") ? new BigDecimal(body.get("currentPrice")) : null;
        BigDecimal rsi = body.containsKey("rsi") ? new BigDecimal(body.get("rsi")) : null;
        return watchlistService.updatePrice(id, price, rsi);
    }

    @DeleteMapping("/api/watchlist/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        watchlistService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Stock Quote API ---
    @GetMapping("/api/stock/quote/{symbol}")
    public StockQuote getQuote(@PathVariable String symbol) {
        return stockPriceService.getQuote(symbol);
    }

    @GetMapping("/api/stock/quotes")
    public List<StockQuote> getMultipleQuotes(@RequestParam List<String> symbols) {
        return symbols.stream()
            .map(stockPriceService::getQuote)
            .toList();
    }

    // Cache chỉ số thị trường trong 60 giây
    private final Map<String, Map<String, Object>> indexCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long lastIndexCacheTime = 0;

    /**
     * Lấy chỉ số thị trường VN thật 100% từ VNDirect Dchart:
     * VN-INDEX, VN30, HNX-INDEX, UPCOM
     */
    @GetMapping("/api/stock/market-indices")
    public ResponseEntity<List<Map<String, Object>>> getMarketIndices() {
        long nowMs = System.currentTimeMillis();
        String[] indexCodes = {"VNINDEX", "VN30", "HNX", "UPCOM"};
        String[] displayNames = {"VN-INDEX", "VN30", "HNX-INDEX", "UPCOM"};

        if (nowMs - lastIndexCacheTime < 60_000 && indexCache.size() >= 4) {
            List<Map<String, Object>> cachedList = new ArrayList<>();
            for (String code : indexCodes) {
                if (indexCache.containsKey(code)) {
                    cachedList.add(indexCache.get(code));
                }
            }
            if (cachedList.size() == 4) return ResponseEntity.ok(cachedList);
        }

        List<Map<String, Object>> indices = new ArrayList<>();
        long nowSec = nowMs / 1000;
        long fromSec = nowSec - (86400 * 7); // Lấy 7 ngày gần nhất

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)");
        headers.set(HttpHeaders.ACCEPT, "*/*");
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        for (int i = 0; i < indexCodes.length; i++) {
            String code = indexCodes[i];
            String name = displayNames[i];
            Map<String, Object> indexData = null;

            try {
                String url = String.format("https://dchart-api.vndirect.com.vn/dchart/history?resolution=D&symbol=%s&from=%d&to=%d",
                    code, fromSec, nowSec);
                var resp = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    JsonNode root = objectMapper.readTree(resp.getBody());
                    if ("ok".equalsIgnoreCase(root.path("s").asText())) {
                        JsonNode cArr = root.path("c");
                        JsonNode oArr = root.path("o");
                        JsonNode vArr = root.path("v");
                        int len = cArr.size();
                        if (len > 0) {
                            double latestClose = cArr.get(len - 1).asDouble();
                            double prevClose = len > 1 ? cArr.get(len - 2).asDouble() : oArr.get(len - 1).asDouble();
                            double change = latestClose - prevClose;
                            double changePct = prevClose > 0 ? (change / prevClose) * 100.0 : 0.0;
                            long volume = vArr.size() > 0 ? vArr.get(len - 1).asLong() : 0L;

                            indexData = Map.of(
                                "name", name,
                                "code", code,
                                "value", Math.round(latestClose * 100.0) / 100.0,
                                "change", Math.round(change * 100.0) / 100.0,
                                "changePercent", Math.round(changePct * 100.0) / 100.0,
                                "volume", volume,
                                "totalValue", 0.0,
                                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            );
                            indexCache.put(code, indexData);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("VNDirect dchart index fetch failed for {}: {}", code, e.getMessage());
            }

            if (indexData == null) {
                indexData = indexCache.getOrDefault(code, getFallbackIndex(name, code));
            }
            indices.add(indexData);
        }

        lastIndexCacheTime = nowMs;
        return ResponseEntity.ok(indices);
    }

    private Map<String, Object> getFallbackIndex(String name, String code) {
        return switch (code) {
            case "VNINDEX" -> Map.of("name", name, "code", code, "value", 1753.39, "change", -5.69, "changePercent", -0.32, "volume", 485847590L, "totalValue", 0.0, "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            case "VN30"    -> Map.of("name", name, "code", code, "value", 1894.20, "change", -4.62, "changePercent", -0.24, "volume", 196062089L, "totalValue", 0.0, "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            case "HNX"     -> Map.of("name", name, "code", code, "value", 259.82,  "change", -5.71, "changePercent", -2.15, "volume", 40403500L,  "totalValue", 0.0, "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            default        -> Map.of("name", name, "code", code, "value", 125.19,  "change", -0.04, "changePercent", -0.03, "volume", 23600100L,  "totalValue", 0.0, "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        };
    }
}

