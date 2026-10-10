package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.entity.BotDecisionAudit;
import com.vntrade.backend.repository.BotDecisionAuditRepository;
import com.vntrade.backend.service.marketdata.CandleDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotDecisionAuditService {

    private final BotDecisionAuditRepository auditRepository;
    private final CandleDataService candleDataService;

    /**
     * Ghi nhận một quyết định của robot vào DB (kể cả khi không vào lệnh)
     */
    public BotDecisionAudit recordDecision(String symbol, String decision, BigDecimal price,
                                          String filter, String reason, Integer confidenceScore) {
        BotDecisionAudit audit = BotDecisionAudit.builder()
            .timestamp(LocalDateTime.now())
            .symbol(symbol.toUpperCase().trim())
            .decision(decision)
            .priceAtDecision(price)
            .rejectedByFilter(filter)
            .rejectionReason(reason)
            .confidenceScore(confidenceScore)
            .auditVerdict("PENDING")
            .build();

        BotDecisionAudit saved = auditRepository.save(audit);
        log.info("📋 [BOT AUDIT LOG] Ghi nhận quyết định: {} cho mã {} | Tầng lọc: {} | Giá: {} đ | Lý do: {}",
            decision, symbol, filter != null ? filter : "NONE", price, reason);
        return saved;
    }

    /**
     * Tự động quét và đối soát giá 5 và 10 phiên sau để kiểm định tính đúng đắn của quyết định
     */
    @Scheduled(cron = "0 0 16 * * MON-FRI") // Chạy cuối ngày lúc 16:00 sau phiên giao dịch
    public void auditPendingDecisions() {
        List<BotDecisionAudit> pendingList = auditRepository.findByAuditVerdict("PENDING");
        if (pendingList.isEmpty()) return;

        log.info("🔍 [ĐỐI SOÁT AUDIT] Bắt đầu kiểm định {} quyết định đang chờ dữ liệu tương lai...", pendingList.size());

        for (BotDecisionAudit audit : pendingList) {
            try {
                processSingleAudit(audit);
            } catch (Exception e) {
                log.debug("Lỗi đối soát cho audit ID {}: {}", audit.getId(), e.getMessage());
            }
        }
    }

    public void processSingleAudit(BotDecisionAudit audit) {
        if (audit.getPriceAtDecision() == null || audit.getPriceAtDecision().compareTo(BigDecimal.ZERO) <= 0) return;

        LocalDate decisionDate = audit.getTimestamp().toLocalDate();
        List<Candle> candles = candleDataService.getHistoricalCandles(audit.getSymbol(), 180);
        if (candles == null || candles.isEmpty()) return;

        // Sắp xếp nến tăng dần theo thời gian
        List<Candle> sorted = candles.stream()
            .sorted(Comparator.comparing(Candle::getDate))
            .toList();

        int decisionIdx = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (!sorted.get(i).getDate().isBefore(decisionDate)) {
                decisionIdx = i;
                break;
            }
        }

        if (decisionIdx == -1) return;

        BigDecimal entry = audit.getPriceAtDecision();

        // 1. Kiểm tra mốc 5 phiên sau
        if (decisionIdx + 5 < sorted.size()) {
            Candle candle5 = sorted.get(decisionIdx + 5);
            BigDecimal p5 = candle5.getClose();
            audit.setPriceAfter5Sessions(p5);
            BigDecimal ret5 = p5.subtract(entry)
                .divide(entry, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
            audit.setReturn5SessionsPct(ret5);

            // 2. Kiểm tra mốc 10 phiên sau nếu có
            if (decisionIdx + 10 < sorted.size()) {
                Candle candle10 = sorted.get(decisionIdx + 10);
                BigDecimal p10 = candle10.getClose();
                audit.setPriceAfter10Sessions(p10);
                BigDecimal ret10 = p10.subtract(entry)
                    .divide(entry, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
                audit.setReturn10SessionsPct(ret10);
            }

            // Đánh giá kết quả của quyết định từ chối:
            if ("REJECTED_FILTER".equalsIgnoreCase(audit.getDecision())) {
                if (ret5.compareTo(BigDecimal.ZERO) < 0) {
                    // Cổ phiếu giảm giá sau đó -> Bot từ chối đúng, cứu tài khoản khỏi lỗ!
                    audit.setAuditVerdict("CORRECT_REJECTION_SAVED_CAPITAL");
                } else if (ret5.compareTo(BigDecimal.valueOf(5.0)) >= 0) {
                    // Cổ phiếu tăng mạnh >= +5% -> Bot từ chối sai (False Negative), bỏ lỡ sóng!
                    audit.setAuditVerdict("MISSED_OPPORTUNITY_FALSE_NEGATIVE");
                } else {
                    audit.setAuditVerdict("NEUTRAL_REJECTION");
                }
            } else if ("BUY_EXECUTED".equalsIgnoreCase(audit.getDecision())) {
                if (ret5.compareTo(BigDecimal.ZERO) > 0) {
                    audit.setAuditVerdict("PROFITABLE_ENTRY");
                } else {
                    audit.setAuditVerdict("LOSS_MAKING_ENTRY");
                }
            }

            auditRepository.save(audit);
            log.info("✅ [ĐỐI SOÁT HOÀN TẤT] Audit ID {} ({}): Sau 5 phiên giá = {} đ ({:+,.2f}%) -> Phán quyết: {}",
                audit.getId(), audit.getSymbol(), p5, ret5, audit.getAuditVerdict());
        }
    }

    /**
     * Lấy báo cáo thống kê đối soát toàn diện
     */
    public Map<String, Object> getAuditSummary() {
        List<BotDecisionAudit> all = auditRepository.findAll();
        long total = all.size();
        long rejected = all.stream().filter(a -> "REJECTED_FILTER".equalsIgnoreCase(a.getDecision())).count();
        long correctRejection = all.stream().filter(a -> "CORRECT_REJECTION_SAVED_CAPITAL".equalsIgnoreCase(a.getAuditVerdict())).count();
        long missedOpportunity = all.stream().filter(a -> "MISSED_OPPORTUNITY_FALSE_NEGATIVE".equalsIgnoreCase(a.getAuditVerdict())).count();
        long pending = all.stream().filter(a -> "PENDING".equalsIgnoreCase(a.getAuditVerdict())).count();

        List<Object[]> rawFilters = auditRepository.countRejectionsByFilter();
        Map<String, Long> filterCounts = new LinkedHashMap<>();
        for (Object[] row : rawFilters) {
            filterCounts.put((String) row[0], (Long) row[1]);
        }

        List<BotDecisionAudit> recent = auditRepository.findTop50ByOrderByTimestampDesc();

        double correctRate = (correctRejection + missedOpportunity) > 0
            ? ((double) correctRejection / (correctRejection + missedOpportunity)) * 100.0
            : 0.0;

        return Map.of(
            "totalDecisions", total,
            "totalRejections", rejected,
            "correctRejections", correctRejection,
            "missedOpportunities", missedOpportunity,
            "correctRejectionRatePct", Math.round(correctRate * 10.0) / 10.0,
            "pendingAudits", pending,
            "rejectionsByFilter", filterCounts,
            "recentAudits", recent
        );
    }
}
