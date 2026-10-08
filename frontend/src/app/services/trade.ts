import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Trade {
  id?: number;
  symbol: string;
  exchange: string;
  type: string;
  tradeDate: string;
  price: number;
  quantity: number;
  fee: number;
  strategy: string;
  stopLoss?: number;
  takeProfit?: number;
  sector?: string;
  reason?: string;
  notes?: string;
  closeDate?: string;
  closePrice?: number;
  closeQuantity?: number;
  status: string;
  pnl?: number;
  pnlPercent?: number;
  createdAt?: string;
}

export interface PortfolioSummary {
  totalInvested: number;
  currentValue: number;
  totalPnl: number;
  totalPnlPercent: number;
  openPositions: number;
  totalTrades: number;
  winningTrades: number;
  winRate: number;
}

@Injectable({ providedIn: 'root' })
export class TradeService {
  private baseUrl = 'http://localhost:8085/api/trades';

  constructor(private http: HttpClient) {}

  getAllTrades(): Observable<Trade[]> {
    return this.http.get<Trade[]>(this.baseUrl);
  }

  getTradeById(id: number): Observable<Trade> {
    return this.http.get<Trade>(`${this.baseUrl}/${id}`);
  }

  createTrade(trade: Partial<Trade>): Observable<Trade> {
    return this.http.post<Trade>(this.baseUrl, trade);
  }

  updateTrade(id: number, trade: Partial<Trade>): Observable<Trade> {
    return this.http.put<Trade>(`${this.baseUrl}/${id}`, trade);
  }

  closeTrade(id: number, data: Partial<Trade>): Observable<Trade> {
    return this.http.post<Trade>(`${this.baseUrl}/${id}/close`, data);
  }

  deleteTrade(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  getPortfolioSummary(): Observable<PortfolioSummary> {
    return this.http.get<PortfolioSummary>(`${this.baseUrl}/summary`);
  }
}
