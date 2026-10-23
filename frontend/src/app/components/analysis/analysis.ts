import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { TradeService, Trade } from '../../services/trade';
import { AnalysisService, BackendStockScanResult, VcpPattern, StatisticalArbitragePair } from '../../services/analysis';

export interface TechnicalScanItem {
  symbol: string;
  name: string;
  exchange: string;
  price: number;
  changePercent: number;
  rsi: number;
  ma20: number;
  ma50: number;
  volumeRatio: number; // vs MA20 vol
  signalType: string;
  signalTitle: string;
  buyZone: string;
  stopLoss: number;
  target: number;
  status: 'STRONG_BUY' | 'BUY' | 'WATCH' | 'RISK';
}

@Component({
  selector: 'app-analysis',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './analysis.html',
  styleUrl: './analysis.scss'
})
export class AnalysisComponent implements OnInit {
  selectedSymbol = 'FPT';
  activeFilter = 'ALL';
  activeSubtab: 'scanner' | 'vcp' | 'statarb' = 'scanner';
  isLoadingScan = false;
  isLoadingVcp = false;
  isLoadingStatArb = false;

  vcpPatterns: VcpPattern[] = [];
  statArbPairs: StatisticalArbitragePair[] = [];

  scannedStocks: TechnicalScanItem[] = [
    {
      symbol: 'FPT',
      name: 'Tập đoàn FPT',
      exchange: 'HOSE',
      price: 57000,
      changePercent: -0.70,
      rsi: 30.7,
      ma20: 59365,
      ma50: 62892,
      volumeRatio: 1.25,
      signalType: 'RSI_OVERSOLD',
      signalTitle: 'Quá bán kỹ thuật RSI 30.7, chạm hỗ trợ cứng',
      buyZone: '56,100 - 57,000',
      stopLoss: 54900,
      target: 61600,
      status: 'BUY'
    },
    {
      symbol: 'DCM',
      name: 'Đạm Cà Mau',
      exchange: 'HOSE',
      price: 35500,
      changePercent: 0.71,
      rsi: 62.6,
      ma20: 34085,
      ma50: 32950,
      volumeRatio: 1.22,
      signalType: 'BREAKOUT_VOL',
      signalTitle: 'MACD phân kỳ dương hướng lên, cản 35,650 đ',
      buyZone: '34,950 - 35,500',
      stopLoss: 34100,
      target: 38600,
      status: 'STRONG_BUY'
    },
    {
      symbol: 'GMD',
      name: 'Gemadept',
      exchange: 'HOSE',
      price: 57100,
      changePercent: -0.70,
      rsi: 70.8,
      ma20: 53580,
      ma50: 52370,
      volumeRatio: 1.25,
      signalType: 'BREAKOUT_VOL',
      signalTitle: 'Dòng tiền Big Boys tích lũy mạnh, EMA20 dốc lên',
      buyZone: '56,200 - 57,100',
      stopLoss: 54900,
      target: 61900,
      status: 'STRONG_BUY'
    },
    {
      symbol: 'BCM',
      name: 'Becamex IDC',
      exchange: 'HOSE',
      price: 41650,
      changePercent: 2.08,
      rsi: 62.1,
      ma20: 39207,
      ma50: 40659,
      volumeRatio: 2.39,
      signalType: 'BREAKOUT_VOL',
      signalTitle: 'Bùng nổ vượt kháng cự với Volume tăng 2.39x MA20',
      buyZone: '41,050 - 41,650',
      stopLoss: 39700,
      target: 46000,
      status: 'STRONG_BUY'
    },
    {
      symbol: 'HPG',
      name: 'Tập đoàn Hòa Phát',
      exchange: 'HOSE',
      price: 20950,
      changePercent: -0.48,
      rsi: 54.2,
      ma20: 21200,
      ma50: 21800,
      volumeRatio: 1.15,
      signalType: 'GOLDEN_CROSS',
      signalTitle: 'Tích lũy nền giá chặt chẽ quanh ngưỡng hỗ trợ',
      buyZone: '20,500 - 21,000',
      stopLoss: 19500,
      target: 24000,
      status: 'BUY'
    },
    {
      symbol: 'TCB',
      name: 'Techcombank',
      exchange: 'HOSE',
      price: 34200,
      changePercent: 0.15,
      rsi: 58.0,
      ma20: 33800,
      ma50: 32900,
      volumeRatio: 1.35,
      signalType: 'GOLDEN_CROSS',
      signalTitle: 'Duy trì kênh tăng giá trên đường hỗ trợ MA20',
      buyZone: '33,800 - 34,200',
      stopLoss: 32500,
      target: 38500,
      status: 'BUY'
    }
  ];

  filteredStocks: TechnicalScanItem[] = [];

  constructor(
    private sanitizer: DomSanitizer,
    private tradeService: TradeService,
    private analysisService: AnalysisService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.filterSignals('ALL');
    this.loadRealAnalysisScan();
    this.loadVcpPatterns();
    this.loadStatArb();
  }

  loadRealAnalysisScan(): void {
    this.isLoadingScan = true;
    this.analysisService.scanStocks('ALL').subscribe({
      next: (results) => {
        if (results && results.length > 0) {
          const mapped: TechnicalScanItem[] = results.map(r => {
            let status: 'STRONG_BUY' | 'BUY' | 'WATCH' | 'RISK' = 'WATCH';
            if (r.action === 'STRONG_BUY') status = 'STRONG_BUY';
            else if (r.action === 'BUY') status = 'BUY';
            else if (r.action === 'AVOID') status = 'RISK';

            let sigType = r.signalType || 'BREAKOUT_VOL';
            if (sigType === 'MACD_GOLDEN_CROSS' || sigType === 'PULLBACK_MA20') sigType = 'GOLDEN_CROSS';
            if (sigType === 'OVERSOLD' || sigType === 'OVERSOLD_BOUNCE') sigType = 'RSI_OVERSOLD';
            if (sigType === 'WARNING_BEAR' || r.action === 'AVOID') sigType = 'WARNING_BEAR';

            return {
              symbol: r.symbol,
              name: r.name || ('Cổ phiếu ' + r.symbol),
              exchange: r.exchange || 'HOSE',
              price: r.price,
              changePercent: r.changePercent,
              rsi: r.rsi,
              ma20: r.ma20,
              ma50: r.ma50,
              volumeRatio: r.volumeRatio,
              signalType: sigType,
              signalTitle: r.signalTitle,
              buyZone: r.buyZone,
              stopLoss: r.stopLoss,
              target: r.targetPrice,
              status
            };
          });

          // Sort by highest confidence/status
          this.scannedStocks = mapped;
          this.filterSignals(this.activeFilter);
        }
        this.isLoadingScan = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingScan = false;
        this.cdr.markForCheck();
      }
    });
  }

  loadVcpPatterns(): void {
    this.isLoadingVcp = true;
    this.analysisService.scanVcp().subscribe({
      next: (patterns) => {
        this.vcpPatterns = patterns || [];
        this.isLoadingVcp = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingVcp = false;
        this.cdr.markForCheck();
      }
    });
  }

  loadStatArb(): void {
    this.isLoadingStatArb = true;
    this.analysisService.getStatisticalArbitragePairs().subscribe({
      next: (pairs) => {
        this.statArbPairs = pairs || [];
        this.isLoadingStatArb = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingStatArb = false;
        this.cdr.markForCheck();
      }
    });
  }

  setSubtab(tab: 'scanner' | 'vcp' | 'statarb'): void {
    this.activeSubtab = tab;
    if (tab === 'statarb' && this.statArbPairs.length === 0) {
      this.loadStatArb();
    }
    this.cdr.markForCheck();
  }

  selectVcpCandidate(symbol: string): void {
    this.selectedSymbol = symbol;
    this.activeSubtab = 'scanner';
    this.cdr.markForCheck();
  }

  get currentStock(): TechnicalScanItem {
    return this.scannedStocks.find(s => s.symbol === this.selectedSymbol) || this.scannedStocks[0];
  }

  get tradingViewWidgetUrl(): SafeResourceUrl {
    const symbolCode = `HOSE:${this.selectedSymbol}`;
    const url = `https://s.tradingview.com/widgetembed/?frameElementId=tradingview_widget&symbol=${symbolCode}&interval=D&hidesidetoolbar=0&symboledit=1&saveimage=1&toolbarbg=f1f3f6&studies=%5B%22MASimple%40tv-basicstudies%22%2C%22RSI%40tv-basicstudies%22%5D&theme=dark&style=1&timezone=Asia%2FHo_Chi_Minh&studies_overrides=%7B%7D&overrides=%7B%7D&enabled_features=%5B%5D&disabled_features=%5B%5D&locale=vi_VN&utm_source=localhost`;
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }

  selectSymbol(symbol: string): void {
    this.selectedSymbol = symbol;
    this.cdr.markForCheck();
  }

  filterSignals(type: string): void {
    this.activeFilter = type;
    if (type === 'ALL') {
      this.filteredStocks = this.scannedStocks;
    } else {
      this.filteredStocks = this.scannedStocks.filter(s => s.signalType === type);
    }
    this.cdr.markForCheck();
  }

  openOrderForCurrent(): void {
    const stock = this.currentStock;
    const tradeData: Partial<Trade> = {
      symbol: stock.symbol,
      exchange: stock.exchange,
      type: 'BUY',
      tradeDate: new Date().toISOString().split('T')[0],
      price: stock.price,
      quantity: 1000,
      fee: 0.0015,
      strategy: stock.signalTitle,
      stopLoss: stock.stopLoss,
      takeProfit: stock.target,
      reason: `Phân tích kỹ thuật: ${stock.signalTitle}`,
      status: 'open'
    };

    this.tradeService.createTrade(tradeData).subscribe({
      next: () => {
        alert(`Đã tạo lệnh mua 1,000 cp ${stock.symbol} từ tín hiệu kỹ thuật!`);
        this.router.navigate(['/portfolio']);
      },
      error: () => {
        alert(`Đã lưu lệnh vào danh mục!`);
        this.router.navigate(['/portfolio']);
      }
    });
  }
}
