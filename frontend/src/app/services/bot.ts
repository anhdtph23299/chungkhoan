import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BotStatus {
  running: boolean;
  mode: string;
  capital: number;
  todayRealizedPnl: number;
  todayTradesCount: number;
  dailyTarget: number;
  circuitBreakerLimit: number;
  recentLogs: string[];
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
  private apiUrl = 'http://localhost:8080/api/bot';
  private analysisUrl = 'http://localhost:8080/api/analysis';
  private streamUrl = 'http://localhost:8080/api/stream/events';

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

  /**
   * Kết nối Server-Sent Events (SSE) để cập nhật log và lệnh tức thì theo thời gian thực
   */
  createEventSource(): Observable<{ event: string; data: any }> {
    return new Observable(observer => {
      const eventSource = new EventSource(this.streamUrl);

      eventSource.onmessage = (event) => {
        try {
          const parsed = JSON.parse(event.data);
          observer.next({ event: 'message', data: parsed });
        } catch (e) {
          observer.next({ event: 'message', data: event.data });
        }
      };

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
        console.debug('SSE stream connecting/reconnecting...', error);
      };

      return () => {
        eventSource.close();
      };
    });
  }
}
