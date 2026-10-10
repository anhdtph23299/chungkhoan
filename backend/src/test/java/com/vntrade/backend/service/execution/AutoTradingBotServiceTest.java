package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.*;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vntrade.backend.service.calculation.RelativeRotationGraphService;
import com.vntrade.backend.service.portfolio.AlertService;
import com.vntrade.backend.service.calculation.GarchVolatilityForecastService;
import com.vntrade.backend.service.marketdata.SseStreamService;
import com.vntrade.backend.service.decision.StrategyService;
import com.vntrade.backend.service.decision.OrderBookImbalanceService;
import com.vntrade.backend.service.calculation.LiquidityAdjustedReturnService;
import com.vntrade.backend.service.risk.ForeignFlowRiskService;
import com.vntrade.backend.service.decision.MicrostructureSpoofingDetectorService;
import com.vntrade.backend.service.marketdata.StockPriceService;
import com.vntrade.backend.service.risk.RiskService;
import com.vntrade.backend.service.decision.MultiTimeframeConfluenceService;
import com.vntrade.backend.service.risk.MarketCrashProtectionService;
import com.vntrade.backend.service.decision.CanslimRatingService;
import com.vntrade.backend.service.decision.SmartMoneyFlowService;
import com.vntrade.backend.service.calculation.KalmanFilterTrendService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AutoTradingBotServiceTest {

    @Mock
    private StrategyService strategyService;
    @Mock
    private RiskService riskService;
    @Mock
    private TradeService tradeService;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private AlertService alertService;
    @Mock
    private StockPriceService stockPriceService;
    @Mock
    private SseStreamService sseStreamService;
    @Mock
    private SmartMoneyFlowService smartMoneyFlowService;
    @Mock
    private MarketCrashProtectionService crashProtectionService;
    @Mock
    private MultiTimeframeConfluenceService confluenceService;
    @Mock
    private CanslimRatingService canslimRatingService;
    @Mock
    private OrderBookImbalanceService orderBookImbalanceService;
    @Mock
    private ForeignFlowRiskService foreignFlowRiskService;
    @Mock
    private MicrostructureSpoofingDetectorService spoofingDetectorService;
    @Mock
    private KalmanFilterTrendService kalmanFilterTrendService;
    @Mock
    private GarchVolatilityForecastService garchVolatilityForecastService;
    @Mock
    private AdaptivePositionSizingService adaptivePositionSizingService;
    @Mock
    private LiquidityAdjustedReturnService liquidityAdjustedReturnService;
    @Mock
    private RelativeRotationGraphService relativeRotationGraphService;
    @Mock
    private BotDecisionAuditService botDecisionAuditService;

    @InjectMocks
    private AutoTradingBotService botService;

    @BeforeEach
    void setUp() {
        when(spoofingDetectorService.detectSpoofing(anyString())).thenReturn(
            SpoofingDetectorDto.builder().isSafeToBuy(true).build()
        );
        when(kalmanFilterTrendService.analyzeKalmanTrend(anyString())).thenReturn(
            KalmanFilterTrendDto.builder().trendRegime("MODERATE_UPTREND").priceVelocity(BigDecimal.valueOf(150)).build()
        );
        when(garchVolatilityForecastService.forecastVolatility(anyString())).thenReturn(
            GarchVolatilityForecastDto.builder().dynamicT25StopLossPercent(BigDecimal.valueOf(5.0)).build()
        );
        when(adaptivePositionSizingService.calculateAdaptiveSize(anyString(), any())).thenReturn(
            AdaptivePositionSizingDto.builder()
                .adaptiveSharesToBuy(500)
                .adaptiveAllocationPercent(BigDecimal.valueOf(10.0))
                .actualAllocationPercent(BigDecimal.valueOf(10.0))
                .atrPercent(BigDecimal.valueOf(1.5))
                .currentRegime("BULL")
                .volatilityEnvironment("NORMAL")
                .riskWarning("✅ Các điều kiện an toàn đã được kiểm tra.")
                .build()
        );

        when(liquidityAdjustedReturnService.calculateLiquidityAdjustedReturn(anyString(), anyInt(), any(), any(), any())).thenReturn(
            LiquidityAdjustedReturnDto.builder()
                .symbol("FPT")
                .isLiquidEnough(true)
                .liquidityTier("TIER_1_BLUE_CHIP")
                .adtvShares(BigDecimal.valueOf(5_000_000))
                .bidAskSpreadPct(BigDecimal.valueOf(0.05))
                .totalSlippagePct(BigDecimal.valueOf(0.12))
                .liquidityPenaltyPct(BigDecimal.valueOf(0.53))
                .netReturnPct(BigDecimal.valueOf(8.50))
                .recommendedLotSize(10_000)
                .executionStrategy("MARKET_ORDER")
                .build()
        );

        when(relativeRotationGraphService.calculateSingleStockRrg(anyString())).thenReturn(
            RrgItemDto.builder()
                .symbol("FPT")
                .quadrant("LEADING")
                .headingDirection("NORTHEAST")
                .headingAngle(BigDecimal.valueOf(45.0))
                .rotationalVelocity(BigDecimal.valueOf(1.8))
                .convictionScore(92)
                .currentPoint(RrgPointDto.builder().rsRatio(BigDecimal.valueOf(104.5)).rsMomentum(BigDecimal.valueOf(102.8)).build())
                .build()
        );

        // Market is safe
        when(crashProtectionService.evaluateMarketCircuitBreaker()).thenReturn(
            MarketCrashProtectionDto.builder()
                .allowNewPurchases(true)
                .defenseLevel(0)
                .defenseStatus("NORMAL")
                .circuitBreakerMessage("Thị trường ổn định")
                .build()
        );

        when(stockPriceService.isMarketDataConnected()).thenReturn(true);

        when(stockPriceService.getQuote(anyString())).thenReturn(
            StockQuote.builder()
                .symbol("FPT")
                .price(BigDecimal.valueOf(140000))
                .volume(3000000L)
                .changePercent(BigDecimal.valueOf(1.5))
                .dataSource("REAL")
                .source("VNDIRECT")
                .build()
        );

        when(orderBookImbalanceService.analyzeMicrostructure(anyString())).thenReturn(
            OrderBookImbalanceDto.builder()
                .symbol("FPT")
                .orderBookImbalanceRatio(BigDecimal.valueOf(0.15))
                .wallDetected("NONE")
                .wallProportionPercent(BigDecimal.ZERO)
                .build()
        );

        when(foreignFlowRiskService.evaluateForeignFlowRisk(anyString())).thenReturn(
            ForeignFlowRiskDto.builder()
                .symbol("FPT")
                .slippageRiskIndex("LOW")
                .fiiFlowVerdict("STRONG_FOREIGN_NET_ACCUMULATION")
                .build()
        );

        when(riskService.getDynamicRiskPercent()).thenReturn(BigDecimal.valueOf(1.75));
        when(riskService.calculatePositionSize(any())).thenReturn(
            PositionSizingResult.builder().acceptable(true).maxSharesToBuy(1000).riskRewardRatio(BigDecimal.valueOf(2.5)).build()
        );
        when(riskService.isSectorAllocationAllowed(anyString(), any(), any())).thenReturn(true);
        when(smartMoneyFlowService.analyzeSmartMoney(anyString())).thenReturn(
            SmartMoneyFlowDto.builder().accumulationScore(75).build()
        );

        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));
        botService.setEnforceMarketHours(false);
    }

    @Test
    void testBotToggleRunning() {
        assertTrue(botService.isRunning());
        botService.setRunning(false);
        assertFalse(botService.isRunning());
        botService.setRunning(true);
        assertTrue(botService.isRunning());
    }

    @Test
    void testSimulateDailyProfitTrade() {
        Trade trade = botService.simulateDailyProfitTrade("FPT");

        assertNotNull(trade);
        assertEquals("FPT", trade.getSymbol());
        assertEquals("closed", trade.getStatus());
        assertTrue(trade.getPnl().compareTo(BigDecimal.ZERO) > 0, "Lợi nhuận chốt lời mô phỏng phải dương");
        assertTrue(botService.getTodayRealizedPnl().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(1, botService.getTodayTradesCount());
    }

    @Test
    void testExecuteBotCycle_StaleMarketDataBlocksBuys() {
        // When market data feed is disconnected or stale
        when(stockPriceService.isMarketDataConnected()).thenReturn(false);

        botService.executeBotCycle();

        // Verify no trades are opened and bot logs STALE alert
        verify(tradeService, never()).createTrade(any());
        assertTrue(botService.getBotLogs().stream().anyMatch(log -> log.contains("MẤT KẾT NỐI DỮ LIỆU THẬT")));
    }

    @Test
    void testExecuteBotCycle_CircuitBreakerBlocksBuys() {
        // When market circuit breaker trips
        when(crashProtectionService.evaluateMarketCircuitBreaker()).thenReturn(
            MarketCrashProtectionDto.builder()
                .allowNewPurchases(false)
                .defenseLevel(2)
                .defenseStatus("DEFCON_1_STORM_LOCKOUT")
                .circuitBreakerMessage("Có hơn 3 mã VN30 giảm sàn trắng bên mua")
                .build()
        );

        botService.executeBotCycle();

        // Verify no trades are opened
        verify(tradeService, never()).createTrade(any());
        assertTrue(botService.getBotLogs().stream().anyMatch(log -> log.contains("DEFCON-1")));
    }

    @Test
    void testExecuteBotCycle_SpoofingBullTrapBlocksBuys() {
        StockScanResult mockScan = StockScanResult.builder()
                .symbol("FPT")
                .exchange("HOSE")
                .action("STRONG_BUY")
                .confidenceScore(85)
                .price(BigDecimal.valueOf(140000))
                .stopLoss(BigDecimal.valueOf(130000))
                .targetPrice(BigDecimal.valueOf(160000))
                .build();
        when(strategyService.scanAllStocks()).thenReturn(List.of(mockScan));
        when(canslimRatingService.rateStock(anyString())).thenReturn(
            CanslimRatingDto.builder().institutionalGrade(true).canslimGrade("A").canslimScore(85).build()
        );
        when(confluenceService.analyzeMultiTimeframe(anyString())).thenReturn(
            MultiTimeframeConfluenceDto.builder().confluenceScore(80).build()
        );
        when(riskService.getDynamicRiskPercent()).thenReturn(BigDecimal.valueOf(1.75));
        when(riskService.calculatePositionSize(any())).thenReturn(
            PositionSizingResult.builder().acceptable(true).maxSharesToBuy(1000).riskRewardRatio(BigDecimal.valueOf(2.5)).build()
        );
        when(riskService.isSectorAllocationAllowed(anyString(), any(), any())).thenReturn(true);
        when(smartMoneyFlowService.analyzeSmartMoney(anyString())).thenReturn(
            SmartMoneyFlowDto.builder().accumulationScore(75).build()
        );

        when(spoofingDetectorService.detectSpoofing(anyString())).thenReturn(
            SpoofingDetectorDto.builder()
                .isSafeToBuy(false)
                .layeringPattern("PHANTOM_BID_SUPPORT_LAYER")
                .spoofingRiskScore(85)
                .build()
        );

        botService.executeBotCycle();

        verify(tradeService, never()).createTrade(any());
        assertTrue(botService.getBotLogs().stream().anyMatch(log -> log.contains("BẪY KÊ MUA ẢO")));
    }

    @Test
    void testExecuteBotCycle_LiquidityTrapBlocksBuys() {
        StockScanResult mockScan = StockScanResult.builder()
                .symbol("XYZ")
                .exchange("UPCOM")
                .action("STRONG_BUY")
                .confidenceScore(85)
                .price(BigDecimal.valueOf(10000))
                .stopLoss(BigDecimal.valueOf(9200))
                .targetPrice(BigDecimal.valueOf(11500))
                .build();
        when(strategyService.scanAllStocks()).thenReturn(List.of(mockScan));
        when(canslimRatingService.rateStock(anyString())).thenReturn(
            CanslimRatingDto.builder().institutionalGrade(true).canslimGrade("A").canslimScore(85).build()
        );
        when(confluenceService.analyzeMultiTimeframe(anyString())).thenReturn(
            MultiTimeframeConfluenceDto.builder().confluenceScore(80).build()
        );
        when(riskService.getDynamicRiskPercent()).thenReturn(BigDecimal.valueOf(1.75));
        when(riskService.calculatePositionSize(any())).thenReturn(
            PositionSizingResult.builder().acceptable(true).maxSharesToBuy(1000).riskRewardRatio(BigDecimal.valueOf(2.5)).build()
        );
        when(riskService.isSectorAllocationAllowed(anyString(), any(), any())).thenReturn(true);
        when(smartMoneyFlowService.analyzeSmartMoney(anyString())).thenReturn(
            SmartMoneyFlowDto.builder().accumulationScore(75).build()
        );

        // Mock illiquid stock
        when(liquidityAdjustedReturnService.calculateLiquidityAdjustedReturn(anyString(), anyInt(), any(), any(), any())).thenReturn(
            LiquidityAdjustedReturnDto.builder()
                .symbol("XYZ")
                .isLiquidEnough(false)
                .liquidityTier("ILLIQUID")
                .adtvShares(BigDecimal.valueOf(30_000))
                .totalSlippagePct(BigDecimal.valueOf(3.5))
                .liquidityPenaltyPct(BigDecimal.valueOf(4.2))
                .netReturnPct(BigDecimal.valueOf(-1.2))
                .build()
        );

        botService.executeBotCycle();

        verify(tradeService, never()).createTrade(any());
        assertTrue(botService.getBotLogs().stream().anyMatch(log -> log.contains("BẪY THANH KHOẢN")));
    }

    @Test
    void testExecuteBotCycle_RrgLaggingSouthwestBlocksBuys() {
        StockScanResult mockScan = StockScanResult.builder()
                .symbol("SAD")
                .exchange("HOSE")
                .action("STRONG_BUY")
                .confidenceScore(85)
                .price(BigDecimal.valueOf(25000))
                .stopLoss(BigDecimal.valueOf(23000))
                .targetPrice(BigDecimal.valueOf(30000))
                .build();
        when(strategyService.scanAllStocks()).thenReturn(List.of(mockScan));
        when(canslimRatingService.rateStock(anyString())).thenReturn(
            CanslimRatingDto.builder().institutionalGrade(true).canslimGrade("A").canslimScore(85).build()
        );
        when(confluenceService.analyzeMultiTimeframe(anyString())).thenReturn(
            MultiTimeframeConfluenceDto.builder().confluenceScore(80).build()
        );

        // Mock RRG Lagging Southwest (toxic trap)
        when(relativeRotationGraphService.calculateSingleStockRrg(anyString())).thenReturn(
            RrgItemDto.builder()
                .symbol("SAD")
                .quadrant("LAGGING")
                .headingDirection("SOUTHWEST")
                .headingAngle(BigDecimal.valueOf(225.0))
                .rotationalVelocity(BigDecimal.valueOf(2.5))
                .convictionScore(18)
                .currentPoint(RrgPointDto.builder().rsRatio(BigDecimal.valueOf(94.2)).rsMomentum(BigDecimal.valueOf(93.1)).build())
                .build()
        );

        botService.executeBotCycle();

        verify(tradeService, never()).createTrade(any());
        assertTrue(botService.getBotLogs().stream().anyMatch(log -> log.contains("BẪY TỤT HẬU RRG")));
    }
}