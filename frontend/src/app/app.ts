import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  title = 'VN Stock Tracker';
  sidebarCollapsed = false;

  navItems = [
    { path: 'dashboard', icon: '🏠', label: 'Tổng quan' },
    { path: 'watchlist', icon: '👁️', label: 'Watchlist' },
    { path: 'portfolio', icon: '💼', label: 'Danh mục' },
    { path: 'journal', icon: '📔', label: 'Nhật ký GD' },
    { path: 'analysis', icon: '🔬', label: 'Phân tích KT' },
    { path: 'risk', icon: '🛡️', label: 'Quản lý RR' },
    { path: 'schedule', icon: '📅', label: 'Lịch GD' },
    { path: 'bot', icon: '🤖', label: 'Bot Trader' },
    { path: 'futures', icon: '⚡', label: 'Phái Sinh T+0' },
  ];

  get currentHour(): number {
    return new Date().getHours();
  }

  get isMarketOpen(): boolean {
    const now = new Date();
    const day = now.getDay(); // 0=CN, 6=T7
    const h = now.getHours();
    const m = now.getMinutes();
    if (day === 0 || day === 6) return false;
    const timeMin = h * 60 + m;
    return (timeMin >= 9 * 60 && timeMin < 11 * 60 + 30) ||
           (timeMin >= 13 * 60 && timeMin < 14 * 60 + 45);
  }

  toggleSidebar() {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }
}
