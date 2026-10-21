# Cẩm Nang Vận Hành Giao Dịch Thực Chiến (Live Trading Runbook)
> **Dự án:** VNTrade Pro — Quy Trình Chuẩn (SOP) Dành Cho Phiên Live Trace Ngày 1 & Vận Hành Thực Tế

---

## 1. Lịch Trình Khung Giờ Giao Dịch Chuẩn Sàn Chứng Khoán Việt Nam

Để hệ thống định lượng vận hành chuẩn xác theo thực tế sàn HOSE và HNX, người vận hành cần nắm rõ các mốc thời gian pháp lý:

```mermaid
timeline
    title Khung Giờ Giao Dịch Thị Trường Chứng Khoán Cơ Sở Việt Nam
    08:30 - 08:55 : Tiền trạm & Chuẩn bị hệ thống : Khởi động Backend/Frontend, kiểm tra số dư 100M VNĐ
    09:00 - 09:15 : Phiên Khớp Lệnh Mở Cửa (ATO) : Xác định giá mở cửa HOSE, không hủy/sửa lệnh ATO
    09:15 - 11:30 : Khớp Lệnh Liên Tục Buổi Sáng : Robot quét tín hiệu mỗi 30s, kiểm tra OBI và bẫy kê ảo
    11:30 - 13:00 : Giờ Nghỉ Trưa : Thị trường tạm dừng, bot cập nhật định giá tạm thời
    13:00 - 14:30 : Khớp Lệnh Liên Tục Buổi Chiều : Dòng tiền hàng T+2.5 về tài khoản, cơ hội săn điểm bứt phá
    14:30 - 14:45 : Phiên Khớp Lệnh Đóng Cửa (ATC) : Xác định giá đóng cửa ngày, dời Trailing Stop
    14:45 - 15:00 : Thỏa thuận & Đóng sàn : Sàn UPCOM đóng lúc 15:00
    Sau 15:00     : Tổng Kết Phiên & Kiểm Toán : Đóng sổ nhật ký PnL, đối soát danh mục
```

---

## 2. Checklist Chuẩn Bị Cho Phiên Live Trace Ngày 1 (Day 1 Trace Checklist)

Phiên giao dịch Ngày 1 đánh dấu cột mốc chuyển dịch từ môi trường thử nghiệm sang chế độ **trực canh bám sát 100% thị trường thật**:

### ✅ Bước 1: Kiểm Tra Trạng Thái Hạ Tầng (08:30 - 08:45)
1. Đảm bảo cổng Backend **8085** và cổng Frontend **4200** đang chạy ổn định.
2. Kiểm tra log khởi động:
   ```text
   Hệ thống khởi động chế độ Live Trace: Sẵn sàng trực canh phiên giao dịch!
   ```
3. Truy cập nhanh kiểm tra trạng thái qua terminal:
   ```bash
   curl.exe http://localhost:8085/api/bot/status
   ```
   **Kỳ vọng:** `running: true`, `mode: "LIVE_PAPER_MONEY"`, `capital: 100000000`, `todayTradesCount: 0`.

### ✅ Bước 2: Kiểm Tra Nguồn Dữ Liệu Báo Giá & Radar Tiền Trạm (08:45 - 08:55)
1. Kiểm tra 4 chỉ số thị trường:
   ```bash
   curl.exe http://localhost:8085/api/stock/market-indices
   ```
2. Kiểm tra Radar Tiền Trạm & Đối Chiếu Giá Dầu Thế Giới:
   ```bash
   curl.exe http://localhost:8085/api/analysis/pre-market-sentiment
   ```
   **Kỳ vọng:** Trả về điểm số tâm lý mở cửa, tình trạng giá dầu Brent/WTI đêm qua và danh mục ưu tiên trực canh (PLX, PVT, BSR, FPT, HPG).
3. Kiểm tra Radar Bắt Đáy Hoảng Loạn:
   ```bash
   curl.exe http://localhost:8085/api/analysis/oversold-bounce
   ```

### ✅ Bước 3: Trực Canh Phiên Sáng (09:00 - 11:30)
1. Mở giao diện tại tab **Robot Giao Dịch**: `http://localhost:4200/bot`.
2. Giữ nguyên tab để theo dõi luồng thông báo thời gian thực qua Server-Sent Events (SSE).
3. Khi đồng hồ điểm **09:00**, thị trường bước vào phiên ATO:
   - Bot trực canh quan sát, không mở lệnh bừa bãi trong ATO (09:00 - 09:15).
   - Từ 09:15: Kích hoạt chu kỳ quét tìm điểm mua chuẩn định chế CANSLIM và VCP.
   - Nếu cổ phiếu Alpha bị tường bán lớn đè giá, bot tự động xếp vào **Hàng Đợi Rình Mồi (Breakout Queue)** tại `http://localhost:8085/api/bot/breakout-queue`.
   - Khi lực cầu tổ chức nuốt trọn tường bán (`scan.getPrice() >= wallPrice`), cơ chế **Breakout Wall Override** tự động giải ngân.

### ✅ Bước 4: Giờ Nghỉ Trưa Thông Minh (11:30 - 12:55)
- Bot tự động chuyển sang chế độ **Smart Idle**, tạm dừng quét nặng để bảo vệ 100% CPU máy tính.

### ✅ Bước 5: Giám Sát Phiên Chiều & Khóa T+2.5 (13:00 - 14:45)
1. Vào buổi chiều, lượng cổ phiếu mua từ phiên T-2 về tài khoản tạo rung lắc mạnh.
2. Các lệnh mua mới được tự động gán cờ **Khóa Thanh Khoản T+2.5**.
3. Khung giờ vàng **14:15 - 14:45**: Theo dõi dòng tiền tạo lập Big Boys và dời Trailing Stop.
4. Đến phiên ATC (14:30 - 14:45), bot chốt giá đóng cửa chính thức và lưu snapshot PnL.

---

## 3. Quy Trình Ứng Phó Sự Cố (Incident Response SOP)

| Tình huống sự cố | Triệu chứng | Cách xử lý tức thì |
| :--- | :--- | :--- |
| **API VNDirect bị trễ hoặc ngắt kết nối** | Log cảnh báo `VNDirect dchart index fetch failed`. | Hệ thống tự động kích hoạt bộ đệm In-Memory Cache (60s) và fallback về bảng giá tham chiếu an toàn. Người vận hành không cần thao tác gì. |
| **Xung đột khóa file cơ sở dữ liệu H2 (`.lock.db`)** | Backend không khởi động được do máy tính tắt ngang. | 1. Tắt tiến trình Java cũ.<br/>2. Chạy lệnh xóa file khóa: `del /q backend\data\*.lock.db`.<br/>3. Khởi động lại: `mvn spring-boot:run`. |
| **Thị trường sụt giảm mạnh bất thường (Thiên Nga Đen)** | VN-Index giảm sâu > 2.5% trong phiên. | Cơ chế **DEFCON-1 Crash Protection** tự động kích hoạt, khóa 100% lệnh mua mới để bảo toàn tuyệt đối 100M vốn. |
| **Chạm ngưỡng lỗ tối đa trong ngày (-2.0% NAV)** | Mức lỗ ngày chạm -2,000,000 VNĐ. | Cầu chì **Circuit Breaker** ngắt mạch tự động, robot ngừng giao dịch trong phần còn lại của ngày và gửi cảnh báo khẩn cấp. |

---

## 4. Các Lệnh Điều Khiển Nhanh Qua Terminal

- **Xem tóm tắt tài sản ròng:**
  ```bash
  curl.exe http://localhost:8085/api/trades/summary
  ```
- **Xem báo cáo thu nhập ngày:**
  ```bash
  curl.exe http://localhost:8085/api/income/today
  ```
- **Xem bảng so sánh hiệu suất đa kỳ (DoD, WoW, MoM, QoQ):**
  ```bash
  curl.exe http://localhost:8085/api/portfolio/performance-comparison
  ```
- **Xem hàng đợi rình mồi bứt phá:**
  ```bash
  curl.exe http://localhost:8085/api/bot/breakout-queue
  ```
- **Tạm dừng robot khẩn cấp:**
  ```bash
  curl.exe -X POST http://localhost:8085/api/bot/stop
  ```
- **Kích hoạt lại robot:**
  ```bash
  curl.exe -X POST http://localhost:8085/api/bot/start
  ```
- **Thiết lập lại Paper Trading 100 Triệu:**
  ```bash
  curl.exe -X POST "http://localhost:8085/api/bot/setup-paper?capital=100000000"
  ```
