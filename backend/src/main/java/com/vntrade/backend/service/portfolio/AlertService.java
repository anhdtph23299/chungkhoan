package com.vntrade.backend.service.portfolio;

import com.vntrade.backend.entity.Alert;
import com.vntrade.backend.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import com.vntrade.backend.service.marketdata.SseStreamService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final SseStreamService sseStreamService;

    public List<Alert> getAllAlerts() {
        return alertRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Alert> getUnreadAlerts() {
        return alertRepository.findByIsReadFalseOrderByCreatedAtDesc();
    }

    public Alert createAlert(String symbol, String alertType, String title, String message, BigDecimal price, String severity) {
        // Chống spam: Không tạo lặp lại alert cùng loại cho cùng 1 mã trong 15 phút
        java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusMinutes(15);
        boolean existsRecent = alertRepository.findAllByOrderByCreatedAtDesc().stream()
            .anyMatch(a -> a.getSymbol() != null && a.getSymbol().equalsIgnoreCase(symbol)
                        && a.getAlertType() != null && a.getAlertType().equalsIgnoreCase(alertType)
                        && a.getCreatedAt() != null && a.getCreatedAt().isAfter(threshold));
        if (existsRecent) {
            log.debug("AlertService: Bỏ qua tạo alert trùng lặp cho {} ({}) trong vòng 15 phút.", symbol, alertType);
            return null;
        }

        Alert alert = Alert.builder()
            .symbol(symbol.toUpperCase())
            .alertType(alertType)
            .title(title)
            .message(message)
            .priceAtAlert(price)
            .severity(severity)
            .isRead(false)
            .build();
        log.info("Creating [{}] Alert for {}: {}", severity, symbol, title);
        Alert saved = alertRepository.save(alert);
        try {
            sseStreamService.broadcast("ALERT_TRIGGERED", saved);
        } catch (Exception ignored) {}
        return saved;
    }

    public void markAsRead(Long id) {
        alertRepository.findById(id).ifPresent(alert -> {
            alert.setRead(true);
            alertRepository.save(alert);
        });
    }

    public void markAllAsRead() {
        List<Alert> unread = alertRepository.findByIsReadFalseOrderByCreatedAtDesc();
        unread.forEach(a -> a.setRead(true));
        alertRepository.saveAll(unread);
    }

    public void deleteAlert(Long id) {
        alertRepository.deleteById(id);
    }
}