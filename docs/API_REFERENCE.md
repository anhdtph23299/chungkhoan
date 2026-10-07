# Danh Mục API RESTful & Luồng Sự Kiện (API Reference)
> **Dự án:** VNTrade Pro — Tài Liệu Tích Hợp Hệ Thống

---

## 1. Thông Tin Cơ Bản (General Info)
- **Base URL:** `http://localhost:8080`
- **Frontend Origin Cho Phép:** `http://localhost:4200`
- **Định dạng dữ liệu:** `Content-Type: application/json; charset=UTF-8`
- **Mã phản hồi chuẩn:**
  - `200 OK`: Yêu cầu thành công.
  - `204 No Content`: Xóa hoặc cập nhật thành công không trả về body.
  - `400 Bad Request`: Dữ liệu đầu vào không hợp lệ.
  - `404 Not Found`: Không tìm thấy tài nguyên.
  - `500 Internal Server Error`: Lỗi xử lý backend.

---

## 2. Danh Mục Endpoint Chi Tiết

### 2.1. Phân Hệ Giao Dịch & Danh Mục (`TradeController`)
| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/api/trades` | Lấy toàn bộ danh sách lệnh trong hệ thống (sắp xếp mới nhất trước). |
| `GET` | `/api/trades/summary` | Lấy tổng quan tài sản ròng (NAV, Tổng vốn đầu tư, Lãi/lỗ, Tỷ lệ thắng). |
| `POST` | `/api/trades` | Mở vị thế mua mới thủ công. |
| `PUT` | `/api/trades/{id}/close` | Đóng toàn bộ vị thế của một mã cổ phiếu. |
| `POST` | `/api/trades/{id}/harvest` | Chốt lời chủ động 50% khối lượng và dời Stop Loss lên điểm hòa vốn. |

#### Ví dụ Response: `GET /api/trades/summary`
```json
{
  "totalInvested": 100000000,
  "currentValue": 100000000,
  "totalPnl": 0,
  "totalPnlPercent": 0.0000,
  "openPositions": 0,
  "totalTrades": 0,
  "winningTrades": 0,
  "winRate": 0.0
}
```

---

### 2.2. Phân Hệ Robot Tự Động (`BotController`)
| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/api/bot/status` | Lấy trạng thái hoạt động, chế độ, vốn và các ngưỡng an toàn của bot. |
| `POST` | `/api/bot/start` | Kích hoạt bot chạy tự động săn tìm tín hiệu. |
| `POST` | `/api/bot/stop` | Tạm dừng bot (không mở vị thế mới). |
| `POST` | `/api/bot/reset-capital` | Đặt lại số vốn ban đầu (truyền `?capital=100000000`). |
| `POST` | `/api/bot/setup-paper` | Khởi tạo môi trường Paper Trading chuẩn bị cho phiên giao dịch Ngày 1. |
| `GET` | `/api/bot/logs` | Lấy danh sách 50 log hoạt động gần nhất của robot. |

#### Ví dụ Response: `GET /api/bot/status`
```json
{
  "running": true,
  "mode": "LIVE_PAPER_MONEY",
  "capital": 100000000,
  "dailyTarget": 1500000,
  "circuitBreakerLimit": 2000000,
  "todayRealizedPnl": 0,
  "todayTradesCount": 0,
  "recentLogs": []
}
```

---

### 2.3. Phân Hệ Dữ Liệu Thị Trường & Báo Giá (`WatchlistController`, `CandleController`)
| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/api/stock/market-indices` | Lấy điểm số thật 4 chỉ số: VN-INDEX, VN30, HNX-INDEX, UPCOM từ VNDirect. |
| `GET` | `/api/stock/quote/{symbol}` | Lấy báo giá chi tiết hiện tại của một mã (giá, thay đổi, vol, sàn). |
| `GET` | `/api/stock/quotes` | Lấy báo giá hàng loạt (truyền danh sách `?symbols=FPT,HPG,SSI,MWG`). |
| `GET` | `/api/candles/history/{symbol}` | Lấy danh sách nến thật lịch sử (truyền `?days=120`). |

#### Ví dụ Response: `GET /api/stock/market-indices`
```json
[
  {
    "code": "VNINDEX",
    "name": "VN-INDEX",
    "value": 1753.39,
    "change": -5.69,
    "changePercent": -0.32,
    "volume": 485847590,
    "timestamp": "2026-10-07T22:00:33"
  },
  {
    "code": "VN30",
    "name": "VN30",
    "value": 1894.20,
    "change": -4.62,
    "changePercent": -0.24,
    "volume": 196062089,
    "timestamp": "2026-10-07T22:00:33"
  }
]
```

---

### 2.4. Phân Hệ Kiểm Thử Định Chế (`BacktestController`)
| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/api/backtest/institutional/{symbol}` | Chạy backtest nến thật có ràng buộc T+2.5, trừ thuế phí và tách mẫu. |
| `GET` | `/api/backtest/walk-forward-optimization/{symbol}` | Phân tích tối ưu hóa tịnh tiến (Walk-Forward Analysis). |
| `GET` | `/api/backtest/deflated-sharpe/{symbol}` | Kiểm toán tỷ số Deflated Sharpe Ratio khử lỗi ăn may. |
| `GET` | `/api/backtest/monte-carlo-stress/{symbol}` | Chạy 1,000 kịch bản ngẫu nhiên đánh giá rủi ro sụt giảm tối đa. |

#### Tham số truy vấn: `GET /api/backtest/institutional/{symbol}`
- `candles`: Số lượng nến lịch sử cần kiểm thử (mặc định: `180`).
- `strategy`: Chiến lược áp dụng (mặc định: `VCP_INSTITUTIONAL_BREAKOUT`).
- `capital`: Số vốn kiểm thử (mặc định: `100000000`).
- `stopLoss`: Tỷ lệ cắt lỗ ban đầu % (mặc định: `7.0`).
- `takeProfit`: Tỷ lệ chốt lời mục tiêu % (mặc định: `15.0`).

---

### 2.5. Phân Hệ Dòng Tiền & Mục Tiêu Thu Nhập (`DailyIncomeController`)
| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/api/income/today` | Báo cáo chi tiết thu nhập trong ngày và trạng thái phiên giao dịch. |
| `GET` | `/api/income/history` | Lịch sử thu nhập các ngày trước đó (truyền `?days=7`). |
| `GET` | `/api/income/wealth-projection` | Dự phóng gia tăng tài sản 90 ngày theo mô phỏng Monte Carlo. |
| `GET` | `/api/income/smart-money` | Danh sách top cổ phiếu đang được dòng tiền lớn gom hàng mạnh nhất. |

#### Ví dụ Response: `GET /api/income/today`
```json
{
  "reportDate": "2026-10-07",
  "dailyRealizedProfit": 0,
  "dailyUnrealizedProfit": 0,
  "totalDailyNetProfit": 0,
  "dailyTarget": 1500000,
  "targetAchievementPercent": 0.0,
  "marketStatus": "MARKET_CLOSED",
  "dailyStatusMessage": "🌙 NGOÀI GIỜ GIAO DỊCH: Thị trường đã đóng cửa. Hệ thống đã chuẩn bị sẵn sàng cho phiên giao dịch sáng mai!",
  "closedTradesToday": [],
  "openPositionsSummary": []
}
```

---

### 2.6. Phân Hệ Quản Trị Rủi Ro (`RiskController`)
| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/risk/position-size` | Tính toán số lượng cổ phiếu tối ưu nên mua dựa trên mức rủi ro NAV. |
| `GET` | `/api/risk/portfolio-limits` | Lấy các giới hạn rủi ro: trần ngành 35%, số mã tối đa, rủi ro ngày. |
| `GET` | `/api/risk/black-swan-scenarios` | Mô phỏng tổn thất danh mục dưới các cú sốc vĩ mô lịch sử. |
| `GET` | `/api/risk/portfolio-var` | Tính toán Value-at-Risk 95% và Conditional VaR 99%. |

---

### 2.7. Luồng Sự Kiện Thời Gian Thực (Server-Sent Events - SSE)
*Endpoint:* `GET /api/stream/sse`

Frontend kết nối trực tiếp qua `EventSource` để nhận luồng đẩy dữ liệu tức thì từ backend mà không cần polling liên tục:

| Sự kiện (Event Name) | Dữ liệu trả về (Payload) | Khi nào kích hoạt |
| :--- | :--- | :--- |
| `QUOTE_TICK` | Báo giá cổ phiếu mới nhất | Khi giá thị trường thay đổi |
| `BOT_LOG` | Chuỗi log hoạt động và số PnL | Mỗi khi bot hoàn thành một bước xử lý |
| `BOT_TRADE_OPENED` | Thông tin chi tiết lệnh mua vừa mở | Khi bot quét thấy điểm mua chuẩn và giải ngân |
| `TRADE_CLOSED` | Thông tin lệnh vừa chốt lời hoặc cắt lỗ | Khi vị thế chạm SL, TP hoặc bán chủ động |
| `ALERT_FIRED` | Thông điệp cảnh báo rủi ro | Khi phát hiện tường bán cản hoặc bẫy kê ảo |
