package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreMarketSentimentDto {

    private LocalDateTime checkedAt;
    private BigDecimal brentPrice;
    private BigDecimal brentChangePercent;
    private BigDecimal wtiPrice;
    private BigDecimal wtiChangePercent;
    private String oilMarketStatus;

    private String usMarketSentiment;
    private String asiaMarketSentiment;
    private BigDecimal foreignFlowYesterdayBillionVnd;

    private String openingMarketSentiment;
    private int sentimentScore;

    private String hypothesisVerdict;
    private String recommendedOpeningTactic;

    private List<PreMarketStockWatch> priorityWatchlist;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreMarketStockWatch {
        private String symbol;
        private String sector;
        private BigDecimal referencePrice;
        private String focusReason;
        private String triggerAction;
    }
}
