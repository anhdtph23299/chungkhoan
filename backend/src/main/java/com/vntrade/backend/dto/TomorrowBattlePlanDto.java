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
public class TomorrowBattlePlanDto {

    private LocalDateTime generatedAt;
    private String sessionTitle;
    private String marketRegime;
    private String marketRegimeDisplay;
    private BigDecimal vnIndexPrice;
    private BigDecimal vnIndexChangePercent;
    private BigDecimal vn30FuturesPrice;
    private BigDecimal vn30Basis;
    private String smartMoneyFootprint;
    private String masterStrategyVerdict;

    // Nguồn lực tài khoản hiện tại
    private BigDecimal currentNav;
    private BigDecimal availableCash;
    private BigDecimal cashRatioPercent;
    private int openEquityPositionsCount;
    private int openFuturesPositionsCount;

    // Mũi nhọn 1: Động cơ Phái sinh VN30F1M T+0 (Kiếm tiền tươi ngay trong ngày)
    private FuturesT0Plan futuresT0Plan;

    // Mũi nhọn 2: Top Siêu cổ phiếu Kháng Bão (Relative Strength Leaders)
    private List<TacticalStockPick> antiMarketLeaders;

    // Mũi nhọn 3: Top Cổ phiếu Bắt Đáy Hoảng Loạn Rút Chân (Oversold Spring)
    private List<TacticalStockPick> oversoldBouncePicks;

    // Kế hoạch xử lý từng vị thế đang nắm giữ trong danh mục
    private List<PortfolioPositionAction> openPositionActions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FuturesT0Plan {
        private String symbol;
        private String activeSignal; // LONG, SHORT, STANDBY
        private String strategyName;
        private int confidenceScore;
        private BigDecimal currentPrice;
        private BigDecimal basis;
        private BigDecimal vwap;
        private double rsi;
        private double macdHist;
        private BigDecimal entryPrice;
        private BigDecimal stopLossPrice;
        private BigDecimal takeProfit1;
        private BigDecimal takeProfit2;
        private BigDecimal expectedProfit2ContractsVnd;
        private String longScenarioTrigger;
        private String shortScenarioTrigger;
        private String activePositionSummary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TacticalStockPick {
        private String symbol;
        private String category; // ANTI_MARKET_LEADER, OVERSOLD_SPRING
        private BigDecimal currentPrice;
        private BigDecimal changePercent;
        private BigDecimal alphaVsVnIndex;
        private BigDecimal rsi;
        private int score;
        private BigDecimal entryPrice;
        private BigDecimal stopLoss;
        private BigDecimal harvest6PercentPrice;
        private BigDecimal targetPrice;
        private int suggestedShares;
        private BigDecimal suggestedBudgetVnd;
        private String catalystReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortfolioPositionAction {
        private String symbol;
        private int quantity;
        private BigDecimal entryPrice;
        private BigDecimal currentPrice;
        private BigDecimal pnlVnd;
        private BigDecimal pnlPercent;
        private String strategy;
        private BigDecimal stopLoss;
        private BigDecimal harvest6PercentPrice;
        private BigDecimal takeProfit;
        private String settlementStatus;
        private String actionInstruction;
    }
}
