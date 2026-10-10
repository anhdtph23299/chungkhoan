import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface FuturesQuote {
  symbol: string;
  currentPrice: number;
  change: number;
  pctChange: number;
  vn30IndexPrice: number;
  basis: number;
  basisStatus: string;
  volume: number;
  openInterest: number;
  sessionTime: string;
  timestamp: string;
}

export interface FuturesSignal {
  symbol: string;
  action: 'LONG' | 'SHORT' | 'CLOSE_LONG' | 'CLOSE_SHORT' | 'STANDBY';
  currentPrice: number;
  entryPrice: number;
  stopLossPrice: number;
  takeProfit1: number;
  takeProfit2: number;
  trailingStopPoints: number;
  confidenceScore: number;
  strategyName: string;
  trendStatus: string;
  recommendationReason: string;
  rsi: number;
  macdHist: number;
  vwap: number;
  basis: number;
  generatedAt: string;
}

export interface FuturesPosition {
  id: string;
  symbol: string;
  side: 'LONG' | 'SHORT';
  contracts: number;
  entryPrice: number;
  currentPrice: number;
  stopLossPrice: number;
  takeProfitPrice: number;
  trailingStopPrice?: number;
  pnlPoints: number;
  grossPnlVnd: number;
  feesVnd: number;
  netPnlVnd: number;
  marginUsed: number;
  status: 'OPEN' | 'CLOSED';
  closeReason?: string;
  closePrice?: number;
  openTime: string;
  closeTime?: string;
}

export interface FuturesBotConfig {
  autoTrading: boolean;
  mode: string;
  capital: number;
  maxContracts: number;
  stopLossPoints: number;
  takeProfitPoints: number;
  trailingStopTrigger: number;
  trailingStopDistance: number;
  closeBeforeAtc: boolean;
  preferredStrategy: string;
  todayRealizedPnlVnd: number;
  todayPnlPoints: number;
  todayTradesCount: number;
}

export interface FuturesTradeHistory {
  tradeId: string;
  side: 'LONG' | 'SHORT';
  contracts: number;
  entryPrice: number;
  exitPrice: number;
  pnlPoints: number;
  netPnlVnd: number;
  exitReason: string;
  openTime: string;
  closeTime: string;
  durationMinutes: number;
}

export interface FuturesBacktestResult {
  symbol: string;
  resolution: string;
  testDays: number;
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  winRatePercent: number;
  totalPnlPoints: number;
  totalNetPnlVnd: number;
  maxDrawdownPoints: number;
  maxDrawdownVnd: number;
  profitFactor: number;
  sharpeRatio: number;
  longTrades: number;
  shortTrades: number;
  avgPointsPerTrade: number;
  recentTrades: FuturesTradeHistory[];
}

@Injectable({
  providedIn: 'root'
})
export class FuturesService {
  private apiUrl = 'http://localhost:8085/api/futures';

  constructor(private http: HttpClient) {}

  getQuote(): Observable<FuturesQuote> {
    return this.http.get<FuturesQuote>(`${this.apiUrl}/quote`);
  }

  getSignal(): Observable<FuturesSignal> {
    return this.http.get<FuturesSignal>(`${this.apiUrl}/signal`);
  }

  getOpenPositions(): Observable<FuturesPosition[]> {
    return this.http.get<FuturesPosition[]>(`${this.apiUrl}/positions`);
  }

  getPositionHistory(): Observable<FuturesPosition[]> {
    return this.http.get<FuturesPosition[]>(`${this.apiUrl}/history`);
  }

  openPosition(side: string, contracts: number = 1, entryPrice?: number, stopLoss?: number, takeProfit?: number, reason?: string): Observable<FuturesPosition> {
    let params: any = { side, contracts };
    if (entryPrice) params.entryPrice = entryPrice;
    if (stopLoss) params.stopLoss = stopLoss;
    if (takeProfit) params.takeProfit = takeProfit;
    if (reason) params.reason = reason;
    return this.http.post<FuturesPosition>(`${this.apiUrl}/order`, null, { params });
  }

  closePosition(id: string, closePrice?: number, reason?: string): Observable<FuturesPosition> {
    let params: any = {};
    if (closePrice) params.closePrice = closePrice;
    if (reason) params.reason = reason;
    return this.http.post<FuturesPosition>(`${this.apiUrl}/close/${id}`, null, { params });
  }

  closeAllPositions(reason?: string): Observable<{ message: string }> {
    let params: any = {};
    if (reason) params.reason = reason;
    return this.http.post<{ message: string }>(`${this.apiUrl}/close-all`, null, { params });
  }

  getBotStatus(): Observable<{ config: FuturesBotConfig; logs: string[]; openPositionsCount: number }> {
    return this.http.get<{ config: FuturesBotConfig; logs: string[]; openPositionsCount: number }>(`${this.apiUrl}/bot-status`);
  }

  toggleBot(enable: boolean): Observable<{ autoTrading: boolean; message: string }> {
    return this.http.post<{ autoTrading: boolean; message: string }>(`${this.apiUrl}/bot-toggle`, null, { params: { enable } });
  }

  updateBotConfig(config: Partial<FuturesBotConfig>): Observable<FuturesBotConfig> {
    return this.http.post<FuturesBotConfig>(`${this.apiUrl}/bot-config`, config);
  }

  runBacktest(days: number = 10, stopLoss: number = 2.5, takeProfit: number = 5.0, trailingStop: number = 1.5): Observable<FuturesBacktestResult> {
    return this.http.get<FuturesBacktestResult>(`${this.apiUrl}/backtest`, {
      params: { days, stopLoss, takeProfit, trailingStop }
    });
  }
}
