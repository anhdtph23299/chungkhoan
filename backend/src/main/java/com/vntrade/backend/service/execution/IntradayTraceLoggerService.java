package com.vntrade.backend.service.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * Bộ ghi nhật ký vi mô Event-Driven Delta Trace (JSONL theo ngày).
 * - Ghi tức thời mọi biến động giá/SL/OBI/quyết định bot ra file backend/logs/trace/trace-YYYY-MM-DD.jsonl
 * - Chống spam trùng lặp (Delta Deduplication) + Heartbeat mỗi 60s -> chỉ ~1-2 MB/ngày (~300 KB sau nén GZIP)
 * - Không lưu vào DB H2 để giữ Database nhẹ và tốc độ truy vấn tức thì.
 */
@Service
@Slf4j
public class IntradayTraceLoggerService {

    private static final Path TRACE_DIR = Paths.get("logs", "trace");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String lastHeartbeatSignature = "";
    private volatile long lastHeartbeatWriteMs = 0L;
    private static final long HEARTBEAT_INTERVAL_MS = 60_000L;

    public IntradayTraceLoggerService() {
        try {
            Files.createDirectories(TRACE_DIR);
        } catch (Exception e) {
            log.warn("Không thể tạo thư mục trace logs {}: {}", TRACE_DIR.toAbsolutePath(), e.getMessage());
        }
    }

    /**
     * Ghi sự kiện quyết định / giao dịch / thay đổi trạng thái (luôn ghi ngay lập tức)
     */
    public synchronized void recordEvent(String eventType, String symbol, Map<String, Object> payload) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ts", LocalDateTime.now().format(TS_FMT));
            entry.put("type", eventType);
            if (symbol != null) entry.put("symbol", symbol);
            if (payload != null) entry.putAll(payload);
            appendJsonLine(LocalDate.now(), entry);
        } catch (Exception e) {
            log.debug("Lỗi ghi trace event: {}", e.getMessage());
        }
    }

    /**
     * Ghi Delta Snapshot theo nhịp quét: chỉ ghi khi giá / PnL / VN-Index / Regime thay đổi,
     * hoặc tối thiểu 1 lần mỗi 60 giây để tiết kiệm 95% dung lượng ổ cứng.
     */
    public synchronized void recordMarketDelta(String signature, Map<String, Object> snapshot) {
        long now = System.currentTimeMillis();
        boolean changed = signature != null && !signature.equals(lastHeartbeatSignature);
        boolean heartbeatDue = (now - lastHeartbeatWriteMs) >= HEARTBEAT_INTERVAL_MS;
        if (!changed && !heartbeatDue) {
            return;
        }
        lastHeartbeatSignature = signature != null ? signature : "";
        lastHeartbeatWriteMs = now;
        recordEvent(changed ? "MARKET_DELTA" : "HEARTBEAT", "PORTFOLIO", snapshot);
    }

    private void appendJsonLine(LocalDate date, Map<String, Object> entry) throws IOException {
        Files.createDirectories(TRACE_DIR);
        Path file = TRACE_DIR.resolve("trace-" + date + ".jsonl");
        String line = objectMapper.writeValueAsString(entry) + System.lineSeparator();
        Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Đọc các dòng trace gần nhất của một ngày để người dùng phân tích rút kinh nghiệm
     */
    public List<Map<String, Object>> readRecentTraces(LocalDate date, int limit) {
        LocalDate target = date != null ? date : LocalDate.now();
        int safeLimit = Math.max(1, Math.min(limit, 500));
        Path file = TRACE_DIR.resolve("trace-" + target + ".jsonl");
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - safeLimit);
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = lines.size() - 1; i >= start; i--) {
                String l = lines.get(i);
                if (l != null && !l.isBlank()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = objectMapper.readValue(l, Map.class);
                    result.add(map);
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("Lỗi đọc file trace {}: {}", file, e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getTraceStorageStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        Path todayFile = TRACE_DIR.resolve("trace-" + LocalDate.now() + ".jsonl");
        long todayBytes = 0L;
        long todayLines = 0L;
        long totalBytes = 0L;
        int fileCount = 0;

        try {
            if (Files.exists(todayFile)) {
                todayBytes = Files.size(todayFile);
                try (var stream = Files.lines(todayFile, StandardCharsets.UTF_8)) {
                    todayLines = stream.count();
                }
            }
            if (Files.exists(TRACE_DIR)) {
                try (var files = Files.list(TRACE_DIR)) {
                    for (Path p : files.toList()) {
                        if (Files.isRegularFile(p)) {
                            fileCount++;
                            totalBytes += Files.size(p);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        stats.put("traceDirectory", TRACE_DIR.toAbsolutePath().toString());
        stats.put("todayFile", todayFile.getFileName().toString());
        stats.put("todayEventsCount", todayLines);
        stats.put("todaySizeKb", Math.round((todayBytes / 1024.0) * 10.0) / 10.0);
        stats.put("totalFiles", fileCount);
        stats.put("totalStorageKb", Math.round((totalBytes / 1024.0) * 10.0) / 10.0);
        return stats;
    }

    /**
     * Tự động nén GZIP các file trace của những ngày trước vào lúc 15:05 chiều (giảm ~85% dung lượng ổ cứng)
     */
    @Scheduled(cron = "0 5 15 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
    public void compressPreviousDayLogs() {
        String todayName = "trace-" + LocalDate.now() + ".jsonl";
        try {
            if (!Files.exists(TRACE_DIR)) return;
            try (var files = Files.list(TRACE_DIR)) {
                for (Path src : files.toList()) {
                    String name = src.getFileName().toString();
                    if (name.endsWith(".jsonl") && !name.equals(todayName)) {
                        Path gzPath = TRACE_DIR.resolve(name + ".gz");
                        try (InputStream in = Files.newInputStream(src);
                             GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(gzPath))) {
                            in.transferTo(out);
                        }
                        Files.deleteIfExists(src);
                        log.info("🗜️ [TRACE ARCHIVE] Đã nén file nhật ký phiên cũ {} -> {}", name, gzPath.getFileName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi nén trace log: {}", e.getMessage());
        }
    }
}
