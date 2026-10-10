import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FuturesService, FuturesQuote, FuturesSignal, FuturesPosition, FuturesBotConfig, FuturesBacktestResult } from '../../services/futures';

@Component({
  selector: 'app-futures',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './futures.html',
  styleUrl: './futures.scss'
})
export class FuturesComponent implements OnInit, OnDestroy {
  quote?: FuturesQuote;
  signal?: FuturesSignal;
  openPositions: FuturesPosition[] = [];
  positionHistory: FuturesPosition[] = [];
  botConfig?: FuturesBotConfig;
  botLogs: string[] = [];

  // Backtest
  backtestResult?: FuturesBacktestResult;
  isBacktesting = false;
  btDays = 10;
  btStopLoss = 2.5;
  btTakeProfit = 5.0;
  btTrailingStop = 1.5;

  // Active view tab
  activeTab: 'TERMINAL' | 'BACKTEST' | 'HISTORY' = 'TERMINAL';

  // Manual Order Inputs
  manualContracts = 1;
  orderSide: 'LONG' | 'SHORT' = 'LONG';
  manualStopLoss?: number;
  manualTakeProfit?: number;

  actionMessage = '';
  actionType: 'success' | 'warning' | 'error' | 'info' = 'info';

  private pollTimer?: any;

  constructor(
    private futuresService: FuturesService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.refreshAll();
    this.pollTimer = setInterval(() => {
      this.pollRealtime();
    }, 4000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) clearInterval(this.pollTimer);
  }

  refreshAll(): void {
    this.futuresService.getQuote().subscribe({
      next: q => { this.quote = q; this.cdr.markForCheck(); },
      error: err => console.warn('Lỗi lấy quote phái sinh:', err)
    });

    this.futuresService.getSignal().subscribe({
      next: s => { this.signal = s; this.cdr.markForCheck(); },
      error: err => console.warn('Lỗi lấy signal phái sinh:', err)
    });

    this.futuresService.getOpenPositions().subscribe({
      next: pos => { this.openPositions = pos; this.cdr.markForCheck(); },
      error: err => console.warn('Lỗi lấy open positions:', err)
    });

    this.futuresService.getPositionHistory().subscribe({
      next: h => { this.positionHistory = h; this.cdr.markForCheck(); },
      error: err => console.warn('Lỗi lấy history:', err)
    });

    this.futuresService.getBotStatus().subscribe({
      next: res => {
        this.botConfig = res.config;
        this.botLogs = res.logs || [];
        this.cdr.markForCheck();
      },
      error: err => console.warn('Lỗi lấy bot status:', err)
    });
  }

  pollRealtime(): void {
    this.futuresService.getQuote().subscribe(q => { this.quote = q; this.cdr.markForCheck(); });
    this.futuresService.getSignal().subscribe(s => { this.signal = s; this.cdr.markForCheck(); });
    this.futuresService.getOpenPositions().subscribe(pos => { this.openPositions = pos; this.cdr.markForCheck(); });
    this.futuresService.getBotStatus().subscribe(res => {
      this.botConfig = res.config;
      this.botLogs = res.logs || [];
      this.cdr.markForCheck();
    });
  }

  toggleBot(enable: boolean): void {
    this.futuresService.toggleBot(enable).subscribe({
      next: res => {
        if (this.botConfig) this.botConfig.autoTrading = res.autoTrading;
        this.showToast(res.message, 'success');
        this.refreshAll();
      },
      error: err => this.showToast('Lỗi bật/tắt bot: ' + err.message, 'error')
    });
  }

  executeQuickOrder(side: 'LONG' | 'SHORT'): void {
    if (!this.quote) return;
    const currentPrice = this.quote.currentPrice;
    const sl = side === 'LONG' ? currentPrice - 2.5 : currentPrice + 2.5;
    const tp = side === 'LONG' ? currentPrice + 5.0 : currentPrice - 5.0;

    this.futuresService.openPosition(side, this.manualContracts, currentPrice, sl, tp, 'Lệnh giao dịch nhanh Terminal').subscribe({
      next: pos => {
        this.showToast(`Khớp lệnh thành công: ${side} ${pos.contracts} HĐ @ ${pos.entryPrice}`, 'success');
        this.refreshAll();
      },
      error: err => this.showToast('Không thể mở lệnh: ' + err.message, 'error')
    });
  }

  closePosition(pos: FuturesPosition): void {
    this.futuresService.closePosition(pos.id, this.quote?.currentPrice, 'Đóng thủ công từ Terminal').subscribe({
      next: closed => {
        this.showToast(`Đã tất toán vị thế ${closed.side}: Lãi/Lỗ ${closed.pnlPoints > 0 ? '+' : ''}${closed.pnlPoints} điểm`, 'info');
        this.refreshAll();
      },
      error: err => this.showToast('Lỗi đóng vị thế: ' + err.message, 'error')
    });
  }

  closeAll(): void {
    this.futuresService.closeAllPositions('Tất toán khẩn cấp từ Terminal').subscribe({
      next: res => {
        this.showToast(res.message, 'warning');
        this.refreshAll();
      },
      error: err => this.showToast('Lỗi tất toán: ' + err.message, 'error')
    });
  }

  runBacktest(): void {
    this.isBacktesting = true;
    this.futuresService.runBacktest(this.btDays, this.btStopLoss, this.btTakeProfit, this.btTrailingStop).subscribe({
      next: res => {
        this.backtestResult = res;
        this.isBacktesting = false;
        this.showToast(`Hoàn tất backtest ${res.testDays} ngày: Win Rate ${res.winRatePercent}%, Lãi ròng ${res.totalNetPnlVnd.toLocaleString()} đ`, 'success');
        this.cdr.markForCheck();
      },
      error: err => {
        this.isBacktesting = false;
        this.showToast('Lỗi chạy backtest: ' + err.message, 'error');
      }
    });
  }

  showToast(msg: string, type: 'success' | 'warning' | 'error' | 'info'): void {
    this.actionMessage = msg;
    this.actionType = type;
    setTimeout(() => {
      if (this.actionMessage === msg) this.actionMessage = '';
      this.cdr.markForCheck();
    }, 4500);
  }
}
