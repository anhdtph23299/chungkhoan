import { Routes } from '@angular/router';
import { DashboardComponent } from './components/dashboard/dashboard';
import { WatchlistComponent } from './components/watchlist/watchlist';
import { PortfolioComponent } from './components/portfolio/portfolio';
import { JournalComponent } from './components/journal/journal';
import { AnalysisComponent } from './components/analysis/analysis';
import { RiskComponent } from './components/risk/risk';
import { ScheduleComponent } from './components/schedule/schedule';
import { BotComponent } from './components/bot/bot';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'watchlist', component: WatchlistComponent },
  { path: 'portfolio', component: PortfolioComponent },
  { path: 'journal', component: JournalComponent },
  { path: 'analysis', component: AnalysisComponent },
  { path: 'risk', component: RiskComponent },
  { path: 'schedule', component: ScheduleComponent },
  { path: 'bot', component: BotComponent },
  { path: '**', redirectTo: 'dashboard' }
];
