import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface WatchlistItem {
  id?: number;
  symbol: string;
  exchange: string;
  targetPrice?: number;
  currentPrice?: number;
  stopLoss?: number;
  takeProfit?: number;
  rsi?: number;
  notes?: string;
  createdAt?: string;
}

@Injectable({ providedIn: 'root' })
export class WatchlistService {
  private baseUrl = 'http://localhost:8085/api/watchlist';

  constructor(private http: HttpClient) {}

  getAll(): Observable<WatchlistItem[]> {
    return this.http.get<WatchlistItem[]>(this.baseUrl);
  }

  addOrUpdate(item: WatchlistItem): Observable<WatchlistItem> {
    return this.http.post<WatchlistItem>(this.baseUrl, item);
  }

  updatePrice(id: number, currentPrice: number, rsi?: number): Observable<WatchlistItem> {
    return this.http.put<WatchlistItem>(`${this.baseUrl}/${id}/price`, {
      currentPrice: currentPrice.toString(),
      rsi: rsi?.toString()
    });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
