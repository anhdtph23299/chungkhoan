import { Component, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BotService, BotStatus, RrgData, SignalScreener } from '../../services/bot';
import { TradeService, Trade } from '../../services/trade';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-bot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './bot.html',
  styleUrl: './bot.scss'
})
export class BotComponent implements OnInit, OnDestroy {
  @ViewChild('logContainer') logContainer?: ElementRef;

  status: BotStatus = {
    running: true,
    mode: 'LIVE_PAPER_MONEY',
    capital: 20000000,
    todayRealizedPnl: 0,
    todayTradesCount: 0,
    dailyTarget: 300000,
    circuitBreakerLimit: 400000,
    recentLogs: []
  };

  openPositions: Trade[] = [];
  closedTrades: Trade[] = [];
  signals: SignalScreener[] = [];
  sectorRrg?: RrgData;

  selectedLogCategory: 'ALL' | 'BOT' | 'DEFCON' | 'RRG' | 'LIQUIDITY' | 'TRADE' = 'ALL';
  filteredLogs: string[] = [];

  isLoading: boolean = false;
  actionMessage: string = '';
  actionType: 'success' | 'warning' | 'info' | 'error' = 'info';

  private pollSub?: any;
  private sseSub?: Subscription;

  constructor(
    private botService: BotService,
    private tradeService: TradeService
  ) {}

  ngOnInit(): void {
    this.refreshAllData();

    // Polling nhẹ mỗi 5 giây để luôn đồng bộ trạng thái
    this.pollSub = setInterval(() => {
      this.loadStatusOnly();
      this.loadPositions();
    }, 5000);

    // Lắng nghe SSE stream đẩy log và lệnh theo thời gian thực
    this.sseSub = this.botService.createEventSource().subscribe({
      next: (event) => {
        if (event.event === 'BOT_LOG' && event.data?.log) {
          this.status.recentLogs.unshift(event.data.log);
          if (this.status.recentLogs.length > 80) this.status.recentLogs.pop();
          if (event.data.realizedPnl !== undefined) this.status.todayRealizedPnl = event.data.realizedPnl;
          if (event.data.tradesCount !== undefined) this.status.todayTradesCount = event.data.tradesCount;
          this.applyLogFilter();
        } else if (event.event === 'BOT_TRADE_OPENED' || event.event === 'BOT_TRADE_CLOSED') {
          this.loadPositions();
          this.loadStatusOnly();
        }
      },
      error: (err) => console.debug('SSE stream error', err)
    });
  }

  ngOnDestroy(): void {
    if (this.pollSub) clearInterval(this.pollSub);
    if (this.sseSub) this.sseSub.unsubscribe();
  }

  refreshAllData(): void {
    this.loadStatusOnly();
    this.loadPositions();
    this.loadSignals();
    this.loadRrgRadar();
  }

  loadStatusOnly(): void {
    this.botService.getBotStatus().subscribe({
      next: (res) => {
        this.status = res;
        this.applyLogFilter();
      },
      error: (e) => console.debug('Error getting bot status', e)
    });
  }

  loadPositions(): void {
    this.tradeService.getAllTrades().subscribe({
      next: (trades) => {
        this.openPositions = trades.filter(t => t.status === 'open');
        this.closedTrades = trades.filter(t => t.status === 'closed').slice(0, 10);
      },
      error: (e) => console.debug('Error getting trades', e)
    });
  }

  loadSignals(): void {
    this.botService.getBotSignals().subscribe({
      next: (res) => this.signals = res.slice(0, 6),
      error: (e) => console.debug('Error getting signals', e)
    });
  }

  loadRrgRadar(): void {
    this.botService.getSectorsRrg().subscribe({
      next: (res) => this.sectorRrg = res,
      error: (e) => console.debug('Error getting RRG', e)
    });
  }

  toggleBot(): void {
    const nextState = !this.status.running;
    this.isLoading = true;
    this.botService.toggleBot(nextState).subscribe({
      next: (res) => {
        this.status.running = res.running;
        this.showMessage(res.message, res.running ? 'success' : 'warning');
        this.isLoading = false;
        this.loadStatusOnly();
      },
      error: (err) => {
        this.showMessage('Lỗi khi bật/tắt bot!', 'error');
        this.isLoading = false;
      }
    });
  }

  setupPaperTrading100M(): void {
    if (!confirm('Khởi tạo lại tài khoản Live Trace với số vốn 100.000.000 đ chuẩn bị cho Ngày 1 sáng mai (09:00)?')) {
      return;
    }
    this.isLoading = true;
    this.botService.setupPaperTrading(100000000).subscribe({
      next: (res) => {
        this.showMessage('🚀 ' + res.message, 'success');
        this.isLoading = false;
        this.refreshAllData();
      },
      error: (err) => {
        this.showMessage('Lỗi thiết lập paper trading!', 'error');
        this.isLoading = false;
      }
    });
  }

  triggerScanCycle(): void {
    this.isLoading = true;
    this.botService.triggerCycle().subscribe({
      next: (res) => {
        this.showMessage('⚡ Đã kích hoạt quét lệnh tức thì!', 'info');
        this.isLoading = false;
        this.loadStatusOnly();
        this.loadPositions();
      },
      error: (err) => {
        this.showMessage('Lỗi quét lệnh!', 'error');
        this.isLoading = false;
      }
    });
  }

  harvestProfit(symbol: string): void {
    this.isLoading = true;
    this.botService.harvestProfit(symbol).subscribe({
      next: (res) => {
        this.showMessage(res.message || `Đã chốt lời 50% gặt tiền mặt cho ${symbol}!`, 'success');
        this.isLoading = false;
        this.loadPositions();
        this.loadStatusOnly();
      },
      error: (err) => {
        this.showMessage('Lỗi chốt lời: ' + (err.error?.message || err.message), 'error');
        this.isLoading = false;
      }
    });
  }

  setLogFilter(category: 'ALL' | 'BOT' | 'DEFCON' | 'RRG' | 'LIQUIDITY' | 'TRADE'): void {
    this.selectedLogCategory = category;
    this.applyLogFilter();
  }

  applyLogFilter(): void {
    if (!this.status.recentLogs) {
      this.filteredLogs = [];
      return;
    }

    if (this.selectedLogCategory === 'ALL') {
      this.filteredLogs = [...this.status.recentLogs];
    } else if (this.selectedLogCategory === 'TRADE') {
      this.filteredLogs = this.status.recentLogs.filter(l => l.includes('MỞ VỊ THẾ') || l.includes('CHỐT LỜI') || l.includes('CẮT LỖ') || l.includes('GẶT HÁI'));
    } else if (this.selectedLogCategory === 'DEFCON') {
      this.filteredLogs = this.status.recentLogs.filter(l => l.includes('DEFCON') || l.includes('CẦU CHÌ') || l.includes('PHÒNG HỘ'));
    } else if (this.selectedLogCategory === 'RRG') {
      this.filteredLogs = this.status.recentLogs.filter(l => l.includes('RRG') || l.includes('ALPHA LEADER') || l.includes('BẪY TỤT HẬU'));
    } else if (this.selectedLogCategory === 'LIQUIDITY') {
      this.filteredLogs = this.status.recentLogs.filter(l => l.includes('THANH KHOẢN') || l.includes('SLIPPAGE') || l.includes('KIỂM TOÁN'));
    } else {
      this.filteredLogs = this.status.recentLogs.filter(l => l.includes('Bot:'));
    }
  }

  // Tiện ích tính % mục tiêu ngày
  getTargetProgressPercent(): number {
    if (!this.status.dailyTarget || this.status.dailyTarget <= 0) return 0;
    const pct = (this.status.todayRealizedPnl / this.status.dailyTarget) * 100;
    return Math.max(0, Math.min(100, Math.round(pct)));
  }

  // Đánh giá tình trạng T+2.5
  isT25Locked(trade: Trade): boolean {
    if (!trade.tradeDate) return false;
    const tDate = new Date(trade.tradeDate);
    const now = new Date();
    const diffDays = Math.floor((now.getTime() - tDate.getTime()) / (1000 * 3600 * 24));
    return diffDays < 2;
  }

  showMessage(msg: string, type: 'success' | 'warning' | 'info' | 'error'): void {
    this.actionMessage = msg;
    this.actionType = type;
    setTimeout(() => {
      if (this.actionMessage === msg) this.actionMessage = '';
    }, 6000);
  }
}
