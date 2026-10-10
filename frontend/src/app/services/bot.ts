import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BreakoutItem {
  symbol: string;
  wallPrice: number;
  wallVolume: number;
  wallProportionPercent: number;
  currentPrice: number;
  distancePercent: number;
  confidenceScore: number;
  status: string;
  updatedAt: string;
}

export interface PeriodComparisonItem {
  periodLabel: string;
  baselineDate: string;
  nav: number;
  deltaNavVnd: number;
  deltaNavPercent: number;
  winRate: number;
  totalTrades: number;
  performanceVerdict: string;
}

export interface MonthlyPerformanceItem {
  monthLabel: string;
  startNav: number;
  endNav: number;
  netProfitVnd: number;
  returnPercent: number;
  winRate: number;
  tradesCount: number;
  statusBadge: string;
}

export interface PerformanceComparison {
  asOfDate: string;
  currentNav: number;
  initialCapital: number;
  totalProfitVnd: number;
  totalProfitPercent: number;
  currentWinRate: number;
  currentTotalTrades: number;
  openPositionsCount: number;
  oneDayAgo: PeriodComparisonItem;
  oneWeekAgo: PeriodComparisonItem;
  oneMonthAgo: PeriodComparisonItem;
  threeMonthsAgo: PeriodComparisonItem;
  monthlyBreakdown: MonthlyPerformanceItem[];
  sharpeRatio: number;
  maxDrawdownPercent: number;
  profitFactor: number;
  hedgeFundRating: string;
  executiveSummary: string;
}

export interface PreMarketStockWatch {
  symbol: string;
  sector: string;
  referencePrice: number;
  focusReason: string;
  triggerAction: string;
}

export interface PreMarketSentiment {
  checkedAt: string;
  brentPrice: number;
  brentChangePercent: number;
  wtiPrice: number;
  wtiChangePercent: number;
  oilMarketStatus: string;
  usMarketSentiment: string;
  asiaMarketSentiment: string;
  foreignFlowYesterdayBillionVnd: number;
  openingMarketSentiment: string;
  sentimentScore: number;
  hypothesisVerdict: string;
  recommendedOpeningTactic: string;
  priorityWatchlist: PreMarketStockWatch[];
}

export interface OversoldCandidate {
  symbol: string;
  sector: string;
  currentPrice: number;
  changePercent: number;
  rsi14: number;
  bollingerLower: number;
  sma200: number;
  distanceToSma200Percent: number;
  divergenceSignal: string;
  bounceScore: number;
  suggestedTacticalAllocationPercent: number;
  tacticalStopLoss: number;
  tacticalTargetPrice: number;
  riskRewardRatio: number;
  executionTactic: string;
}

export interface OversoldBounce {
  scanTime: string;
  totalSymbolsScanned: number;
  oversoldCandidatesCount: number;
  marketPanicStatus: string;
  macroVerdict: string;
  candidates: OversoldCandidate[];
}

export interface BotStatus {
  running: boolean;
  mode: string;
  capital: number;
  todayRealizedPnl: number;
  todayTradesCount: number;
  dailyTarget: number;
  circuitBreakerLimit: number;
  recentLogs: string[];
  breakoutQueue?: BreakoutItem[];
  marketDataStatus?: 'REAL' | 'STALE';
  isDataFeedHealthy?: boolean;
  targetExpectancy?: number;
  targetWinRate?: number;
  targetSharpe?: number;
  maxDrawdownThreshold?: number;
  riskPerTradePercent?: number;
  cycleLabel?: string;
}

export interface BotConfig {
  isRunning: boolean;
  mode: string;
  accountCapital: number;
  dailyProfitTarget: number;
  dailyMaxLossLimit: number;
  maxRiskPerTradePercent: number;
  stopLossPercent: number;
  partialProfitThresholdPercent: number;
  fullTakeProfitPercent: number;
  trailingStopThresholdPercent: number;
  sectorCapPercent: number;
  maxConcurrentPositions: number;
  scanIntervalSeconds: number;
}

export interface RrgItem {
  symbol: string;
  name: string;
  sector: string;
  currentPrice: number;
  changePercent: number;
  quadrant: 'LEADING' | 'WEAKENING' | 'LAGGING' | 'IMPROVING';
  headingAngle: number;
  headingDirection: string;
  rotationalVelocity: number;
  distanceToCenter: number;
  institutionalAction: string;
  convictionScore: number;
  qualitativeComment: string;
  currentPoint?: {
    rsRatio: number;
    rsMomentum: number;
  };
}

export interface RrgData {
  benchmark: string;
  analysisDate: string;
  items: RrgItem[];
  leadingCount: number;
  weakeningCount: number;
  laggingCount: number;
  improvingCount: number;
  rotationVerdict: string;
  dominantQuadrant: string;
  topLeadingSectors: string;
  toxicLaggingSectors: string;
}

export interface SignalScreener {
  symbol: string;
  price: number;
  changePercent: number;
  signalTitle: string;
  signalDescription: string;
  confidenceScore: number;
  canslimGrade: string;
  action: string;
  targetPrice: number;
  stopLoss: number;
  riskRewardRatio: number;
}

@Injectable({ providedIn: 'root' })
export class BotService {
  private apiUrl = 'http://localhost:8085/api/bot';
  private analysisUrl = 'http://localhost:8085/api/analysis';
  private streamUrl = 'http://localhost:8085/api/stream/events';

  constructor(
    private http: HttpClient
  ) {}

  getBotStatus(): Observable<BotStatus> {
    return this.http.get<BotStatus>(`${this.apiUrl}/status`);
  }

  getBotConfig(): Observable<BotConfig> {
    return this.http.get<BotConfig>(`${this.apiUrl}/config`);
  }

  toggleBot(enable: boolean): Observable<{ running: boolean; message: string }> {
    return this.http.post<{ running: boolean; message: string }>(`${this.apiUrl}/toggle?enable=${enable}`, {});
  }

  setupPaperTrading(capital: number = 20000000): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/setup-paper-trading?capital=${capital}`, {});
  }

  triggerCycle(): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/trigger-cycle`, {});
  }

  getBotSignals(): Observable<SignalScreener[]> {
    return this.http.get<SignalScreener[]>(`${this.apiUrl}/signals`);
  }

  getSectorsRrg(): Observable<RrgData> {
    return this.http.get<RrgData>(`${this.analysisUrl}/rrg/sectors`);
  }

  getVn30Rrg(): Observable<RrgData> {
    return this.http.get<RrgData>(`${this.analysisUrl}/rrg/vn30`);
  }

  harvestProfit(symbol: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/harvest/${symbol}`, {});
  }

  getBreakoutQueue(): Observable<BreakoutItem[]> {
    return this.http.get<BreakoutItem[]>(`${this.apiUrl}/breakout-queue`);
  }

  getPerformanceComparison(): Observable<PerformanceComparison> {
    return this.http.get<PerformanceComparison>('http://localhost:8085/api/portfolio/performance-comparison');
  }

  getPreMarketSentiment(): Observable<PreMarketSentiment> {
    return this.http.get<PreMarketSentiment>(`${this.analysisUrl}/pre-market-sentiment`);
  }

  getOversoldBounce(): Observable<OversoldBounce> {
    return this.http.get<OversoldBounce>(`${this.analysisUrl}/oversold-bounce`);
  }

  /**
   * Kết nối Server-Sent Events (SSE) để cập nhật log và lệnh tức thì theo thời gian thực
   */
  createEventSource(): Observable<{ event: string; data: any }> {
    return new Observable(observer => {
      let eventSource: EventSource | null = null;
      let reconnectTimer: any = null;

      const connect = () => {
        eventSource = new EventSource(this.streamUrl);

        eventSource.onopen = () => {
          console.log('✅ SSE Stream connected to VNTrade Pro backend');
        };

        eventSource.onmessage = (event) => {
          try {
            const parsed = JSON.parse(event.data);
            observer.next({ event: 'message', data: parsed });
          } catch (e) {
            observer.next({ event: 'message', data: event.data });
          }
        };

        eventSource.addEventListener('CONNECTED', (event: any) => {
          try {
            observer.next({ event: 'CONNECTED', data: JSON.parse(event.data) });
          } catch (e) {
            observer.next({ event: 'CONNECTED', data: event.data });
          }
        });

        eventSource.addEventListener('BOT_LOG', (event: any) => {
          try {
            observer.next({ event: 'BOT_LOG', data: JSON.parse(event.data) });
          } catch (e) {
            observer.next({ event: 'BOT_LOG', data: event.data });
          }
        });

        eventSource.addEventListener('BOT_TRADE_OPENED', (event: any) => {
          try {
            observer.next({ event: 'BOT_TRADE_OPENED', data: JSON.parse(event.data) });
          } catch (e) {
            observer.next({ event: 'BOT_TRADE_OPENED', data: event.data });
          }
        });

        eventSource.addEventListener('BOT_TRADE_CLOSED', (event: any) => {
          try {
            observer.next({ event: 'BOT_TRADE_CLOSED', data: JSON.parse(event.data) });
          } catch (e) {
            observer.next({ event: 'BOT_TRADE_CLOSED', data: event.data });
          }
        });

        eventSource.onerror = (error) => {
          console.debug('SSE stream status check...', error);
          if (eventSource && eventSource.readyState === EventSource.CLOSED) {
            eventSource.close();
            if (!reconnectTimer) {
              reconnectTimer = setTimeout(() => {
                reconnectTimer = null;
                connect();
              }, 3000);
            }
          }
        };
      };

      connect();

      return () => {
        if (reconnectTimer) clearTimeout(reconnectTimer);
        if (eventSource) eventSource.close();
      };
    });
  }
}
