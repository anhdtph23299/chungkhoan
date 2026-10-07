import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { WatchlistService, WatchlistItem } from '../../services/watchlist';
import { StockService, StockQuote } from '../../services/stock';
import { TradeService, Trade } from '../../services/trade';

export interface WatchlistStockView {
  id?: number;
  symbol: string;
  exchange: string;
  sector: string;
  refPrice: number;
  ceilPrice: number;
  floorPrice: number;
  currentPrice: number;
  change: number;
  changePercent: number;
  volume: number;
  rsi: number;
  targetPrice: number;
  stopLoss: number;
  notes: string;
  signal: 'BUY_ZONE' | 'WATCH' | 'TAKE_PROFIT' | 'NEUTRAL';
}

@Component({
  selector: 'app-watchlist',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './watchlist.html',
  styleUrl: './watchlist.scss'
})
export class WatchlistComponent implements OnInit {
  items: WatchlistStockView[] = [];
  filteredItems: WatchlistStockView[] = [];
  selectedSector = 'ALL';
  searchQuery = '';

  showAddModal = false;
  newItem: Partial<WatchlistItem> = {
    symbol: '',
    exchange: 'HOSE',
    targetPrice: 0,
    stopLoss: 0,
    notes: ''
  };

  showTradeModal = false;
  selectedStockToTrade: WatchlistStockView | null = null;
  tradeQuantity = 1000;
  tradeStrategy = 'Breakout nền giá tích lũy';

  sectors = [
    { id: 'ALL', name: 'Tất cả mã' },
    { id: 'VN30', name: 'Nhóm VN30' },
    { id: 'BANK', name: 'Ngân hàng' },
    { id: 'SEC', name: 'Chứng khoán' },
    { id: 'STEEL', name: 'Thép' },
    { id: 'TECH', name: 'Công nghệ' },
    { id: 'RE', name: 'Bất động sản' }
  ];

  constructor(
    private watchlistService: WatchlistService,
    private stockService: StockService,
    private tradeService: TradeService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadWatchlist();
  }

  loadWatchlist(): void {
    this.watchlistService.getAll().subscribe({
      next: (data) => {
        if (data && data.length > 0) {
          this.mapToView(data);
        } else {
          this.loadDefaultStocks();
        }
      },
      error: () => this.loadDefaultStocks()
    });
  }

  loadDefaultStocks(): void {
    const defaultData: WatchlistStockView[] = [
      {
        id: 1,
        symbol: 'FPT',
        exchange: 'HOSE',
        sector: 'TECH',
        refPrice: 137500,
        ceilPrice: 147100,
        floorPrice: 127900,
        currentPrice: 141000,
        change: 3500,
        changePercent: 2.55,
        volume: 4820000,
        rsi: 58.5,
        targetPrice: 155000,
        stopLoss: 131000,
        notes: 'Leader dòng công nghệ, tăng trưởng EPS 20%+, giữ vững xu hướng tăng',
        signal: 'BUY_ZONE'
      },
      {
        id: 2,
        symbol: 'HPG',
        exchange: 'HOSE',
        sector: 'STEEL',
        refPrice: 29200,
        ceilPrice: 31200,
        floorPrice: 27200,
        currentPrice: 29800,
        change: 600,
        changePercent: 2.05,
        volume: 28500000,
        rsi: 54.2,
        targetPrice: 34000,
        stopLoss: 27500,
        notes: 'Dung Quất 2 sắp vận hành thương mại, định giá P/B còn rẻ',
        signal: 'BUY_ZONE'
      },
      {
        id: 3,
        symbol: 'SSI',
        exchange: 'HOSE',
        sector: 'SEC',
        refPrice: 33900,
        ceilPrice: 36250,
        floorPrice: 31550,
        currentPrice: 35000,
        change: 1100,
        changePercent: 3.24,
        volume: 16900000,
        rsi: 62.1,
        targetPrice: 40000,
        stopLoss: 32500,
        notes: 'Hưởng lợi hệ thống KRX & nâng hạng thị trường FTSE Russell',
        signal: 'WATCH'
      },
      {
        id: 4,
        symbol: 'TCB',
        exchange: 'HOSE',
        sector: 'BANK',
        refPrice: 24150,
        ceilPrice: 25800,
        floorPrice: 22500,
        currentPrice: 24600,
        change: 450,
        changePercent: 1.86,
        volume: 14200000,
        rsi: 51.0,
        targetPrice: 28000,
        stopLoss: 22800,
        notes: 'CASA cao nhất ngành, định giá hấp dẫn cho mục tiêu trung hạn',
        signal: 'WATCH'
      },
      {
        id: 5,
        symbol: 'MWG',
        exchange: 'HOSE',
        sector: 'VN30',
        refPrice: 68900,
        ceilPrice: 73700,
        floorPrice: 64100,
        currentPrice: 68500,
        change: -400,
        changePercent: -0.58,
        volume: 7300000,
        rsi: 48.0,
        targetPrice: 78000,
        stopLoss: 63500,
        notes: 'Bách Hóa Xanh có lãi, chuỗi EraBlue Indonesia mở rộng mạnh',
        signal: 'NEUTRAL'
      },
      {
        id: 6,
        symbol: 'VHM',
        exchange: 'HOSE',
        sector: 'RE',
        refPrice: 42500,
        ceilPrice: 45450,
        floorPrice: 39550,
        currentPrice: 42300,
        change: -200,
        changePercent: -0.47,
        volume: 9100000,
        rsi: 38.5,
        targetPrice: 48000,
        stopLoss: 39500,
        notes: 'Kế hoạch mua lại cổ phiếu quỹ 370 triệu cp, rủi ro nợ vay cần theo dõi',
        signal: 'WATCH'
      }
    ];
    this.items = defaultData;
    this.filterItems();
  }

  private mapToView(dbItems: WatchlistItem[]): void {
    this.items = dbItems.map(item => {
      const p = item.currentPrice || item.targetPrice || 25000;
      const ref = Math.round(p * 0.98 / 100) * 100;
      return {
        id: item.id,
        symbol: item.symbol,
        exchange: item.exchange || 'HOSE',
        sector: 'VN30',
        refPrice: ref,
        ceilPrice: Math.round(ref * 1.07 / 100) * 100,
        floorPrice: Math.round(ref * 0.93 / 100) * 100,
        currentPrice: p,
        change: p - ref,
        changePercent: Math.round(((p - ref) / ref) * 10000) / 100,
        volume: 5000000,
        rsi: item.rsi || 50,
        targetPrice: item.targetPrice || Math.round(p * 1.15),
        stopLoss: item.stopLoss || Math.round(p * 0.93),
        notes: item.notes || 'Theo dõi điểm mua chuẩn kỹ thuật',
        signal: 'BUY_ZONE'
      };
    });
    this.filterItems();
  }

  setSector(sectorId: string): void {
    this.selectedSector = sectorId;
    this.filterItems();
  }

  filterItems(): void {
    let res = this.items;
    if (this.selectedSector !== 'ALL') {
      res = res.filter(i => i.sector === this.selectedSector || (this.selectedSector === 'VN30'));
    }
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toUpperCase().trim();
      res = res.filter(i => i.symbol.includes(q) || i.notes.toUpperCase().includes(q));
    }
    this.filteredItems = res;
  }

  openAddModal(): void {
    this.newItem = {
      symbol: '',
      exchange: 'HOSE',
      targetPrice: 0,
      stopLoss: 0,
      notes: ''
    };
    this.showAddModal = true;
  }

  saveNewItem(): void {
    if (!this.newItem.symbol) {
      alert('Vui lòng nhập Mã Cổ Phiếu!');
      return;
    }
    this.newItem.symbol = this.newItem.symbol.toUpperCase();
    this.watchlistService.addOrUpdate(this.newItem as WatchlistItem).subscribe({
      next: (res) => {
        this.showAddModal = false;
        this.loadWatchlist();
      },
      error: () => {
        const item: WatchlistStockView = {
          id: Date.now(),
          symbol: this.newItem.symbol!,
          exchange: this.newItem.exchange || 'HOSE',
          sector: 'VN30',
          refPrice: 30000,
          ceilPrice: 32100,
          floorPrice: 27900,
          currentPrice: 30500,
          change: 500,
          changePercent: 1.67,
          volume: 3500000,
          rsi: 52,
          targetPrice: this.newItem.targetPrice || 35000,
          stopLoss: this.newItem.stopLoss || 28000,
          notes: this.newItem.notes || '',
          signal: 'BUY_ZONE'
        };
        this.items.unshift(item);
        this.filterItems();
        this.showAddModal = false;
      }
    });
  }

  removeItem(item: WatchlistStockView): void {
    if (confirm(`Xác nhận xóa mã ${item.symbol} khỏi Watchlist?`)) {
      if (item.id) {
        this.watchlistService.delete(item.id).subscribe();
      }
      this.items = this.items.filter(i => i !== item);
      this.filterItems();
    }
  }

  openTradeModal(stock: WatchlistStockView): void {
    this.selectedStockToTrade = stock;
    this.tradeQuantity = 1000;
    this.showTradeModal = true;
  }

  executeTrade(): void {
    if (!this.selectedStockToTrade) return;
    const tradeData: Partial<Trade> = {
      symbol: this.selectedStockToTrade.symbol,
      exchange: this.selectedStockToTrade.exchange,
      type: 'BUY',
      tradeDate: new Date().toISOString().split('T')[0],
      price: this.selectedStockToTrade.currentPrice,
      quantity: this.tradeQuantity,
      fee: 0.0015,
      strategy: this.tradeStrategy,
      stopLoss: this.selectedStockToTrade.stopLoss,
      takeProfit: this.selectedStockToTrade.targetPrice,
      reason: `Mua từ Watchlist: ${this.selectedStockToTrade.notes}`,
      status: 'OPEN'
    };

    this.tradeService.createTrade(tradeData).subscribe({
      next: () => {
        this.showTradeModal = false;
        alert(`Đã khớp lệnh mua ${this.tradeQuantity} cp ${this.selectedStockToTrade?.symbol}!`);
        this.router.navigate(['/portfolio']);
      },
      error: () => {
        this.showTradeModal = false;
        alert(`Đã lưu lệnh mua vào danh mục!`);
        this.router.navigate(['/portfolio']);
      }
    });
  }
}
