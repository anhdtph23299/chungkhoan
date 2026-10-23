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

export interface RealMoneyAuditCriterion {
  name: string;
  requiredThreshold: string;
  actualValue: string;
  status: 'PASS' | 'IN_PROGRESS' | 'WARNING';
  notes: string;
}

export interface RealMoneyAuditDto {
  certifiedForRealMoney: boolean;
  readinessScorePercent: number;
  executiveSummary: string;
  currentNav: number;
  netProfitToDate: number;
  winRate: number;
  profitFactor: number;
  mathematicalExpectancy: number;
  maxDrawdownPercent: number;
  criteria: RealMoneyAuditCriterion[];
  recommendedNextSteps: string[];
}

export interface StressScenarioDetail {
  scenarioId: string;
  scenarioName: string;
  historicalEventDescription: string;
  marketShockPercent: number;
  portfolioLossPercent: number;
  portfolioLossVnd: number;
  capitalAfterShock: number;
  t25LiquidityFreezeImpact: string;
  survivalStatus: string;
}

export interface BlackSwanStressScenario {
  portfolioCapital: number;
  overallResilienceScore: number;
  worstCaseDrawdownPercent: number;
  worstCaseLossAmount: number;
  marginCallTriggered: boolean;
  recommendedCashBufferPercent: number;
  recommendedVn30FuturesHedgeContracts: number;
  institutionalStressVerdict: string;
  scenarioResults: StressScenarioDetail[];
}

export interface PortfolioHealthReport {
  status: string;
  currentNav: number;
  cashPercent: number;
  investedPercent: number;
  openPositionsCount: number;
  maxAllowedPositions: number;
  sectorAllocations: { [key: string]: number };
  sectorWarnings: string[];
  streakStatus: string;
  streakCount: number;
  recommendedRiskPercent: number;
  allowNewPurchases: boolean;
  riskChecklist: string[];
}

@Injectable({ providedIn: 'root' })
export class RiskService {
  private baseUrl = 'http://localhost:8085/api/risk';

  constructor(private http: HttpClient) {}

  getVeteranDisciplineAudit(): Observable<VeteranDisciplineAudit> {
    return this.http.get<VeteranDisciplineAudit>(`${this.baseUrl}/veteran-discipline-audit`);
  }

  getRealMoneyAudit(): Observable<RealMoneyAuditDto> {
    return this.http.get<RealMoneyAuditDto>(`${this.baseUrl}/real-money-audit`);
  }

  getPortfolioHealth(): Observable<PortfolioHealthReport> {
    return this.http.get<PortfolioHealthReport>(`${this.baseUrl}/portfolio-health`);
  }

  getBlackSwanStress(capital?: number): Observable<BlackSwanStressScenario> {
    const url = capital ? `${this.baseUrl}/black-swan-stress?capital=${capital}` : `${this.baseUrl}/black-swan-stress`;
    return this.http.get<BlackSwanStressScenario>(url);
  }
}
