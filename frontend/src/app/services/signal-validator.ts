import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SignalValidationTrade {
  id: number;
  symbol: string;
  signalDate: string;
  entryPrice: number;
  stopLoss: number;
  takeProfit: number;
  confidenceScore: number;
  signalReason: string;
  outcome: string; // PENDING | WIN | LOSS | STOPPED_OUT | TIMEOUT
  currentPnlPercent: number;
  currentPrice: number;
  mfePercent: number;
  maePercent: number;
  daysTracked: number;
  closedDate: string;
}

export interface ValidatorStats {
  totalSignals: number;
  pendingCount: number;
  closedCount: number;
  winCount: number;
  lossCount: number;
  winRate: number;
  expectancy: number;
  avgWinPercent: number;
  avgLossPercent: number;
  universeSize: number;
  lastScanTime: string;
  // Realtime pending metrics
  pendingGreenCount: number;
  pendingRedCount: number;
  pendingRefCount: number;
  pendingWinRate: number;
  pendingAvgPnl: number;
  bestPerformer: string;
  worstPerformer: string;
}

@Injectable({ providedIn: 'root' })
export class SignalValidatorService {
  private api = 'http://localhost:8085/api/validator';

  constructor(private http: HttpClient) {}

  getStats(): Observable<ValidatorStats> {
    return this.http.get<ValidatorStats>(`${this.api}/stats`);
  }

  getSignals(): Observable<SignalValidationTrade[]> {
    return this.http.get<SignalValidationTrade[]>(`${this.api}/signals`);
  }

  getLogs(): Observable<string[]> {
    return this.http.get<string[]>(`${this.api}/logs`);
  }

  scanNow(): Observable<ValidatorStats> {
    return this.http.post<ValidatorStats>(`${this.api}/scan`, {});
  }

  reset(): Observable<any> {
    return this.http.delete(`${this.api}/reset`);
  }
}
