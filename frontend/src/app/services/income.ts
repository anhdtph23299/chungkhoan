import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DailyIncome {
  reportDate: string;
  dailyRealizedProfit: number;
  dailyUnrealizedProfit: number;
  totalDailyNetProfit: number;
  dailyTarget: number;
  targetAchievementPercent: number;
  harvestedProfitsCount: number;
  tradesClosedToday: number;
  winRateToday: number;
  reinvestmentCapital: number;
  withdrawableIncome: number;
  monthlyProjectedIncome: number;
  marketStatus: string;
  dailyStatusMessage: string;
}

export interface SmartMoneyFlow {
  symbol: string;
  currentPrice: number;
  totalVolume: number;
  buyActiveVolume: number;
  sellActiveVolume: number;
  buyActiveRatio: number;
  foreignNetBuy: number;
  propNetBuy: number;
  accumulationScore: number;
  moneyFlowStatus: string;
  institutionalVerdict: string;
}

export interface WealthProjection {
  initialCapital: number;
  currentNav: number;
  realizedProfitToDate: number;
  totalProfitPercent: number;
  winRate: number;
  profitFactor: number;
  averageDailyIncome: number;
  projectedNav30Days: number;
  projectedNav60Days: number;
  projectedNav90Days: number;
  conservativeNav90Days: number;
  optimisticNav90Days: number;
  projectedWithdrawableCash90Days: number;
  projectedReinvestedCapital90Days: number;
  estimatedDaysToDoubleNav: number;
  monthlyRoiPercent: number;
  financialIndependenceVerdict: string;
}

@Injectable({ providedIn: 'root' })
export class IncomeService {
  private baseUrl = 'http://localhost:8080/api/income';

  constructor(private http: HttpClient) {}

  getTodayIncome(): Observable<DailyIncome> {
    return this.http.get<DailyIncome>(`${this.baseUrl}/today`);
  }

  getWealthProjection(): Observable<WealthProjection> {
    return this.http.get<WealthProjection>(`${this.baseUrl}/wealth-projection`);
  }

  getSmartMoney(): Observable<SmartMoneyFlow[]> {
    return this.http.get<SmartMoneyFlow[]>(`${this.baseUrl}/smart-money`);
  }

  getMoneyMatrix(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/money-matrix`);
  }

  harvestProfit(symbol: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/harvest/${symbol}`, {});
  }

  simulateTick(): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/simulate-tick`, {});
  }

  fastForwardDay(): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/fast-forward-day`, {});
  }
}
