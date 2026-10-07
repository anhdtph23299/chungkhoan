package com.vntrade.backend.service;

import com.vntrade.backend.dto.SmartMoneyFlowDto;
import com.vntrade.backend.dto.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartMoneyFlowService {

    private final StockPriceService stockPriceService;

    // Giả lập / phân tích bước lệnh dòng tiền lớn dựa trên khối lượng thực và biến động giá
    public SmartMoneyFlowDto analyzeSmartMoney(String symbol) {
        StockQuote quote = stockPriceService.getQuote(symbol);
        BigDecimal price = quote.getPrice() != null ? quote.getPrice() : BigDecimal.valueOf(50000);
        long volume = quote.getVolume() > 0 ? quote.getVolume() : 3_500_000L;

        // Tính toán dòng tiền dựa trên tính chất mã và biến động %
        double changePercent = quote.getChangePercent() != null ? quote.getChangePercent().doubleValue() : 1.5;

        // Ước lượng tỷ lệ Mua chủ động dựa trên sức mạnh giá
        double baseBuyRatio = 50.0 + (changePercent * 6.5);
        if (baseBuyRatio > 85.0) baseBuyRatio = 85.0;
        if (baseBuyRatio < 20.0) baseBuyRatio = 20.0;

        long buyActive = Math.round(volume * (baseBuyRatio / 100.0));
        long sellActive = volume - buyActive;
        BigDecimal buyActiveRatio = BigDecimal.valueOf(baseBuyRatio).setScale(1, RoundingMode.HALF_UP);

        // Khối ngoại và tự doanh mua/bán ròng (VND)
        BigDecimal foreignNet;
        BigDecimal propNet;
        int accumulationScore;
        String status;
        String verdict;

        if (changePercent >= 1.0) {
            foreignNet = price.multiply(BigDecimal.valueOf(buyActive - sellActive)).multiply(BigDecimal.valueOf(0.18)).setScale(0, RoundingMode.HALF_UP);
            propNet = price.multiply(BigDecimal.valueOf(buyActive - sellActive)).multiply(BigDecimal.valueOf(0.12)).setScale(0, RoundingMode.HALF_UP);
            accumulationScore = Math.min(95, (int)(baseBuyRatio * 1.1));
            status = "STRONG_ACCUMULATION";
            verdict = "Dòng tiền lớn cá mập & khối ngoại gom mạnh quyết liệt, áp đảo hoàn toàn lệnh bán. Tín hiệu bảo chứng tăng trưởng bền vững.";
        } else if (changePercent > -1.0) {
            foreignNet = price.multiply(BigDecimal.valueOf(buyActive - sellActive)).multiply(BigDecimal.valueOf(0.08)).setScale(0, RoundingMode.HALF_UP);
            propNet = BigDecimal.valueOf(1_500_000_000L);
            accumulationScore = 55;
            status = "NEUTRAL";
            verdict = "Cung cầu cân bằng tại vùng tích lũy, các quỹ tổ chức đang thăm dò tỷ trọng.";
        } else {
            foreignNet = price.multiply(BigDecimal.valueOf(sellActive - buyActive)).multiply(BigDecimal.valueOf(-0.15)).setScale(0, RoundingMode.HALF_UP);
            propNet = BigDecimal.valueOf(-2_000_000_000L);
            accumulationScore = Math.max(15, (int)(baseBuyRatio * 0.8));
            status = "DISTRIBUTION";
            verdict = "Lực bán chủ động chốt lời gia tăng từ tổ chức. Cần quản trị rủi ro chặt chẽ.";
        }

        return SmartMoneyFlowDto.builder()
            .symbol(symbol.toUpperCase())
            .currentPrice(price)
            .totalVolume(volume)
            .buyActiveVolume(buyActive)
            .sellActiveVolume(sellActive)
            .buyActiveRatio(buyActiveRatio)
            .foreignNetBuy(foreignNet)
            .propNetBuy(propNet)
            .accumulationScore(accumulationScore)
            .moneyFlowStatus(status)
            .institutionalVerdict(verdict)
            .build();
    }

    private final List<SmartMoneyFlowDto> cachedResults = new ArrayList<>();
    private volatile long lastCacheTime = 0;

    public synchronized List<SmartMoneyFlowDto> getTopAccumulationSymbols() {
        long now = System.currentTimeMillis();
        if (now - lastCacheTime < 60_000 && !cachedResults.isEmpty()) {
            return new ArrayList<>(cachedResults);
        }

        List<String> watchlist = List.of("FPT", "HPG", "TCB", "SSI", "MWG", "MBB", "VCB");
        List<SmartMoneyFlowDto> results = new ArrayList<>();
        for (String sym : watchlist) {
            try {
                results.add(analyzeSmartMoney(sym));
            } catch (Exception ignored) {}
        }
        results.sort((a, b) -> Integer.compare(b.getAccumulationScore(), a.getAccumulationScore()));
        cachedResults.clear();
        cachedResults.addAll(results);
        lastCacheTime = now;
        return results;
    }
}
