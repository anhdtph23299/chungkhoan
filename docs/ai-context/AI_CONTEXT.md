# 🤖 AI CONTEXT — VNTrade Stock Bot Project
> **⚠️ ĐỌC FILE NÀY TRƯỚC KHI LÀM BẤT KỲ TÍNH NĂNG NÀO**
> Cập nhật lần cuối: 2026-10-21 | Bot: Live Paper Trading (Forward Test)

---

## 1. TỔNG QUAN DỰ ÁN

Đây là hệ thống **trading bot tự động giấy (Paper Trading)** chạy forward test thực chiến trên thị trường chứng khoán Việt Nam (HOSE/HNX). Mục tiêu là kiểm chứng chiến lược T1 Breakout + DEFCON-1 Circuit Breaker bằng dữ liệu giá thật realtime từ VNDirect.

**Quan trọng**: Đây là paper trading (tiền ảo), KHÔNG phải real money. Nhưng mọi logic phải chuẩn xác như thật.

---

## 2. KIẾN TRÚC HỆ THỐNG

```
c:\stock\
├── backend/          ← Spring Boot 3.3.5, Java 21, port 8085
│   └── src/main/java/com/vntrade/backend/
│       ├── controller/       ← REST API endpoints
│       ├── entity/           ← JPA entities (Trade, Watchlist, ...)
│       ├── repository/       ← Spring Data JPA repos
│       ├── dto/              ← Request/Response DTOs
│       ├── service/
│       │   ├── execution/    ← CỐT LÕI: AutoTradingBotService, TradeService
│       │   ├── decision/     ← Chiến lược: VN30SignalScreenerService, CanslimRating...
│       │   ├── marketdata/   ← Dữ liệu giá: StockPriceService (VNDirect realtime)
│       │   ├── calculation/  ← Toán: Kalman, GARCH, Markowitz, Monte Carlo...
│       │   ├── risk/         ← Bảo vệ vốn: MarketCrashProtectionService, RiskService
│       │   └── portfolio/    ← MarketSchedulerService, AlertService, PortfolioHistory
│       └── config/
├── frontend/         ← Angular 17, port 4200
│   └── src/app/
│       ├── components/bot/      ← Dashboard chính: bot.html, bot.ts, bot.scss
│       ├── components/dashboard/
│       ├── components/analysis/
│       ├── components/portfolio/
│       ├── components/risk/
│       └── services/            ← bot.ts service, trade.ts service...
├── docs/             ← TÀI LIỆU (thư mục này)
│   ├── ai-context/   ← File này — AI đọc trước
│   ├── architecture/ ← Kiến trúc kỹ thuật
│   ├── api/          ← API reference
│   ├── trading-rules/← Chiến lược giao dịch
│   └── daily-logs/   ← Nhật ký giao dịch hàng ngày
└── daily_strategies/ ← (legacy, đã merge vào docs/daily-logs)
```

**Database**: H2 file-based (`backend/data/vntrade_db`). Dữ liệu persist qua restart.
**GitHub**: `https://github.com/anhdtph23299/chungkhoan` (branch `main`)

---

## 3. FILE CỐT LÕI — KHÔNG ĐƯỢC SỬA SAI

### 3.1 Backend — Các file cực kỳ nhạy cảm

| File | Vai trò | Rủi ro nếu sửa sai |
|---|---|---|
| [`AutoTradingBotService.java`](../../backend/src/main/java/com/vntrade/backend/service/execution/AutoTradingBotService.java) | **Não của bot** — vòng lặp 30s, mở/đóng lệnh, trailing stop, noon restart | Mở lệnh ảo, reset vốn nhầm, mất dữ liệu PnL |
| [`TradeService.java`](../../backend/src/main/java/com/vntrade/backend/service/execution/TradeService.java) | CRUD lệnh giao dịch, `executeMarketClose`, `executePartialClose` | Xóa nhầm lệnh đang mở, sai PnL |
| [`StockPriceService.java`](../../backend/src/main/java/com/vntrade/backend/service/marketdata/StockPriceService.java) | Lấy giá realtime từ VNDirect — nguồn dữ liệu duy nhất | Mất dữ liệu REAL → bot dùng giá STALE |
| [`MarketCrashProtectionService.java`](../../backend/src/main/java/com/vntrade/backend/service/risk/MarketCrashProtectionService.java) | DEFCON-1 circuit breaker — khóa mua khi thị trường sụp | Tắt cầu chì → bot mua vào thị trường crash |
| [`RiskService.java`](../../backend/src/main/java/com/vntrade/backend/service/risk/RiskService.java) | Tính sizing lệnh, kiểm soát sector cap 35% NAV | Giải ngân quá mức, vỡ ngành |
| [`Trade.java`](../../backend/src/main/java/com/vntrade/backend/entity/Trade.java) | Entity DB — schema của bảng giao dịch | Thay đổi field → migration fail, mất data |
| [`TradeRepository.java`](../../backend/src/main/java/com/vntrade/backend/repository/TradeRepository.java) | Custom queries: `findOpenBuyTrades()`, `findByStatus...` | Query sai → bot tính toán NAV/cash sai |

### 3.2 Frontend — File quan trọng

| File | Vai trò |
|---|---|
| [`bot.html`](../../frontend/src/app/components/bot/bot.html) | Dashboard chính — KPI cards, log console, danh mục |
| [`bot.ts`](../../frontend/src/app/components/bot/bot.ts) | Component logic — polling API, binding dữ liệu |
| [`bot.service.ts`](../../frontend/src/app/services/bot.ts) | HTTP service — gọi `/api/bot/status`, `/api/trades` |

---

## 4. QUY TẮC TUYỆT ĐỐI KHÔNG ĐƯỢC VI PHẠM

### 4.1 Quy tắc giao dịch (Trading Rules)
- ❌ **KHÔNG MỞ LỆNH trong phiên ATO (09:00 - 09:15)** — giá chưa ổn định
- ❌ **KHÔNG BÁN khi chưa đủ T+2.5** — vi phạm quy định UBCKNN/VSDC
  - T+0: Ngày mua
  - T+1: Ngày tiếp theo
  - T+2.5: Ngày thứ 3, từ 13:00 mới được bán
  - T+3+: Toàn phiên
- ❌ **KHÔNG giải ngân quá 35% NAV vào một ngành** (sector cap)
- ❌ **KHÔNG mở quá 4 vị thế đồng thời**
- ❌ **KHÔNG mua khi mất kết nối dữ liệu (STALE feed)**

### 4.2 Quy tắc code (Coding Rules)
- ❌ **KHÔNG reset `todayRealizedPnl` hoặc `todayTradesCount`** trong giờ giao dịch — làm mất tracking PnL ngày
- ❌ **KHÔNG gọi `tradeRepository.deleteAll()`** ngoài `setupPaperTrading()` — xóa sạch lịch sử
- ❌ **KHÔNG thay đổi schema của `Trade.java`** mà không chạy migration H2
- ❌ **KHÔNG sửa logic `isT25SettlementReady()`** — đây là ràng buộc pháp lý sàn HOSE
- ❌ **KHÔNG tắt `enforceMarketHours`** ở production
- ✅ Luôn dùng `QuantitativeStrategyEngine.roundToVietnameseTick()` khi tính giá mới (bước giá chuẩn HOSE)

---

## 5. TRẠNG THÁI HIỆN TẠI HỆ THỐNG (Cập nhật 21/10/2026)

### Tài khoản
- **Vốn gốc**: 100,000,000 đ
- **NAV hiện tại**: ~99,000,000 đ
- **Mode**: `LIVE_PAPER_MONEY`
- **Strategy**: `FORWARD_TEST_T1_CORE_DEFCON`

### Vị thế đang mở (3 mã)
| Mã | Ngày mua | Giá vốn | Khối lượng | SL hiện tại (Trailing) | TP |
|---|---|---|---|---|---|
| **GMD** | 15/10/2026 | 52,800 đ | 500 cp | ~53,900 đ (Trailing Tầng 2) | 57,100 đ |
| **TCB** | 15/10/2026 | 33,000 đ | 900 cp | ~33,400 đ (Trailing Tầng 1) | 35,300 đ |
| **FRT** | 20/10/2026 | 146,000 đ | 100 cp | 139,000 đ | 161,400 đ |

### Lịch sử lệnh đã đóng
| Mã | Kết quả | PnL |
|---|---|---|
| FRT (ID=1) | Cắt lỗ 14/10 | -1,855,330 đ |
| DGW (ID=2) | Cắt lỗ 14/10 | -472,140 đ |
| PLX (ID=3) | Cắt lỗ 16/10 | -1,515,220 đ |

---

## 6. TÍNH NĂNG ĐÃ IMPLEMENT

| Tính năng | File | Trạng thái |
|---|---|---|
| T1 Breakout Core với DEFCON-1 | `AutoTradingBotService.java` | ✅ Live |
| Dynamic Trailing Stop 4 tầng | `AutoTradingBotService.java` (method `applyDynamicTrailingStop`) | ✅ Live (21/10) |
| Noon Auto-Restart 12:00 | `AutoTradingBotService.java` (method `noonRestart`) | ✅ Live (21/10) |
| T+2.5 Settlement Lock | `AutoTradingBotService.java` (`isT25SettlementReady`) | ✅ Live |
| STALE Feed Protection | `AutoTradingBotService.java` | ✅ Live |
| Sector Cap 35% NAV | `RiskService.java` | ✅ Live |
| KPI Card "Tiền mặt đầu tư" | `bot.html`, `BotController.java` | ✅ Live |
| Partial Take-Profit ≥10% | `AutoTradingBotService.java` (Step 2 trong `manageOpenPositions`) | ✅ Live |
| Circuit Breaker Debounce 15' | `AutoTradingBotService.java` | ✅ Live |
| Portfolio History Snapshot | `PortfolioHistoryService.java` | ✅ Live |
| 6 Shadow Mode Filters | `AutoTradingBotService.java` (CANSLIM, MTF, RRG, OBI, Spoofing, Kalman) | ✅ Shadow Only |

---

## 7. TÍNH NĂNG CHƯA LÀM (Backlog)

| Tính năng | Ưu tiên | Ghi chú |
|---|---|---|
| Telegram Alert Bot | 🟡 Cao | `AlertService.java` đã sẵn, chỉ cần wire Telegram API |
| TradingView Chart NAV Equity Curve | 🟡 Trung bình | Tích hợp Lightweight Charts vào Angular |
| Scale-Out Partial TP (3 tầng) | 🟠 Cao | Chốt lời từng phần 1/3 tại 50%, 80% khoảng TP |

---

## 8. API ENDPOINTS CHÍNH

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/bot/status` | Trạng thái bot, NAV, cash, log |
| GET | `/api/trades` | Danh sách tất cả lệnh |
| GET | `/api/trades/summary` | Portfolio summary |
| POST | `/api/bot/start` | Bật bot |
| POST | `/api/bot/stop` | Tắt bot |
| DELETE | `/api/trades/{id}` | Xóa lệnh (NGUY HIỂM) |
| GET | `/api/analysis/pre-market-sentiment` | Phân tích trước phiên |
| GET | `/api/bot/audit-summary` | Kết quả shadow audit 6 tầng |

---

## 9. QUY TRÌNH PHÁT TRIỂN TÍNH NĂNG MỚI

Khi bạn giao tính năng mới, AI phải:

1. **Đọc file này trước** — nắm kiến trúc và quy tắc
2. **Đọc file liên quan** — tránh conflict với code hiện tại
3. **Hỏi nếu chưa rõ** — thà hỏi 1 câu còn hơn sửa hỏng production
4. **Chỉ sửa file cần thiết** — không refactor lan man sang file khác
5. **Test bằng curl** — verify API response trước khi báo cáo xong
6. **Push commit rõ ràng** — message theo format `feat:`, `fix:`, `refactor:`

---

## 10. LƯU Ý KỸ THUẬT QUAN TRỌNG

- **H2 Database** tự động persist vào `backend/data/vntrade_db.mv.db` — KHÔNG xóa file này
- **Giờ giao dịch HOSE**: 09:00-11:30 (sáng), 13:00-14:45 (chiều), Thứ 2-6
- **Bước giá HOSE**: Dưới 10k: 10đ | 10k-50k: 50đ | Trên 50k: 100đ — dùng `roundToVietnameseTick()`
- **Phí giao dịch**: Mua 0.15%, Bán 0.15% + thuế TNCN 0.1% trên giá trị bán
- **NAV = tiền mặt khả dụng + giá trị cổ phiếu theo giá thị trường hiện tại**
- **Bot chạy 2 vòng lặp song song**: `AutoTradingBotService` (30s) + `MarketSchedulerService` (60s)
