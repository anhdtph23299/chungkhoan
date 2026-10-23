import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BotService } from '../../services/bot';
import {
  RiskService,
  PortfolioHealthReport,
  RealMoneyAuditDto,
  VeteranDisciplineAudit,
  BlackSwanStressScenario
} from '../../services/risk';

@Component({
  selector: 'app-risk',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './risk.html',
  styleUrl: './risk.scss'
})
export class RiskComponent implements OnInit {
  activeTab: 'sizer' | 'audit' | 'blackswan' = 'sizer';

  // Calculator inputs
  accountCapital = 100000000; // Mặc định 100 triệu theo tài khoản Bot thực tế
  maxRiskPercent = 1.5;       // 1.5% rủi ro tối đa theo chiến lược T1 Core
  entryPrice = 35000;         // Giá mua
  stopLossPrice = 32550;      // -7%
  takeProfitPrice = 40250;    // +15%
  liveNav = 97655312;

  // Institutional data
  portfolioHealth: PortfolioHealthReport | null = null;
  realMoneyAudit: RealMoneyAuditDto | null = null;
  veteranAudit: VeteranDisciplineAudit | null = null;
  blackSwan: BlackSwanStressScenario | null = null;
  loadingAudit = false;
  loadingBlackSwan = false;

  // Drawdown math table
  drawdownData = [
    { loss: 5, gainNeeded: 5.3, safe: true, note: 'Rất dễ phục hồi' },
    { loss: 7, gainNeeded: 7.5, safe: true, note: 'Ngưỡng cắt lỗ chuẩn của huyền thoại O\'Neil' },
    { loss: 10, gainNeeded: 11.1, safe: true, note: 'Giới hạn cảnh báo đỏ' },
    { loss: 20, gainNeeded: 25.0, safe: false, note: 'Khó phục hồi, tâm lý bất an' },
    { loss: 30, gainNeeded: 42.9, safe: false, note: 'Bắt đầu mất kiểm soát' },
    { loss: 50, gainNeeded: 100.0, safe: false, note: 'Cần lãi x2 mới về bờ (Cực kỳ nguy hiểm)' }
  ];

  constructor(
    private botService: BotService,
    private riskService: RiskService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.updateByPercent();
    this.loadBotInfo();
    this.loadAuditData();
  }

  loadBotInfo(): void {
    this.botService.getBotStatus().subscribe({
      next: (status) => {
        if (status.currentNav) {
          this.liveNav = status.currentNav;
        }
        if (status.capital) {
          this.accountCapital = status.capital;
        }
        if (status.riskPerTradePercent) {
          this.maxRiskPercent = status.riskPerTradePercent;
        }
        this.cdr.markForCheck();
      },
      error: () => {}
    });
  }

  loadAuditData(): void {
    this.loadingAudit = true;
    this.riskService.getPortfolioHealth().subscribe({
      next: (health) => {
        this.portfolioHealth = health;
        this.loadingAudit = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingAudit = false;
        this.cdr.markForCheck();
      }
    });

    this.riskService.getRealMoneyAudit().subscribe({
      next: (audit) => {
        this.realMoneyAudit = audit;
        this.cdr.markForCheck();
      },
      error: () => {}
    });

    this.riskService.getVeteranDisciplineAudit().subscribe({
      next: (va) => {
        this.veteranAudit = va;
        this.cdr.markForCheck();
      },
      error: () => {}
    });

    this.loadingBlackSwan = true;
    this.riskService.getBlackSwanStress().subscribe({
      next: (bs) => {
        this.blackSwan = bs;
        this.loadingBlackSwan = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingBlackSwan = false;
        this.cdr.markForCheck();
      }
    });
  }

  setTab(tab: 'sizer' | 'audit' | 'blackswan'): void {
    this.activeTab = tab;
    this.cdr.markForCheck();
  }

  getSectorEntries(): { key: string; value: number }[] {
    if (!this.portfolioHealth || !this.portfolioHealth.sectorAllocations) return [];
    return Object.entries(this.portfolioHealth.sectorAllocations).map(([key, value]) => ({ key, value }));
  }

  setCapital(amount: number): void {
    this.accountCapital = amount;
    this.cdr.markForCheck();
  }

  get maxAllowedLossAmount(): number {
    return (this.accountCapital * this.maxRiskPercent) / 100;
  }

  get riskPerShare(): number {
    return Math.max(0, this.entryPrice - this.stopLossPrice);
  }

  get profitPerShare(): number {
    return Math.max(0, this.takeProfitPrice - this.entryPrice);
  }

  get stopLossPercent(): number {
    return this.entryPrice > 0 ? ((this.entryPrice - this.stopLossPrice) / this.entryPrice) * 100 : 0;
  }

  get takeProfitPercent(): number {
    return this.entryPrice > 0 ? ((this.takeProfitPrice - this.entryPrice) / this.entryPrice) * 100 : 0;
  }

  get riskRewardRatio(): number {
    return this.riskPerShare > 0 ? this.profitPerShare / this.riskPerShare : 0;
  }

  get isRRAcceptable(): boolean {
    return this.riskRewardRatio >= 2.0;
  }

  get maxSharesToBuy(): number {
    if (this.riskPerShare <= 0) return 0;
    const rawShares = this.maxAllowedLossAmount / this.riskPerShare;
    // Làm tròn xuống bội số của 100 cổ phiếu (lô chuẩn sàn VN)
    return Math.floor(rawShares / 100) * 100;
  }

  get totalCapitalRequired(): number {
    return this.maxSharesToBuy * this.entryPrice;
  }

  get allocationPercent(): number {
    return this.accountCapital > 0 ? (this.totalCapitalRequired / this.accountCapital) * 100 : 0;
  }

  get expectedProfitAmount(): number {
    return this.maxSharesToBuy * this.profitPerShare;
  }

  get expectedLossAmount(): number {
    return this.maxSharesToBuy * this.riskPerShare;
  }

  updateByPercent(): void {
    if (this.entryPrice > 0) {
      // 7% Stoploss & 15% Takeprofit
      this.stopLossPrice = Math.round(this.entryPrice * 0.93 / 100) * 100;
      this.takeProfitPrice = Math.round(this.entryPrice * 1.15 / 100) * 100;
    }
  }
}
