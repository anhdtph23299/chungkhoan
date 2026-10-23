import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BackendStockScanResult {
  symbol: string;
  name: string;
  exchange: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
  volumeRatio: number;
  rsi: number;
  ma20: number;
  ma50: number;
  ma200?: number;
  signalType: string;
  signalTitle: string;
  signalDescription?: string;
  buyZone: string;
  stopLoss: number;
  targetPrice: number;
  confidenceScore: number;
  action: 'STRONG_BUY' | 'BUY' | 'WATCH' | 'AVOID' | string;
}

export interface VcpPattern {
  symbol: string;
  isVcpForming: boolean;
  numberOfContractions: number;
  contractionPercents: number[];
  volumeDryUpConfirmed: boolean;
  currentPrice: number;
  pivotPrice: number;
  suggestedStopLoss: number;
  targetPrice: number;
  riskRewardRatio: number;
  patternStatus: string;
  analysisNote: string;
}

@Injectable({ providedIn: 'root' })
export class AnalysisService {
  private baseUrl = 'http://localhost:8085/api/analysis';

  constructor(private http: HttpClient) {}

  scanStocks(strategy = 'ALL'): Observable<BackendStockScanResult[]> {
    return this.http.get<BackendStockScanResult[]>(`${this.baseUrl}/scan?strategy=${strategy}`);
  }

  evaluateSymbol(symbol: string): Observable<BackendStockScanResult> {
    return this.http.get<BackendStockScanResult>(`${this.baseUrl}/symbol/${symbol}`);
  }

  scanVcp(): Observable<VcpPattern[]> {
    return this.http.get<VcpPattern[]>(`${this.baseUrl}/vcp`);
  }

  getMarketRegime(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/market-regime`);
  }

  getCanslimForSymbol(symbol: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/canslim/${symbol}`);
  }
}
