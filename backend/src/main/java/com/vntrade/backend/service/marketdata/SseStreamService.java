package com.vntrade.backend.service.marketdata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SseStreamService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter registerClient() {
        // Timeout 30 minutes
        SseEmitter emitter = new SseEmitter(1800_000L);

        emitters.add(emitter);
        log.info("New SSE client subscribed. Total active listeners: {}", emitters.size());

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("SSE client completed. Active listeners: {}", emitters.size());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
            log.info("SSE client timed out. Active listeners: {}", emitters.size());
        });

        emitter.onError(e -> {
            emitters.remove(emitter);
            log.warn("SSE client error. Active listeners: {}", emitters.size());
        });

        // Send initial handshake
        try {
            emitter.send(SseEmitter.event()
                .name("CONNECTED")
                .data(Map.of(
                    "status", "CONNECTED",
                    "time", LocalDateTime.now().toString(),
                    "message", "Đã kết nối luồng dữ liệu thời gian thực VN Stock Bot"
                )));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcast(String eventName, Object data) {
        if (emitters.isEmpty()) return;

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            } catch (Exception e) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    /**
     * Heartbeat mỗi 15 giây để duy trì kết nối qua reverse proxy và tường lửa
     */
    @Scheduled(fixedDelay = 15000)
    public void sendHeartbeat() {
        if (!emitters.isEmpty()) {
            broadcast("HEARTBEAT", Map.of("timestamp", System.currentTimeMillis()));
        }
    }
}
