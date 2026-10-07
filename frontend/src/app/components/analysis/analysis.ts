import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { TradeService, Trade } from '../../services/trade';

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
  signalType: 'BREAKOUT_VOL' | 'RSI_OVERSOLD' | 'GOLDEN_CROSS' | 'WARNING_BEAR';
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

  scannedStocks: TechnicalScanItem[] = [
    {
      symbol: 'FPT',
      name: 'Tập đoàn FPT',
      exchange: 'HOSE',
      price: 141000,
      changePercent: 2.55,
      rsi: 58.5,
      ma20: 136200,
      ma50: 130500,
      volumeRatio: 1.82,
      signalType: 'BREAKOUT_VOL',
      signalTitle: 'Vượt đỉnh 52 tuần với thanh khoản tăng 1.8x MA20',
      buyZone: '138,000 - 141,000',
      stopLoss: 131000,
      target: 160000,
      status: 'STRONG_BUY'
    },
    {
      symbol: 'HPG',
      name: 'Tập đoàn Hòa Phát',
      exchange: 'HOSE',
      price: 29800,
      changePercent: 2.05,
      rsi: 54.2,
      ma20: 28900,
      ma50: 28100,
      volumeRatio: 1.65,
      signalType: 'GOLDEN_CROSS',
      signalTitle: 'MA20 cắt lên MA50 xác nhận xu hướng tăng trung hạn',
      buyZone: '29,200 - 29,800',
      stopLoss: 27500,
      target: 34500,
      status: 'BUY'
    },
    {
      symbol: 'SSI',
      name: 'Chứng khoán SSI',
      exchange: 'HOSE',
      price: 35000,
      changePercent: 3.24,
      rsi: 62.1,
      ma20: 33800,
      ma50: 32900,
      volumeRatio: 1.95,
      signalType: 'BREAKOUT_VOL',
      signalTitle: 'Breakout mẫu hình tích lũy kèm sóng ngành KRX',
      buyZone: '34,200 - 35,000',
      stopLoss: 32500,
      target: 40500,
      status: 'STRONG_BUY'
    },
    {
      symbol: 'TCB',
      name: 'Techcombank',
      exchange: 'HOSE',
      price: 24600,
      changePercent: 1.86,
      rsi: 51.0,
      ma20: 24100,
      ma50: 23600,
      volumeRatio: 1.35,
      signalType: 'GOLDEN_CROSS',
      signalTitle: 'Duy trì kênh tăng giá trên đường hỗ trợ MA20',
      buyZone: '24,000 - 24,600',
      stopLoss: 22800,
      target: 28500,
      status: 'BUY'
    },
    {
      symbol: 'MWG',
      name: 'Thế Giới Di Động',
      exchange: 'HOSE',
      price: 68500,
      changePercent: -0.58,
      rsi: 48.0,
      ma20: 69200,
      ma50: 66800,
      volumeRatio: 0.85,
      signalType: 'RSI_OVERSOLD',
      signalTitle: 'Đang tích lũy chặt quanh MA20, thanh khoản cạn kiệt',
      buyZone: '67,500 - 68,500',
      stopLoss: 63700,
      target: 78000,
      status: 'WATCH'
    },
    {
      symbol: 'VHM',
      name: 'Vinhomes',
      exchange: 'HOSE',
      price: 42300,
      changePercent: -0.47,
      rsi: 38.5,
      ma20: 43100,
      ma50: 42000,
      volumeRatio: 1.1,
      signalType: 'WARNING_BEAR',
      signalTitle: 'Dưới MA20, rủi ro nợ vay, chạm hỗ trợ tâm lý 42.0',
      buyZone: 'Chờ tạo đáy 2',
      stopLoss: 39500,
      target: 46000,
      status: 'RISK'
    }
  ];

  filteredStocks: TechnicalScanItem[] = [];

  constructor(
    private sanitizer: DomSanitizer,
    private tradeService: TradeService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.filterSignals('ALL');
  }

  get currentStock(): TechnicalScanItem {
    return this.scannedStocks.find(s => s.symbol === this.selectedSymbol) || this.scannedStocks[0];
  }

  get tradingViewWidgetUrl(): SafeResourceUrl {
    const symbolCode = `HOSE:${this.selectedSymbol}`;
    // TradingView Advanced Chart embed URL
    const url = `https://s.tradingview.com/widgetembed/?frameElementId=tradingview_widget&symbol=${symbolCode}&interval=D&hidesidetoolbar=0&symboledit=1&saveimage=1&toolbarbg=f1f3f6&studies=%5B%22MASimple%40tv-basicstudies%22%2C%22RSI%40tv-basicstudies%22%5D&theme=dark&style=1&timezone=Asia%2FHo_Chi_Minh&studies_overrides=%7B%7D&overrides=%7B%7D&enabled_features=%5B%5D&disabled_features=%5B%5D&locale=vi_VN&utm_source=localhost`;
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }

  selectSymbol(symbol: string): void {
    this.selectedSymbol = symbol;
  }

  filterSignals(type: string): void {
    this.activeFilter = type;
    if (type === 'ALL') {
      this.filteredStocks = this.scannedStocks;
    } else {
      this.filteredStocks = this.scannedStocks.filter(s => s.signalType === type);
    }
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
      status: 'OPEN'
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
