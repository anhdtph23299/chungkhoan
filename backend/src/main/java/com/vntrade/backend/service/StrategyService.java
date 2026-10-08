package com.vntrade.backend.service;

import com.vntrade.backend.dto.StockQuote;
import com.vntrade.backend.dto.StockScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyService {

    private final StockPriceService stockPriceService;
    private final CandleDataService candleDataService;
    private final QuantitativeStrategyEngine quantitativeStrategyEngine;

    // Danh sách 50 mã cổ phiếu thanh khoản cao nhất thị trường VN (VN30 + Top Midcap)
    public static final List<String> FOCUS_SYMBOLS = List.of(
        // VN30 & Ngân hàng đầu ngành (12 mã)
        "VCB", "BID", "CTG", "TCB", "MBB", "VPB", "ACB", "STB", "HDB", "TPB", "SHB", "LPB",
        // Chứng khoán (6 mã)
        "SSI", "VND", "VCI", "SHS", "HCM", "VIX",
        // Thép & Vật liệu (3 mã)
        "HPG", "HSG", "NKG",
        // Bất động sản & KCN (7 mã)
        "VHM", "VIC", "VRE", "KBC", "IDC", "DIG", "PDR",
        // Bán lẻ & Tiêu dùng (6 mã)
        "MWG", "MSN", "VNM", "SAB", "FRT", "DGW",
        // Công nghệ & Viễn thông (2 mã)
        "FPT", "CMG",
        // Dầu khí & Năng lượng (6 mã)
        "GAS", "PVD", "PVS", "BSR", "PLX", "POW",
        // Hóa chất & Phân bón (3 mã)
        "DGC", "DCM", "DPM",
        // Hàng không, Vận tải biển & Cao su (5 mã)
        "VJC", "GMD", "HAH", "GVR", "BCM"
    );

    // CPU Throttling: Giới hạn cố định tối đa 4 luồng xử lý đồng thời (<= 25% CPU trên máy 12 Cores)
    // để dành 8-9 Cores (75% CPU) cho các ứng dụng và dự án khác của người dùng
    private static final int MAX_CONCURRENT_THREADS = 4;
    private final ExecutorService boundedExecutor = Executors.newFixedThreadPool(
        MAX_CONCURRENT_THREADS,
        r -> {
            Thread t = new Thread(r, "vntrade-scan-worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1); // Ưu tiên thấp hơn để nhường CPU cho IDE/dự án khác
            return t;
        }
    );

    /**
     * Quét song song 50 cổ phiếu theo lô 4 luồng kiểm soát tải CPU
     */
    public List<StockScanResult> scanAllStocks() {
        List<CompletableFuture<StockScanResult>> futures = FOCUS_SYMBOLS.stream()
            .map(symbol -> CompletableFuture.supplyAsync(() -> {
                try {
                    return evaluateSymbol(symbol);
                } catch (Exception e) {
                    log.debug("Lỗi quét mã {}: {}", symbol, e.getMessage());
                    return null;
                }
            }, boundedExecutor))
            .toList();

        return futures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .toList();
    }

    public List<StockScanResult> scanByStrategy(String strategyType) {
        return scanAllStocks().stream()
            .filter(s -> strategyType == null || strategyType.equalsIgnoreCase("ALL") || s.getSignalType().equalsIgnoreCase(strategyType))
            .toList();
    }

    public StockScanResult evaluateSymbol(String symbol) {
        String sym = symbol.toUpperCase().trim();
        StockQuote quote = stockPriceService.getQuote(sym);
        try {
            var candles = candleDataService.getHistoricalCandles(sym, 80);
            return quantitativeStrategyEngine.evaluateRealTimeData(sym, quote, candles);
        } catch (Exception e) {
            log.warn("Falling back to basic evaluation for {}: {}", sym, e.getMessage());
            return quantitativeStrategyEngine.evaluateRealTimeData(sym, quote, List.of());
        }
    }
}
