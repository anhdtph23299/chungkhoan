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
1. Đảm bảo cổng Backend **8080** và cổng Frontend **4200** đang chạy ổn định.
2. Kiểm tra log khởi động:
   ```text
   Hệ thống khởi động chế độ Live Trace Ngày 1: Không nạp lệnh mẫu giả, sẵn sàng chờ thị trường mở cửa!
   ```
3. Truy cập nhanh kiểm tra trạng thái qua terminal:
   ```bash
   curl.exe http://localhost:8080/api/bot/status
   ```
   **Kỳ vọng:** `running: true`, `mode: "LIVE_PAPER_MONEY"`, `capital: 100000000`, `todayTradesCount: 0`.

### ✅ Bước 2: Kiểm Tra Nguồn Dữ Liệu Báo Giá Thật (08:45 - 08:55)
1. Kiểm tra 4 chỉ số thị trường:
   ```bash
   curl.exe http://localhost:8080/api/stock/market-indices
   ```
   **Kỳ vọng:** Điểm số các chỉ số VN-INDEX, VN30, HNX-INDEX, UPCOM trả về dạng số thực (không null, không lỗi 404).

### ✅ Bước 3: Trực Canh Phiên Sáng (09:00 - 11:30)
1. Mở giao diện tại tab **Robot Giao Dịch**: `http://localhost:4200/bot`.
2. Giữ nguyên tab để theo dõi luồng thông báo thời gian thực qua Server-Sent Events (SSE).
3. Khi đồng hồ điểm **09:00**, thị trường bước vào phiên ATO:
   - Robot bắt đầu kích hoạt chu kỳ quét tìm điểm mua chuẩn định chế CANSLIM và VCP.
   - Nếu phát hiện tường bán đè giá lớn hoặc bẫy kê mua ảo của lái (Spoofing), robot sẽ ghi nhận log cảnh báo và bỏ qua mã đó.
   - Nếu mã cổ phiếu thỏa mãn toàn bộ 8 bộ lọc định lượng, robot tự động khớp lệnh mua với quy mô chuẩn được tính toán theo ATR và Half-Kelly.

### ✅ Bước 4: Giám Sát Phiên Chiều & Khóa T+2.5 (13:00 - 14:45)
1. Vào buổi chiều, lượng cổ phiếu mua từ phiên T-2 chính thức về tài khoản của toàn thị trường, thường tạo ra các đợt rung lắc mạnh.
2. Các lệnh robot vừa mua trong ngày hôm nay sẽ tự động được gán cờ **Khóa Thanh Khoản T+2.5** (chưa thể bán trong ngày).
3. Đến phiên ATC (14:30 - 14:45), robot cập nhật giá đóng cửa chính thức và tính toán lãi/lỗ chuẩn xác của ngày.

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
  curl.exe http://localhost:8080/api/trades/summary
  ```
- **Xem báo cáo thu nhập ngày:**
  ```bash
  curl.exe http://localhost:8080/api/income/today
  ```
- **Tạm dừng robot khẩn cấp:**
  ```bash
  curl.exe -X POST http://localhost:8080/api/bot/stop
  ```
- **Kích hoạt lại robot:**
  ```bash
  curl.exe -X POST http://localhost:8080/api/bot/start
  ```
- **Thiết lập lại Paper Trading 100 Triệu:**
  ```bash
  curl.exe -X POST "http://localhost:8080/api/bot/setup-paper?capital=100000000"
  ```
