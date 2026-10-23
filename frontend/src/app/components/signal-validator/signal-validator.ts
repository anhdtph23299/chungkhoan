import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription } from 'rxjs';
import {
  SignalValidatorService,
  SignalValidationTrade,
  ValidatorStats
} from '../../services/signal-validator';
import { BotService, BotStatus } from '../../services/bot';

@Component({
  selector: 'app-signal-validator',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './signal-validator.html',
  styleUrl: './signal-validator.scss'
})
export class SignalValidatorComponent implements OnInit, OnDestroy {
  stats: ValidatorStats = {
    totalSignals: 0, pendingCount: 0, closedCount: 0,
    winCount: 0, lossCount: 0, winRate: 0, expectancy: 0,
    avgWinPercent: 0, avgLossPercent: 0, universeSize: 0, lastScanTime: '',
    pendingGreenCount: 0, pendingRedCount: 0, pendingRefCount: 0,
    pendingWinRate: 0, pendingAvgPnl: 0, bestPerformer: '—', worstPerformer: '—'
  };
  signals: SignalValidationTrade[] = [];
  logs: string[] = [];
  botStatus?: BotStatus;
  activeTab: 'signals' | 'pending' | 'green' | 'red' | 'closed' | 'logs' = 'signals';
  isResetting = false;
  isScanning = false;
  searchTerm = '';
  sortBy: 'score' | 'pnl' | 'symbol' = 'pnl';
  sortDir: 'asc' | 'desc' = 'desc';

  private subs: Subscription[] = [];

  constructor(
    private validatorService: SignalValidatorService,
    private botService: BotService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.loadAll();
    this.loadBotStatus();
    // Poll every 10 seconds
    this.subs.push(
      interval(10000).subscribe(() => {
        this.loadAll();
        this.loadBotStatus();
      })
    );
  }

  loadBotStatus() {
    this.botService.getBotStatus().subscribe({
      next: s => {
        this.botStatus = s;
        this.cdr.markForCheck();
      },
      error: err => console.debug('Bot status sync error:', err)
    });
  }

  triggerScan() {
    this.isScanning = true;
    this.cdr.markForCheck();
    this.validatorService.scanNow().subscribe({
      next: stats => {
        this.stats = stats;
        this.loadAll();
        this.isScanning = false;
        this.cdr.markForCheck();
      },
      error: err => {
        console.error('Scan error:', err);
        this.isScanning = false;
        this.cdr.markForCheck();
      }
    });
  }

  ngOnDestroy() {
    this.subs.forEach(s => s.unsubscribe());
  }

  loadAll() {
    this.validatorService.getStats().subscribe({
      next: d => {
        this.stats = d;
        this.cdr.markForCheck();
      },
      error: err => console.error('Validator getStats error:', err)
    });
    this.validatorService.getSignals().subscribe({
      next: d => {
        this.signals = d;
        this.cdr.markForCheck();
      },
      error: err => console.error('Validator getSignals error:', err)
    });
    this.validatorService.getLogs().subscribe({
      next: d => {
        this.logs = d;
        this.cdr.markForCheck();
      },
      error: err => console.error('Validator getLogs error:', err)
    });
  }

  setActiveTab(tab: 'signals' | 'pending' | 'green' | 'red' | 'closed' | 'logs') {
    this.activeTab = tab;
    this.cdr.markForCheck();
  }

  get pendingSignals(): SignalValidationTrade[] {
    return this.signals.filter(s => s.outcome === 'PENDING');
  }

  get greenSignals(): SignalValidationTrade[] {
    return this.signals.filter(s => (s.currentPnlPercent || 0) > 0);
  }

  get redSignals(): SignalValidationTrade[] {
    return this.signals.filter(s => (s.currentPnlPercent || 0) < 0);
  }

  get closedSignals(): SignalValidationTrade[] {
    return this.signals.filter(s => s.outcome !== 'PENDING');
  }

  get effectiveWinRate(): number {
    return this.stats.closedCount > 0 ? this.stats.winRate : (this.stats.pendingWinRate ?? 0);
  }

  get effectiveExpectancy(): number {
    return this.stats.closedCount > 0 ? this.stats.expectancy : (this.stats.pendingAvgPnl ?? 0);
  }

  get totalEstPnl10m(): number {
    return this.signals.reduce((acc, s) => acc + ((s.currentPnlPercent || 0) / 100 * 10000000), 0);
  }

  priceDiff(s: SignalValidationTrade): number {
    return (s.currentPrice || 0) - (s.entryPrice || 0);
  }

  estPnl10m(s: SignalValidationTrade): number {
    return ((s.currentPnlPercent || 0) / 100) * 10000000;
  }

  get displayedSignals(): SignalValidationTrade[] {
    let list: SignalValidationTrade[];
    if (this.activeTab === 'pending') list = this.pendingSignals;
    else if (this.activeTab === 'green') list = this.greenSignals;
    else if (this.activeTab === 'red') list = this.redSignals;
    else if (this.activeTab === 'closed') list = this.closedSignals;
    else list = this.signals;

    if (this.searchTerm.trim()) {
      const q = this.searchTerm.trim().toUpperCase();
      list = list.filter(s => s.symbol.toUpperCase().includes(q));
    }
    return [...list].sort((a, b) => {
      let cmp = 0;
      if (this.sortBy === 'score') cmp = (b.confidenceScore || 0) - (a.confidenceScore || 0);
      else if (this.sortBy === 'pnl') cmp = (b.currentPnlPercent || 0) - (a.currentPnlPercent || 0);
      else if (this.sortBy === 'symbol') cmp = a.symbol.localeCompare(b.symbol);
      return this.sortDir === 'desc' ? cmp : -cmp;
    });
  }

  setSort(col: 'score' | 'pnl' | 'symbol') {
    if (this.sortBy === col) {
      this.sortDir = this.sortDir === 'desc' ? 'asc' : 'desc';
    } else {
      this.sortBy = col;
      this.sortDir = 'desc';
    }
    this.cdr.markForCheck();
  }

  onSearch(event: any) {
    this.searchTerm = event.target.value || '';
    this.cdr.markForCheck();
  }

  getOutcomeLabel(outcome: string): string {
    switch (outcome) {
      case 'WIN': return '✅ WIN';
      case 'LOSS': return '❌ LOSS';
      case 'STOPPED_OUT': return '🛑 CẮT LỖ';
      case 'TIMEOUT': return '⏰ HẾT HẠN';
      case 'PENDING': return '🔄 Đang theo dõi';
      default: return outcome;
    }
  }

  getOutcomeClass(outcome: string): string {
    switch (outcome) {
      case 'WIN': return 'outcome-win';
      case 'LOSS': return 'outcome-loss';
      case 'STOPPED_OUT': return 'outcome-stopped';
      case 'TIMEOUT': return 'outcome-timeout';
      case 'PENDING': return 'outcome-pending';
      default: return '';
    }
  }

  getPnlClass(pnl: number): string {
    if (pnl === null || pnl === undefined || pnl === 0) return 'pnl-zero';
    return pnl > 0 ? 'pnl-positive' : 'pnl-negative';
  }

  getScoreClass(score: number): string {
    if (score >= 80) return 'score-high';
    if (score >= 70) return 'score-mid';
    return 'score-low';
  }

  reset() {
    if (!confirm('Xóa toàn bộ lịch sử validation? Hành động này không thể hoàn tác.')) return;
    this.isResetting = true;
    this.cdr.markForCheck();
    this.validatorService.reset().subscribe({
      next: () => {
        this.loadAll();
        this.isResetting = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isResetting = false;
        this.cdr.markForCheck();
      }
    });
  }
}
