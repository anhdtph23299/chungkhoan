import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TradeService, Trade } from '../../services/trade';

export interface JournalEntry {
  id?: number;
  symbol: string;
  tradeDate: string;
  closeDate: string;
  type: string;
  buyPrice: number;
  sellPrice: number;
  quantity: number;
  pnl: number;
  pnlPercent: number;
  strategy: string;
  psychology: string;
  lessonLearned: string;
}

@Component({
  selector: 'app-journal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './journal.html',
  styleUrl: './journal.scss'
})
export class JournalComponent implements OnInit {
  entries: JournalEntry[] = [];
  filteredEntries: JournalEntry[] = [];
  filterStatus = 'ALL'; // ALL, WIN, LOSS
  searchQuery = '';

  showAddModal = false;
  newEntry: Partial<JournalEntry> = {
    symbol: '',
    tradeDate: new Date().toISOString().split('T')[0],
    closeDate: new Date().toISOString().split('T')[0],
    buyPrice: 0,
    sellPrice: 0,
    quantity: 1000,
    strategy: 'Breakout nền giá tích lũy',
    psychology: 'Tuân thủ đúng kế hoạch',
    lessonLearned: ''
  };

  constructor(private tradeService: TradeService) {}

  ngOnInit(): void {
    this.loadJournal();
  }

  loadJournal(): void {
    this.tradeService.getAllTrades().subscribe({
      next: (trades) => {
        const closed = trades.filter(t => t.status === 'CLOSED');
        if (closed && closed.length > 0) {
          this.entries = closed.map(t => ({
            id: t.id,
            symbol: t.symbol,
            tradeDate: t.tradeDate,
            closeDate: t.closeDate || t.tradeDate,
            type: t.type,
            buyPrice: t.price,
            sellPrice: t.closePrice || t.price,
            quantity: t.closeQuantity || t.quantity,
            pnl: t.pnl || 0,
            pnlPercent: t.pnlPercent || 0,
            strategy: t.strategy || 'Breakout',
            psychology: t.notes || 'Bình tĩnh, tuân thủ kỷ luật',
            lessonLearned: t.reason || 'Kỷ luật chốt lời đúng hạn'
          }));
          this.applyFilter();
        } else {
          this.initMockJournal();
        }
      },
      error: () => this.initMockJournal()
    });
  }

  initMockJournal(): void {
    this.entries = [
      {
        id: 1,
        symbol: 'FPT',
        tradeDate: '2026-08-01',
        closeDate: '2026-08-28',
        type: 'BUY',
        buyPrice: 125000,
        sellPrice: 142000,
        quantity: 1000,
        pnl: 17000000,
        pnlPercent: 13.6,
        strategy: 'Breakout đỉnh lịch sử + Sóng AI',
        psychology: 'Tự tin nắm giữ qua các nhịp rung lắc, không bán non',
        lessonLearned: 'Cổ phiếu leader dòng tiền lớn luôn tăng bền bỉ nhất, kiên nhẫn mang lại thành quả.'
      },
      {
        id: 2,
        symbol: 'MBB',
        tradeDate: '2026-08-10',
        closeDate: '2026-09-12',
        type: 'BUY',
        buyPrice: 22000,
        sellPrice: 25300,
        quantity: 2000,
        pnl: 6600000,
        pnlPercent: 15.0,
        strategy: 'Canslim tăng trưởng LN + MA20',
        psychology: 'Chốt lời từng phần 50% ở target 1 và 50% ở target 2',
        lessonLearned: 'Chiến thuật chốt lời từng phần giúp bảo toàn tâm lý và khóa lợi nhuận.'
      },
      {
        id: 3,
        symbol: 'VND',
        tradeDate: '2026-08-15',
        closeDate: '2026-08-25',
        type: 'BUY',
        buyPrice: 16500,
        sellPrice: 15350,
        quantity: 1500,
        pnl: -1725000,
        pnlPercent: -6.97,
        strategy: 'Bắt đáy ngắn hạn',
        psychology: 'Dứt khoát cắt lỗ ngay khi giá gãy ngưỡng 15.4, không do dự',
        lessonLearned: 'Bắt đáy rủi ro cao hơn mua vượt đỉnh, cần tuân thủ SL 7% không được gồng lỗ!'
      },
      {
        id: 4,
        symbol: 'DGC',
        tradeDate: '2026-07-05',
        closeDate: '2026-07-28',
        type: 'BUY',
        buyPrice: 110000,
        sellPrice: 124000,
        quantity: 500,
        pnl: 7000000,
        pnlPercent: 12.73,
        strategy: 'Hưởng lợi giá photpho vàng hồi phục',
        psychology: 'Vào lệnh đúng điểm mua Pocket Pivot',
        lessonLearned: 'Điểm mua chuẩn từ nền giúp giảm thiểu rủi ro biến động ngắn hạn.'
      }
    ];
    this.applyFilter();
  }

  get totalPnl(): number {
    return this.entries.reduce((sum, e) => sum + e.pnl, 0);
  }

  get winCount(): number {
    return this.entries.filter(e => e.pnl > 0).length;
  }

  get lossCount(): number {
    return this.entries.filter(e => e.pnl < 0).length;
  }

  get winRate(): number {
    return this.entries.length > 0 ? (this.winCount / this.entries.length) * 100 : 0;
  }

  get profitFactor(): number {
    const totalWin = this.entries.filter(e => e.pnl > 0).reduce((sum, e) => sum + e.pnl, 0);
    const totalLoss = Math.abs(this.entries.filter(e => e.pnl < 0).reduce((sum, e) => sum + e.pnl, 0));
    return totalLoss > 0 ? totalWin / totalLoss : totalWin > 0 ? 99 : 0;
  }

  applyFilter(): void {
    let res = this.entries;
    if (this.filterStatus === 'WIN') res = res.filter(e => e.pnl > 0);
    if (this.filterStatus === 'LOSS') res = res.filter(e => e.pnl < 0);
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toUpperCase().trim();
      res = res.filter(e => e.symbol.includes(q) || e.strategy.toUpperCase().includes(q) || e.lessonLearned.toUpperCase().includes(q));
    }
    this.filteredEntries = res;
  }

  setFilter(status: string): void {
    this.filterStatus = status;
    this.applyFilter();
  }

  openAddModal(): void {
    this.newEntry = {
      symbol: '',
      tradeDate: new Date().toISOString().split('T')[0],
      closeDate: new Date().toISOString().split('T')[0],
      buyPrice: 0,
      sellPrice: 0,
      quantity: 1000,
      strategy: 'Breakout nền giá tích lũy',
      psychology: 'Tuân thủ kế hoạch',
      lessonLearned: ''
    };
    this.showAddModal = true;
  }

  saveJournalEntry(): void {
    if (!this.newEntry.symbol || !this.newEntry.buyPrice || !this.newEntry.sellPrice) {
      alert('Vui lòng điền mã, giá mua và giá bán!');
      return;
    }
    const buy = this.newEntry.buyPrice;
    const sell = this.newEntry.sellPrice;
    const qty = this.newEntry.quantity || 1000;
    const pnl = (sell - buy) * qty;
    const pnlPercent = ((sell - buy) / buy) * 100;

    const entry: JournalEntry = {
      id: Date.now(),
      symbol: this.newEntry.symbol.toUpperCase(),
      tradeDate: this.newEntry.tradeDate || new Date().toISOString().split('T')[0],
      closeDate: this.newEntry.closeDate || new Date().toISOString().split('T')[0],
      type: 'BUY',
      buyPrice: buy,
      sellPrice: sell,
      quantity: qty,
      pnl: pnl,
      pnlPercent: Math.round(pnlPercent * 100) / 100,
      strategy: this.newEntry.strategy || 'Chiến lược chuẩn',
      psychology: this.newEntry.psychology || 'Ổn định',
      lessonLearned: this.newEntry.lessonLearned || 'Kỷ luật đầu tư'
    };

    this.entries.unshift(entry);
    this.applyFilter();
    this.showAddModal = false;
  }
}
