package com.vntrade.backend.service.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.dto.StockQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@Slf4j
public class CandleDataService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StockPriceService stockPriceService;

    // Cache historical candles thread-safe cho xử lý song song
    private final Map<String, List<Candle>> candleCache = new java.util.concurrent.ConcurrentHashMap<>();

    public CandleDataService(StockPriceService stockPriceService) {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(4000);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper();
        this.stockPriceService = stockPriceService;
    }

    /**
     * Lấy danh sách nến lịch sử (mặc định 120 phiên giao dịch gần nhất)
     */
    public List<Candle> getHistoricalCandles(String symbol, int days) {
        String sym = symbol.toUpperCase().trim();
        String cacheKey = sym + "_" + days;

        if (candleCache.containsKey(cacheKey)) {
            return candleCache.get(cacheKey);
        }

        // 1. Thử lấy dữ liệu nến thật từ VNDirect Dchart API
        List<Candle> candles = fetchFromVndirect(sym, days);

        // 2. Thử TCBS nếu VNDirect lỗi
        if (candles.isEmpty()) {
            candles = fetchFromTcbs(sym, days);
        }

        // 3. Fallback đọc từ kho dữ liệu nến thật cục bộ (Historical CSV 2020-2026)
        if (candles.isEmpty()) {
            candles = fetchFromLocalCsv(sym, days);
        }

        // 4. Fallback nến mô phỏng cuối cùng nếu không có file CSV cục bộ
        if (candles.isEmpty()) {
            log.warn("Using simulated candles fallback for {} (external APIs and local CSV unavailable)", sym);
            candles = generateRealisticHistoricalCandles(sym, days);
        }

        candleCache.put(cacheKey, candles);
        return candles;
    }

    /**
     * Lấy dữ liệu nến lịch sử THẬT 100% từ sàn qua VNDirect Dchart API
     */
    private List<Candle> fetchFromVndirect(String symbol, int days) {
        try {
            long toTimestamp = System.currentTimeMillis() / 1000;
            long fromTimestamp = toTimestamp - ((long) days * 24 * 3600 * 7 / 5 + 30 * 86400L);
            String url = String.format(
                "https://dchart-api.vndirect.com.vn/dchart/history?resolution=D&symbol=%s&from=%d&to=%d",
                symbol, fromTimestamp, toTimestamp
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)");
            headers.set("Accept", "*/*");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if ("ok".equalsIgnoreCase(root.path("s").asText())) {
                    JsonNode tArr = root.path("t");
                    JsonNode cArr = root.path("c");
                    JsonNode oArr = root.path("o");
                    JsonNode hArr = root.path("h");
                    JsonNode lArr = root.path("l");
                    JsonNode vArr = root.path("v");

                    int size = tArr.size();
                    if (size >= 10) {
                        List<Candle> result = new ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            long unixSec = tArr.get(i).asLong();
                            LocalDate d = Instant.ofEpochSecond(unixSec).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
                            double c = cArr.get(i).asDouble();
                            double o = oArr.get(i).asDouble();
                            double h = hArr.get(i).asDouble();
                            double l = lArr.get(i).asDouble();
                            long v = vArr.get(i).asLong();

                            // VNDirect dchart price scale: giá < 1000 là đơn vị nghìn đồng (ví dụ 62.1 -> 62,100 đ)
                            double scale = (c > 0 && c < 1000) ? 1000.0 : 1.0;

                            result.add(Candle.builder()
                                .symbol(symbol)
                                .date(d)
                                .open(BigDecimal.valueOf(o * scale).setScale(0, RoundingMode.HALF_UP))
                                .high(BigDecimal.valueOf(h * scale).setScale(0, RoundingMode.HALF_UP))
                                .low(BigDecimal.valueOf(l * scale).setScale(0, RoundingMode.HALF_UP))
                                .close(BigDecimal.valueOf(c * scale).setScale(0, RoundingMode.HALF_UP))
                                .volume(v)
                                .build());
                        }
                        log.info("Successfully loaded {} REAL historical candles from VNDirect for {}", result.size(), symbol);
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("VNDirect real candle fetch failed for {}: {}", symbol, e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<Candle> fetchFromTcbs(String symbol, int days) {
        try {
            long toTimestamp = System.currentTimeMillis() / 1000;
            long fromTimestamp = toTimestamp - ((long) days * 24 * 3600 * 2); // nhân 2 trừ ngày nghỉ
            String url = String.format(
                "https://apipubaws.tcbs.com.vn/stock-insight/v1/stock/bars-long-term?ticker=%s&type=stock&resolution=D&from=%d&to=%d",
                symbol, fromTimestamp, toTimestamp
            );

            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 10) {
                    List<Candle> result = new ArrayList<>();
                    for (JsonNode node : data) {
                        String dateStr = node.path("tradingDate").asText("");
                        LocalDate d = dateStr.isEmpty() ? LocalDate.now() : LocalDate.parse(dateStr.substring(0, 10));
                        result.add(Candle.builder()
                            .symbol(symbol)
                            .date(d)
                            .open(BigDecimal.valueOf(node.path("open").asDouble()))
                            .high(BigDecimal.valueOf(node.path("high").asDouble()))
                            .low(BigDecimal.valueOf(node.path("low").asDouble()))
                            .close(BigDecimal.valueOf(node.path("close").asDouble()))
                            .volume(node.path("volume").asLong())
                            .build()
                        );
                    }
                    if (!result.isEmpty()) return result;
                }
            }
        } catch (Exception e) {
            log.debug("TCBS historical candle fetch failed for {}: {}", symbol, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Nạp dữ liệu nến thật từ kho lưu trữ CSV cục bộ (Historical 2020-2026)
     */
    private List<Candle> fetchFromLocalCsv(String symbol, int days) {
        String[] possiblePaths = {
            "backend/data/historical_data/" + symbol + ".csv",
            "data/historical_data/" + symbol + ".csv"
        };

        java.io.File file = null;
        for (String p : possiblePaths) {
            java.io.File f = new java.io.File(p);
            if (f.exists() && f.isFile()) {
                file = f;
                break;
            }
        }

        if (file == null) {
            return Collections.emptyList();
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line = reader.readLine(); // skip header
            List<Candle> allCandles = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    LocalDate d = LocalDate.parse(parts[0].trim());
                    BigDecimal o = new BigDecimal(parts[1].trim());
                    BigDecimal h = new BigDecimal(parts[2].trim());
                    BigDecimal l = new BigDecimal(parts[3].trim());
                    BigDecimal c = new BigDecimal(parts[4].trim());
                    long v = (long) Double.parseDouble(parts[5].trim());

                    allCandles.add(Candle.builder()
                        .symbol(symbol)
                        .date(d)
                        .open(o)
                        .high(h)
                        .low(l)
                        .close(c)
                        .volume(v)
                        .build()
                    );
                }
            }

            if (!allCandles.isEmpty()) {
                int startIdx = Math.max(0, allCandles.size() - days);
                List<Candle> result = new ArrayList<>(allCandles.subList(startIdx, allCandles.size()));
                log.info("Successfully loaded {} historical candles for {} from local CSV: {}", result.size(), symbol, file.getPath());
                return result;
            }
        } catch (Exception e) {
            log.warn("Failed to load local CSV candles for {}: {}", symbol, e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * Thuật toán tạo chuỗi nến lịch sử thực tế dựa trên quy tắc biên độ sàn HOSE (+/- 7%)
     */
    public List<Candle> generateRealisticHistoricalCandles(String symbol, int count) {
        StockQuote quote = stockPriceService != null ? stockPriceService.getQuote(symbol) : null;
        BigDecimal currentPrice = (quote != null && quote.getPrice() != null && quote.getPrice().compareTo(BigDecimal.ZERO) > 0)
            ? quote.getPrice()
            : BigDecimal.valueOf(30000);

        List<Candle> candles = new ArrayList<>();
        Random rand = new Random(symbol.hashCode());
        double price = currentPrice.doubleValue() * 0.75; // bắt đầu từ nền giá cách đây 120 phiên
        LocalDate date = LocalDate.now().minusDays(count * 7 / 5);

        for (int i = 0; i < count; i++) {
            while (date.getDayOfWeek().getValue() >= 6) { // Bỏ qua Thứ 7, Chủ Nhật
                date = date.plusDays(1);
            }

            // Biến động hàng ngày tuân thủ giới hạn trần/sàn +/- 7%
            double dailyReturn = (rand.nextGaussian() * 0.018) + 0.0015; // xu hướng tăng nhẹ
            dailyReturn = Math.max(-0.069, Math.min(0.069, dailyReturn));

            double open = price;
            double close = price * (1 + dailyReturn);
            double high = Math.max(open, close) * (1 + rand.nextDouble() * 0.012);
            double low = Math.min(open, close) * (1 - rand.nextDouble() * 0.012);

            // Volume thông thường 3M - 8M, phiên breakout khối lượng gấp đôi
            long baseVol = 3_500_000L + (long) (rand.nextDouble() * 4_000_000L);
            if (dailyReturn > 0.035) {
                baseVol = (long) (baseVol * 1.85); // Volume nổ khi nến xanh mạnh
            }

            candles.add(Candle.builder()
                .symbol(symbol)
                .date(date)
                .open(BigDecimal.valueOf(open).setScale(0, RoundingMode.HALF_UP))
                .high(BigDecimal.valueOf(high).setScale(0, RoundingMode.HALF_UP))
                .low(BigDecimal.valueOf(low).setScale(0, RoundingMode.HALF_UP))
                .close(BigDecimal.valueOf(close).setScale(0, RoundingMode.HALF_UP))
                .volume(baseVol)
                .build()
            );

            price = close;
            date = date.plusDays(1);
        }

        return candles;
    }
}
