import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TradeService, Trade, PortfolioSummary } from '../../services/trade';

@Component({
  selector: 'app-portfolio',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './portfolio.html',
  styleUrl: './portfolio.scss'
})
export class PortfolioComponent implements OnInit {
  summary: PortfolioSummary = {
    totalInvested: 150000000,
    currentValue: 168500000,
    totalPnl: 18500000,
    totalPnlPercent: 12.33,
    openPositions: 3,
    totalTrades: 12,
    winningTrades: 8,
    winRate: 66.7
  };

  cashBalance = 45000000; // Tiền mặt phòng thủ 45tr
  openPositions: Trade[] = [];
  closedPositions: Trade[] = [];

  // Close Position Modal
  showCloseModal = false;
  selectedTradeToClose: Trade | null = null;
  closeData = {
    closePrice: 0,
    closeDate: new Date().toISOString().split('T')[0],
    closeQuantity: 0,
    reason: 'Đạt mục tiêu chốt lời 15%',
    notes: 'Kỷ luật chốt lời đúng kế hoạch, không tham lam'
  };

  constructor(private tradeService: TradeService) {}

  ngOnInit(): void {
    this.loadPortfolio();
  }

  loadPortfolio(): void {
    this.tradeService.getPortfolioSummary().subscribe({
      next: (res) => {
        if (res && res.totalTrades > 0) this.summary = res;
      },
      error: () => console.log('Using default summary')
    });

    this.tradeService.getAllTrades().subscribe({
      next: (trades) => {
        if (trades && trades.length > 0) {
          this.openPositions = trades.filter(t => t.status === 'OPEN');
          this.closedPositions = trades.filter(t => t.status === 'CLOSED');
        } else {
          this.initMockPortfolio();
        }
      },
      error: () => this.initMockPortfolio()
    });
  }

  private initMockPortfolio(): void {
    this.openPositions = [
      {
        id: 1,
        symbol: 'FPT',
        exchange: 'HOSE',
        type: 'BUY',
        tradeDate: '2026-09-20',
        price: 132000,
        quantity: 800,
        fee: 0.0015,
        strategy: 'Breakout đỉnh 52 tuần',
        stopLoss: 122700,
        takeProfit: 152000,
        status: 'OPEN',
        pnl: 7200000,
        pnlPercent: 6.82
      },
      {
        id: 2,
        symbol: 'HPG',
        exchange: 'HOSE',
        type: 'BUY',
        tradeDate: '2026-09-24',
        price: 28500,
        quantity: 2000,
        fee: 0.0015,
        strategy: 'Pullback hỗ trợ MA50',
        stopLoss: 26500,
        takeProfit: 33000,
        status: 'OPEN',
        pnl: 2600000,
        pnlPercent: 4.56
      },
      {
        id: 3,
        symbol: 'SSI',
        exchange: 'HOSE',
        type: 'BUY',
        tradeDate: '2026-09-28',
        price: 33800,
        quantity: 1500,
        fee: 0.0015,
        strategy: 'Sóng ngành Chứng khoán đón KRX',
        stopLoss: 31400,
        takeProfit: 39000,
        status: 'OPEN',
        pnl: 1800000,
        pnlPercent: 3.55
      }
    ];

    this.closedPositions = [
      {
        id: 4,
        symbol: 'MBB',
        exchange: 'HOSE',
        type: 'BUY',
        tradeDate: '2026-08-10',
        price: 22000,
        quantity: 2000,
        fee: 0.0015,
        strategy: 'Canslim tăng trưởng lợi nhuận',
        closeDate: '2026-09-12',
        closePrice: 25300,
        closeQuantity: 2000,
        status: 'CLOSED',
        pnl: 6600000,
        pnlPercent: 15.0,
        reason: 'Đạt target 15% chốt lời kỷ luật'
      },
      {
        id: 5,
        symbol: 'VND',
        exchange: 'HOSE',
        type: 'BUY',
        tradeDate: '2026-08-15',
        price: 16500,
        quantity: 1500,
        fee: 0.0015,
        strategy: 'Bắt đáy ngắn hạn',
        closeDate: '2026-08-25',
        closePrice: 15350,
        closeQuantity: 1500,
        status: 'CLOSED',
        pnl: -1725000,
        pnlPercent: -6.97,
        reason: 'Cắt lỗ kỷ luật 7% khi thủng hỗ trợ'
      }
    ];
  }

  get totalHoldingsValue(): number {
    return this.openPositions.reduce((sum, p) => sum + (p.price * p.quantity + (p.pnl || 0)), 0);
  }

  get totalNAV(): number {
    return this.totalHoldingsValue + this.cashBalance;
  }

  get stockWeight(): number {
    return this.totalNAV > 0 ? (this.totalHoldingsValue / this.totalNAV) * 100 : 0;
  }

  get cashWeight(): number {
    return 100 - this.stockWeight;
  }

  getHoldingWeight(pos: Trade): number {
    const val = pos.price * pos.quantity + (pos.pnl || 0);
    return this.totalHoldingsValue > 0 ? (val / this.totalHoldingsValue) * 100 : 0;
  }

  openCloseModal(trade: Trade): void {
    this.selectedTradeToClose = trade;
    const currentPrice = trade.price + (trade.pnl || 0) / trade.quantity;
    this.closeData = {
      closePrice: Math.round(currentPrice),
      closeDate: new Date().toISOString().split('T')[0],
      closeQuantity: trade.quantity,
      reason: (trade.pnl || 0) >= 0 ? 'Đạt mục tiêu chốt lời kế hoạch' : 'Cắt lỗ bảo vệ vốn 7%',
      notes: 'Tuân thủ kỷ luật giao dịch'
    };
    this.showCloseModal = true;
  }

  confirmCloseTrade(): void {
    if (!this.selectedTradeToClose || !this.selectedTradeToClose.id) return;
    const pnl = (this.closeData.closePrice - this.selectedTradeToClose.price) * this.closeData.closeQuantity;
    const pnlPercent = ((this.closeData.closePrice - this.selectedTradeToClose.price) / this.selectedTradeToClose.price) * 100;

    const payload: Partial<Trade> = {
      closePrice: this.closeData.closePrice,
      closeDate: this.closeData.closeDate,
      closeQuantity: this.closeData.closeQuantity,
      pnl: pnl,
      pnlPercent: Math.round(pnlPercent * 100) / 100,
      reason: this.closeData.reason,
      notes: this.closeData.notes,
      status: 'CLOSED'
    };

    this.tradeService.closeTrade(this.selectedTradeToClose.id, payload).subscribe({
      next: () => {
        this.showCloseModal = false;
        alert(`Đã chốt vị thế ${this.selectedTradeToClose?.symbol}! P&L: ${pnl >= 0 ? '+' : ''}${pnl.toLocaleString()} đ`);
        this.loadPortfolio();
      },
      error: () => {
        // Local state update
        const closed = { ...this.selectedTradeToClose!, ...payload };
        this.openPositions = this.openPositions.filter(p => p.id !== this.selectedTradeToClose!.id);
        this.closedPositions.unshift(closed);
        this.showCloseModal = false;
        alert(`Đã đóng vị thế ${this.selectedTradeToClose?.symbol}!`);
      }
    });
  }
}
