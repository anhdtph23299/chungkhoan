package com.vntrade.backend.service.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vntrade.backend.dto.OrderBookDto;
import com.vntrade.backend.dto.PreMarketSentimentDto;
import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.service.marketdata.OrderBookService;
import com.vntrade.backend.service.marketdata.StockPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreMarketSentimentService {

    private final StockPriceService stockPriceService;
    private final OrderBookService orderBookService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(3000))
        .build();

    private static final long MACRO_CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final List<String> TOXIC_KEYWORDS = List.of(
        "khởi tố", "bắt tạm giam", "bắt giam", "đình chỉ giao dịch", "hủy niêm yết",
        "kiểm soát đặc biệt", "lỗ lũy kế", "thua lỗ nặng", "vỡ nợ", "phá sản",
        "cắt margin", "thao túng chứng khoán", "thanh tra thuế", "cưỡng chế thuế"
    );

    private volatile long lastRssFetchMs = 0L;
    private volatile List<String> cachedHeadlines = List.of();
    private volatile long lastMacroFetchMs = 0L;
    private volatile MacroSnapshot cachedMacro = null;

    private record YahooQuote(BigDecimal price, BigDecimal changePct) {}
    private record MacroSnapshot(
        BigDecimal brent, BigDecimal brentChg,
        BigDecimal wti, BigDecimal wtiChg,
        BigDecimal djiChg, BigDecimal spxChg,
        boolean live
    ) {}

    /**
     * Quét CafeF RSS xem mã cổ phiếu có tin tức tiêu cực trọng yếu (Risk Veto) không.
     * Chỉ dùng tin xấu để NÉ BẪY rủi ro doanh nghiệp, không dùng tin tốt để FOMO mua.
     */
    public String getToxicNewsReason(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;
        String sym = symbol.toUpperCase().trim();
        List<String> headlines = getRecentHeadlines();
        if (headlines.isEmpty()) return null;

        Pattern symPattern = Pattern.compile("(?<![A-Z0-9])" + Pattern.quote(sym) + "(?![A-Z0-9])");
        for (String title : headlines) {
            if (symPattern.matcher(title).find()) {
                String lower = title.toLowerCase(Locale.ROOT);
                for (String kw : TOXIC_KEYWORDS) {
                    if (lower.contains(kw)) {
                        return "Phát hiện tin rủi ro CafeF [" + kw + "]: " + title;
                    }
                }
            }
        }
        return null;
    }

    public List<String> getRecentHeadlines() {
        long now = System.currentTimeMillis();
        if (!cachedHeadlines.isEmpty() && (now - lastRssFetchMs) < MACRO_CACHE_TTL_MS) {
            return cachedHeadlines;
        }
        List<String> titles = new ArrayList<>();
        for (String rssUrl : List.of(
            "https://cafef.vn/thi-truong-chung-khoan.rss",
            "https://cafef.vn/doanh-nghiep.rss"
        )) {
            titles.addAll(fetchRssTitles(rssUrl));
        }
        if (!titles.isEmpty()) {
            cachedHeadlines = List.copyOf(titles);
            lastRssFetchMs = now;
        }
        return cachedHeadlines;
    }

    private List<String> fetchRssTitles(String url) {
        List<String> titles = new ArrayList<>();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofMillis(3000))
                .GET()
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null) return titles;

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(resp.body().getBytes(StandardCharsets.UTF_8)));
            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < Math.min(items.getLength(), 25); i++) {
                var children = items.item(i).getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    if ("title".equals(children.item(j).getNodeName())) {
                        String t = children.item(j).getTextContent();
                        if (t != null && !t.isBlank()) titles.add(t.trim());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("CafeF RSS fetch error ({}): {}", url, e.getMessage());
        }
        return titles;
    }

    private MacroSnapshot getMacroSnapshot() {
        long now = System.currentTimeMillis();
        if (cachedMacro != null && (now - lastMacroFetchMs) < MACRO_CACHE_TTL_MS) {
            return cachedMacro;
        }
        YahooQuote brent = fetchYahooQuote("BZ=F");
        YahooQuote wti = fetchYahooQuote("CL=F");
        YahooQuote dji = fetchYahooQuote("^DJI");
        YahooQuote spx = fetchYahooQuote("^GSPC");

        boolean live = (brent != null || wti != null || dji != null || spx != null);
        MacroSnapshot snap = new MacroSnapshot(
            brent != null ? brent.price() : BigDecimal.ZERO,
            brent != null ? brent.changePct() : BigDecimal.ZERO,
            wti != null ? wti.price() : BigDecimal.ZERO,
            wti != null ? wti.changePct() : BigDecimal.ZERO,
            dji != null ? dji.changePct() : BigDecimal.ZERO,
            spx != null ? spx.changePct() : BigDecimal.ZERO,
            live
        );
        cachedMacro = snap;
        lastMacroFetchMs = now;
        return snap;
    }

    private YahooQuote fetchYahooQuote(String ticker) {
        try {
            String encoded = ticker.replace("^", "%5E").replace("=", "%3D");
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + encoded + "?interval=1d&range=5d";
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofMillis(3000))
                .GET()
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null) return null;

            JsonNode result0 = objectMapper.readTree(resp.body()).path("chart").path("result").path(0);
            JsonNode meta = result0.path("meta");
            double price = meta.path("regularMarketPrice").asDouble(0.0);

            // Lấy giá đóng cửa phiên liền trước từ mảng nến close (tránh lỗi chartPreviousClose của range=5d lấy giá 5 ngày trước)
            double prevClose = 0.0;
            JsonNode closeArr = result0.path("indicators").path("quote").path(0).path("close");
            if (closeArr.isArray() && closeArr.size() >= 2) {
                List<Double> validCloses = new ArrayList<>();
                for (JsonNode n : closeArr) {
                    if (n != null && !n.isNull() && n.asDouble(0.0) > 0.0) {
                        validCloses.add(n.asDouble());
                    }
                }
                if (validCloses.size() >= 2) {
                    if (price <= 0) price = validCloses.get(validCloses.size() - 1);
                    prevClose = validCloses.get(validCloses.size() - 2);
                }
            }
            if (prevClose <= 0) {
                prevClose = meta.path("previousClose").asDouble(0.0);
            }
            if (prevClose <= 0) {
                prevClose = meta.path("chartPreviousClose").asDouble(0.0);
            }
            if (price <= 0) return null;
            double chgPct = prevClose > 0 ? ((price - prevClose) / prevClose) * 100.0 : 0.0;
            return new YahooQuote(
                BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(chgPct).setScale(2, RoundingMode.HALF_UP)
            );
        } catch (Exception e) {
            log.debug("Yahoo quote fetch error ({}): {}", ticker, e.getMessage());
            return null;
        }
    }

    public PreMarketSentimentDto analyzePreMarketSentiment() {
        LocalDateTime now = LocalDateTime.now();
        String todayStr = now.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        MacroSnapshot macro = getMacroSnapshot();
        BigDecimal brent = macro.brent();
        BigDecimal brentChange = macro.brentChg();
        BigDecimal wti = macro.wti();
        BigDecimal wtiChange = macro.wtiChg();

        String oilStatus = !macro.live() ? "DATA_UNAVAILABLE"
            : (brent.compareTo(BigDecimal.valueOf(90.0)) >= 0
                ? "SURGING_OVER_90_HIGH_ALERT"
                : (brentChange.compareTo(BigDecimal.ZERO) > 0 ? "STEADY_MOMENTUM" : "COOLING_OFF"));

        double avgUsChg = (macro.djiChg().doubleValue() + macro.spxChg().doubleValue()) / 2.0;
        String usSentiment = !macro.live() ? "DATA_UNAVAILABLE"
            : (avgUsChg >= 0.8 ? "RISK_ON_RALLY" : (avgUsChg <= -1.0 ? "RISK_OFF_SELLOFF" : "NEUTRAL_EXPANSION"));
        String asiaSentiment = "CAUTIOUS_MIXED";

        // Tổng hợp dòng tiền ngoại thật từ rổ đại diện VN30 qua OrderBookService
        double netForeignBillion = 0.0;
        for (String leader : List.of("VCB", "CTG", "HPG", "FPT", "VIC", "SSI", "MWG", "VNM")) {
            try {
                OrderBookDto ob = orderBookService.getOrderBook(leader);
                if (ob != null && ob.getCurrentPrice() != null && ob.getForeignNetVolume() != 0) {
                    netForeignBillion += (ob.getCurrentPrice().doubleValue() * ob.getForeignNetVolume()) / 1_000_000_000.0;
                }
            } catch (Exception ignored) {}
        }
        BigDecimal foreignFlow = BigDecimal.valueOf(netForeignBillion).setScale(2, RoundingMode.HALF_UP);

        int sentimentScore = 50;
        if (macro.live()) {
            if (avgUsChg >= 0.8) sentimentScore += 12;
            else if (avgUsChg <= -1.0) sentimentScore -= 15;
            if (netForeignBillion >= 20.0) sentimentScore += 10;
            else if (netForeignBillion <= -50.0) sentimentScore -= 12;
        }
        sentimentScore = Math.max(10, Math.min(90, sentimentScore));
        String openingSentiment = avgUsChg <= -1.2 ? "DEFENSIVE_GAP_ABSORPTION" : "ATO_INERTIA_ABSORPTION";

        List<String> headlines = getRecentHeadlines();
        String topHeadline = headlines.isEmpty() ? "Chưa tải được tin mới CafeF" : headlines.get(0);

        String hypothesisVerdict = String.format(
            "VĨ MÔ THẬT (%s - %s): Brent %s USD (%+.2f%%) | US Dow/SPX %+.2f%% | Khối ngoại rổ VN30 đầu tàu: %+.2f tỷ VND | Tin nổi bật CafeF: \"%s\"",
            todayStr, macro.live() ? "LIVE_YAHOO" : "OFFLINE",
            brent.toPlainString(), brentChange.doubleValue(), avgUsChg, foreignFlow.doubleValue(), topHeadline
        );

        String recommendedTactic =
            "1. TUYỆT ĐỐI KHÔNG MUA ĐUỔI TRONG PHIÊN ATO (09:00 - 09:15) khi sổ lệnh Level-2 chưa ổn định. " +
            "2. KIỂM TRA SỔ LỆNH THẬT & DÒNG TIỀN NGOẠI: Chỉ giải ngân khi không có tường bán đè áp đảo (OBI) và không bị kê mua ảo (Spoofing). " +
            "3. RADAR NÉ TIN XẤU CAFEF: Tự động đưa mã vào danh sách đen nếu phát hiện tin khởi tố/cắt margin/lỗ nặng.";

        List<PreMarketSentimentDto.PreMarketStockWatch> priorityWatchlist = new ArrayList<>();
        priorityWatchlist.add(PreMarketSentimentDto.PreMarketStockWatch.builder()
            .symbol("CTG")
            .sector("NGÂN HÀNG")
            .referencePrice(getSymbolPrice("CTG", BigDecimal.valueOf(31000)))
            .focusReason("Ngân hàng quốc doanh thanh khoản cao, theo dõi lực cầu khối ngoại và nền giá quanh MA20.")
            .triggerAction("Giải ngân khi OBI dương, không dính Spoofing và đạt chuẩn kỹ thuật >= 75đ.")
            .build());
        priorityWatchlist.add(PreMarketSentimentDto.PreMarketStockWatch.builder()
            .symbol("HPG")
            .sector("THÉP & VẬT LIỆU")
            .referencePrice(getSymbolPrice("HPG", BigDecimal.valueOf(21000)))
            .focusReason("Đầu tàu ngành thép thanh khoản top đầu thị trường.")
            .triggerAction("Kích hoạt khi nổ Vol >= 1.25x sau cạn cung và khối ngoại không xả ròng.")
            .build());
        priorityWatchlist.add(PreMarketSentimentDto.PreMarketStockWatch.builder()
            .symbol("FPT")
            .sector("CÔNG NGHỆ")
            .referencePrice(getSymbolPrice("FPT", BigDecimal.valueOf(60000)))
            .focusReason("Bluechip công nghệ nền tảng cơ bản vững, theo dõi điểm cân bằng thanh khoản.")
            .triggerAction("Chờ đồng thuận đa khung giờ MTF & Kalman hướng lên.")
            .build());

        return PreMarketSentimentDto.builder()
            .checkedAt(now)
            .brentPrice(brent)
            .brentChangePercent(brentChange)
            .wtiPrice(wti)
            .wtiChangePercent(wtiChange)
            .oilMarketStatus(oilStatus)
            .usMarketSentiment(usSentiment)
            .asiaMarketSentiment(asiaSentiment)
            .foreignFlowYesterdayBillionVnd(foreignFlow)
            .openingMarketSentiment(openingSentiment)
            .sentimentScore(sentimentScore)
            .hypothesisVerdict(hypothesisVerdict)
            .recommendedOpeningTactic(recommendedTactic)
            .priorityWatchlist(priorityWatchlist)
            .build();
    }

    private BigDecimal getSymbolPrice(String symbol, BigDecimal fallback) {
        try {
            StockQuote q = stockPriceService.getQuote(symbol);
            if (q != null && q.getPrice() != null && q.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                return q.getPrice();
            }
        } catch (Exception ignored) {}
        return fallback;
    }
}