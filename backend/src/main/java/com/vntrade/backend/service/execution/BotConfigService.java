package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.BotConfigDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotConfigService {

    private final AutoTradingBotService botService;

    private BigDecimal dailyProfitTarget = BigDecimal.valueOf(300_000);
    private BigDecimal dailyMaxLossLimit = BigDecimal.valueOf(400_000);
    private BigDecimal maxRiskPerTradePercent = BigDecimal.valueOf(1.75);
    private BigDecimal stopLossPercent = BigDecimal.valueOf(7.0);
    private BigDecimal partialProfitThresholdPercent = BigDecimal.valueOf(10.0);
    private BigDecimal fullTakeProfitPercent = BigDecimal.valueOf(15.0);
    private BigDecimal trailingStopThresholdPercent = BigDecimal.valueOf(7.0);
    private BigDecimal sectorCapPercent = BigDecimal.valueOf(35.0);
    private int maxConcurrentPositions = 4;
    private int scanIntervalSeconds = 30;

    public BotConfigDto getConfig() {
        BigDecimal cap = botService.getAccountCapital() != null ? botService.getAccountCapital() : BigDecimal.valueOf(20_000_000);

        return BotConfigDto.builder()
            .isRunning(botService.isRunning())
            .mode(botService.getMode())
            .accountCapital(cap)
            .dailyProfitTarget(dailyProfitTarget)
            .dailyMaxLossLimit(dailyMaxLossLimit)
            .maxRiskPerTradePercent(maxRiskPerTradePercent)
            .stopLossPercent(stopLossPercent)
            .partialProfitThresholdPercent(partialProfitThresholdPercent)
            .fullTakeProfitPercent(fullTakeProfitPercent)
            .trailingStopThresholdPercent(trailingStopThresholdPercent)
            .sectorCapPercent(sectorCapPercent)
            .maxConcurrentPositions(maxConcurrentPositions)
            .scanIntervalSeconds(scanIntervalSeconds)
            .build();
    }

    public BotConfigDto updateConfig(BotConfigDto update) {
        if (update.getDailyProfitTarget() != null && update.getDailyProfitTarget().compareTo(BigDecimal.ZERO) > 0) {
            this.dailyProfitTarget = update.getDailyProfitTarget();
        }
        if (update.getMaxRiskPerTradePercent() != null) {
            // Giới hạn an toàn rủi ro tối đa từ 0.5% đến 3.0% NAV
            double r = update.getMaxRiskPerTradePercent().doubleValue();
            if (r >= 0.5 && r <= 3.0) {
                this.maxRiskPerTradePercent = update.getMaxRiskPerTradePercent();
            }
        }
        if (update.getStopLossPercent() != null) {
            double sl = update.getStopLossPercent().doubleValue();
            if (sl >= 4.0 && sl <= 10.0) {
                this.stopLossPercent = update.getStopLossPercent();
            }
        }
        if (update.getSectorCapPercent() != null) {
            double sc = update.getSectorCapPercent().doubleValue();
            if (sc >= 20.0 && sc <= 50.0) {
                this.sectorCapPercent = update.getSectorCapPercent();
            }
        }
        if (update.getMaxConcurrentPositions() >= 1 && update.getMaxConcurrentPositions() <= 8) {
            this.maxConcurrentPositions = update.getMaxConcurrentPositions();
        }

        botService.addLog("⚙️ Cập nhật cấu hình bot: Target ngày " + dailyProfitTarget + " đ | Risk: " + maxRiskPerTradePercent + "% | SL: " + stopLossPercent + "%");
        return getConfig();
    }
}
