import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DisciplineRuleCheck {
  ruleName: string;
  sourceOrigin: string;
  compliant: boolean;
  statusText: string;
  explanation: string;
  quantitativeMetric: string;
}

export interface VeteranDisciplineAudit {
  systemName: string;
  disciplineScore: number;
  overallVerdict: string;
  ruleChecks: DisciplineRuleCheck[];
}

export interface RealMoneyAudit {
  systemVerdict: string;
  readyForRealCapital: boolean;
  readinessScore: number;
  checklist: any[];
}

@Injectable({ providedIn: 'root' })
export class RiskService {
  private baseUrl = 'http://localhost:8080/api/risk';

  constructor(private http: HttpClient) {}

  getVeteranDisciplineAudit(): Observable<VeteranDisciplineAudit> {
    return this.http.get<VeteranDisciplineAudit>(`${this.baseUrl}/veteran-discipline-audit`);
  }

  getRealMoneyAudit(): Observable<RealMoneyAudit> {
    return this.http.get<RealMoneyAudit>(`${this.baseUrl}/real-money-audit`);
  }

  getPortfolioHealth(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/portfolio-health`);
  }
}
