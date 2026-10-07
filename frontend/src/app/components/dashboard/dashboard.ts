import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TradeService, Trade, PortfolioSummary } from '../../services/trade';
import { StockService, StockQuote, MarketIndex } from '../../services/stock';
import { IncomeService, DailyIncome, SmartMoneyFlow, WealthProjection } from '../../services/income';
import { RiskService, VeteranDisciplineAudit } from '../../services/risk';
import { forkJoin, interval, Subscription } from 'rxjs';
import { startWith } from 'rxjs/operators';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class DashboardComponent implements OnInit, OnDestroy {

  // ---- Tráº¡ng thÃ¡i loading ----
  isLoadingMain = true;
  isLoadingMarket = true;
  isLoadingEngine = true;
  backendOnline = false;

  // ---- Portfolio Summary (tá»« API tháº­t) ----
  summary: PortfolioSummary | null = null;
  openPositions: Trade[] = [];

  // ---- Thá»‹ trÆ°á»ng (tá»« API tháº­t) ----
  marketIndices: MarketIndex[] = [];
  hotQuotes: StockQuote[] = [];

  // ---- Cá»— mÃ¡y kiáº¿m tiá»n (tá»« API tháº­t) ----
  dailyIncome: DailyIncome | null = null;
  wealthProjection: WealthProjection | null = null;
  smartMoneyPicks: SmartMoneyFlow[] = [];
  veteranAudit: VeteranDisciplineAudit | null = null;

  // ---- Quick Trade Modal ----
  showQuickTradeModal = false;
  actionMessage = '';
  isHarvesting = false;
  newTrade: Partial<Trade> = {
    symbol: '',
    exchange: 'HOSE',
    type: 'BUY',
    price: 0,
    quantity: 0,
    fee: 0.0015,
    strategy: '',
    stopLoss: 0,
    takeProfit: 0,
    reason: ''
  };

  private refreshSub?: Subscription;

  constructor(
    private tradeService: TradeService,
    private stockService: StockService,
    private incomeService: IncomeService,
    private riskService: RiskService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadAllData();
    // Refresh thá»‹ trÆ°á»ng + engine má»—i 15 giÃ¢y
    this.refreshSub = interval(15000).pipe(startWith(0)).subscribe(() => {
      this.refreshMarketData();
      this.loadMoneyEngine();
    });
  }

  ngOnDestroy(): void {
    this.refreshSub?.unsubscribe();
  }

  /** Táº£i portfolio & trades tháº­t */
  loadAllData(): void {
    this.isLoadingMain = true;
    forkJoin({
      summary: this.tradeService.getPortfolioSummary(),
      trades: this.tradeService.getAllTrades()
    }).subscribe({
      next: ({ summary, trades }) => {
        this.summary = summary;
        this.openPositions = trades.filter(t => t.status === 'OPEN' || t.status === 'open');
        this.isLoadingMain = false;
        this.backendOnline = true;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingMain = false;
        this.backendOnline = false;
        this.cdr.markForCheck();
      }
    });
  }

  /** Refresh giÃ¡ thá»‹ trÆ°á»ng tháº­t */
  refreshMarketData(): void {
    // Láº¥y chá»‰ sá»‘ VN-INDEX, VN30, HNX, UPCOM tháº­t
    this.stockService.getMarketIndices().subscribe({
      next: (indices) => {
        this.marketIndices = indices;
        this.isLoadingMarket = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingMarket = false;
        this.cdr.markForCheck();
      }
    });

    // Láº¥y giÃ¡ cá»• phiáº¿u: Æ°u tiÃªn mÃ£ Ä‘ang náº¯m giá»¯ + default watchlist
    const activeSymbols = this.openPositions.map(p => p.symbol);
    const defaultSymbols = ['FPT', 'HPG', 'SSI', 'MWG', 'TCB', 'VHM'];
    const symbols = [...new Set([...activeSymbols, ...defaultSymbols])].slice(0, 8);
    this.stockService.getMultipleQuotes(symbols).subscribe({
      next: (quotes) => { this.hotQuotes = quotes; this.cdr.markForCheck(); },
      error: () => { this.cdr.markForCheck(); }
    });
  }

  /** Táº£i cá»— mÃ¡y kiáº¿m tiá»n â€” income, projection, smart money */
  loadMoneyEngine(): void {
    this.isLoadingEngine = true;

    this.incomeService.getTodayIncome().subscribe({
      next: (data) => { this.dailyIncome = data; this.cdr.markForCheck(); },
      error: () => { this.cdr.markForCheck(); }
    });

    this.incomeService.getWealthProjection().subscribe({
      next: (data) => {
        this.wealthProjection = data;
        this.isLoadingEngine = false;
        this.cdr.markForCheck();
      },
      error: () => { this.isLoadingEngine = false; this.cdr.markForCheck(); }
    });

    this.incomeService.getSmartMoney().subscribe({
      next: (list) => { this.smartMoneyPicks = list.slice(0, 4); this.cdr.markForCheck(); },
      error: () => { this.cdr.markForCheck(); }
    });

    this.riskService.getVeteranDisciplineAudit().subscribe({
      next: (audit) => { this.veteranAudit = audit; this.cdr.markForCheck(); },
      error: () => { this.cdr.markForCheck(); }
    });
  }

  harvestProfit(symbol: string): void {
    this.isHarvesting = true;
    this.incomeService.harvestProfit(symbol).subscribe({
      next: (res) => {
        this.isHarvesting = false;
        this.actionMessage = `ðŸŽ‰ ${res.message || 'ÄÃ£ gáº·t hÃ¡i 50% thÃ nh cÃ´ng!'}`;
        this.loadAllData();
        this.loadMoneyEngine();
        setTimeout(() => this.actionMessage = '', 6000);
      },
      error: () => { this.isHarvesting = false; }
    });
  }

  fastForwardDay(): void {
    this.incomeService.fastForwardDay().subscribe({
      next: (res) => {
        this.actionMessage = `âš¡ ${res.message || 'ÄÃ£ tua nhanh phiÃªn!'}`;
        this.loadAllData();
        this.loadMoneyEngine();
        setTimeout(() => this.actionMessage = '', 6000);
      }
    });
  }

  // ---- Helper methods ----
  get isWeekend(): boolean {
    const day = new Date().getDay();
    return day === 0 || day === 6;
  }

  getTargetProgress(): number {
    if (!this.dailyIncome?.dailyTarget || this.dailyIncome.dailyTarget <= 0) return 0;
    const pct = (this.dailyIncome.dailyRealizedProfit / this.dailyIncome.dailyTarget) * 100;
    return Math.max(0, Math.min(999, Math.round(pct)));
  }

  formatVolume(v: number): string {
    if (!v || v <= 0) return '';
    if (v >= 1_000_000_000_000) return (v / 1_000_000_000_000).toFixed(1) + ' nghìn tỷ';
    if (v >= 1_000_000_000) return (v / 1_000_000_000).toFixed(0) + ' tỷ';
    if (v >= 1_000_000) return (v / 1_000_000).toFixed(1) + 'M';
    return v.toLocaleString('vi-VN');
  }

  isT25Locked(trade: Trade): boolean {
    if (!trade.tradeDate) return false;
    const diffDays = Math.floor((Date.now() - new Date(trade.tradeDate).getTime()) / 86400000);
    return diffDays < 2;
  }

  getQuoteForPosition(symbol: string): StockQuote | undefined {
    return this.hotQuotes.find(q => q.symbol === symbol);
  }

  // ---- Quick Trade Modal ----
  openQuickTrade(): void { this.showQuickTradeModal = true; }
  closeQuickTrade(): void { this.showQuickTradeModal = false; }

  onSymbolChange(): void {
    if (!this.newTrade.symbol) return;
    this.newTrade.symbol = this.newTrade.symbol.toUpperCase();
    const q = this.hotQuotes.find(item => item.symbol === this.newTrade.symbol);
    if (q) { this.newTrade.price = q.price; this.autoCalculateTargets(); }
  }

  autoCalculateTargets(): void {
    if (this.newTrade.price && this.newTrade.price > 0) {
      this.newTrade.stopLoss  = Math.round(this.newTrade.price * 0.93 / 100) * 100;
      this.newTrade.takeProfit = Math.round(this.newTrade.price * 1.15 / 100) * 100;
    }
  }

  submitQuickTrade(): void {
    if (!this.newTrade.symbol || !this.newTrade.price || !this.newTrade.quantity) {
      alert('Vui lÃ²ng Ä‘iá»n Ä‘áº§y Ä‘á»§ MÃ£ CP, GiÃ¡ vÃ  Khá»‘i lÆ°á»£ng!');
      return;
    }
    const tradeToSave: Partial<Trade> = {
      ...this.newTrade,
      tradeDate: new Date().toISOString().split('T')[0],
      status: 'OPEN'
    };
    this.tradeService.createTrade(tradeToSave).subscribe({
      next: (saved) => {
        this.openPositions.unshift(saved);
        this.showQuickTradeModal = false;
        this.actionMessage = `âœ… ÄÃ£ ghi nháº­n lá»‡nh ${saved.symbol}!`;
        this.loadAllData();
        setTimeout(() => this.actionMessage = '', 5000);
      },
      error: () => { alert('Lá»—i khi lÆ°u lá»‡nh â€” kiá»ƒm tra backend!'); }
    });
  }
}
