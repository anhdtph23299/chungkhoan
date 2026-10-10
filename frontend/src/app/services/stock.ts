import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface StockQuote {
  symbol: string;
  price: number;
  change: number;
  changePercent: number;
  open: number;
  high: number;
  low: number;
  volume: number;
  exchange: string;
  source: string;
  dataSource?: 'REAL' | 'STALE';
  timestamp: string;
}

export interface MarketIndex {
  name: string;
  code: string;
  value: number;
  change: number;
  changePercent: number;
  volume: number;
  totalValue: number;
  timestamp: string;
}

@Injectable({ providedIn: 'root' })
export class StockService {
  private baseUrl = 'http://localhost:8085/api/stock';

  constructor(private http: HttpClient) {}

  getQuote(symbol: string): Observable<StockQuote> {
    return this.http.get<StockQuote>(`${this.baseUrl}/quote/${symbol}`);
  }

  getMultipleQuotes(symbols: string[]): Observable<StockQuote[]> {
    const params = symbols.map(s => `symbols=${s}`).join('&');
    return this.http.get<StockQuote[]>(`${this.baseUrl}/quotes?${params}`);
  }

  getMarketIndices(): Observable<MarketIndex[]> {
    return this.http.get<MarketIndex[]>(`${this.baseUrl}/market-indices`);
  }
}

