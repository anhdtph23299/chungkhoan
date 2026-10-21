import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import {
  SignalValidatorService,
  SignalValidationTrade,
  ValidatorStats
} from '../../services/signal-validator';

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
    avgWinPercent: 0, avgLossPercent: 0, universeSize: 0, lastScanTime: ''
  };
  signals: SignalValidationTrade[] = [];
  logs: string[] = [];
  activeTab: 'signals' | 'pending' | 'closed' | 'logs' = 'signals';
  isResetting = false;

  private subs: Subscription[] = [];

  constructor(private validatorService: SignalValidatorService) {}

  ngOnInit() {
    this.loadAll();
    // Poll every 10 seconds
    this.subs.push(
      interval(10000).subscribe(() => this.loadAll())
    );
  }

  ngOnDestroy() {
    this.subs.forEach(s => s.unsubscribe());
  }

  loadAll() {
    this.validatorService.getStats().subscribe({ next: d => this.stats = d, error: () => {} });
    this.validatorService.getSignals().subscribe({ next: d => this.signals = d, error: () => {} });
    this.validatorService.getLogs().subscribe({ next: d => this.logs = d, error: () => {} });
  }

  get pendingSignals() {
    return this.signals.filter(s => s.outcome === 'PENDING');
  }

  get closedSignals() {
    return this.signals.filter(s => s.outcome !== 'PENDING');
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
    if (!pnl) return '';
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
    this.validatorService.reset().subscribe({
      next: () => { this.loadAll(); this.isResetting = false; },
      error: () => { this.isResetting = false; }
    });
  }
}
