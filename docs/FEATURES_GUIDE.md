# Cẩm Nang Tính Năng & Hướng Dẫn Sử Dụng (Features Guide)
> **Dự án:** VNTrade Pro — Trải Nghiệm Người Dùng & Vận Hành Hệ Thống

---

## 1. Màn Hình Bàn Điều Khiển (Executive Dashboard)
*Đường dẫn truy cập:* `http://localhost:4200/`

Bàn điều khiển trung tâm cung cấp góc nhìn toàn diện 360 độ về sức khỏe danh mục đầu tư và biến động thị trường chứng khoán Việt Nam:

### 1.1. Bảng Chỉ Số Thị Trường Trực Tuyến (Market Indices Bar)
- Hiển thị 4 chỉ số trụ cột: **VN-INDEX**, **VN30**, **HNX-INDEX**, **UPCOM**.
- Tích hợp 100% dữ liệu thật từ VNDirect Dchart API, cập nhật điểm số, mức thay đổi (± điểm, ±%), tổng khối lượng khớp lệnh theo phiên.
- Tự động lưu cache bộ nhớ đệm 60 giây để tối ưu tốc độ tải trang.

### 1.2. Thẻ Tài Sản Định Giá Thực (Realistic NAV Cards)
- **Tổng Tài Sản Ròng (NAV)**: Phản ánh trung thực công thức:
  $$\text{NAV} = \text{Tiền mặt khả dụng} + \text{Giá trị thị trường của danh mục cổ phiếu}$$
- **Mục Tiêu Lợi Nhuận Ngày (Daily Income Engine)**:
  - Hiển thị rõ số tiền lãi đã chốt (Realized PnL) và lãi tạm tính (Unrealized PnL).
  - Tự động nhận diện trạng thái thị trường (`DANG_GIAO_DICH`, `NGHI_TRUA`, `MARKET_CLOSED`, `WEEKEND_CLOSED`) để đưa ra thông điệp chuẩn xác, không báo lãi giả ngoài giờ.
  - Tự động chia dòng tiền: 70% tái đầu tư sinh lời kép và 30% dòng tiền khả dụng có thể rút tiêu dùng.

### 1.3. Bảng Vị Thế Đang Nắm Giữ & Gặt Hái Lợi Nhuận (Harvesting)
- Danh sách cổ phiếu đang mở vị thế: Giá mua, Giá thị trường hiện tại, Lãi/Lỗ (±VNĐ, ±%), Điểm dừng lỗ (SL), Điểm chốt lời (TP).
- **Tính năng Gặt hái Lợi nhuận (Harvesting)**: Nút thao tác một chạm cho phép chốt lời chủ động 50% khối lượng khi cổ phiếu đạt mục tiêu, đồng thời tự động dời điểm cắt lỗ lên giá vốn (Breakeven Stop) cho 50% khối lượng còn lại.
- **Nút Đặt Lệnh Nhanh (Quick Trade Modal)**: Cho phép mở vị thế thủ công với kiểm tra ràng buộc rủi ro ngay trên giao diện.

---

## 2. Phân Hệ Robot Giao Dịch Tự Động (Auto-Trading Bot)
*Đường dẫn truy cập:* `http://localhost:4200/bot`

Được thiết kế nhằm loại bỏ hoàn toàn yếu tố cảm xúc (FOLO, hoảng loạn bán tháo, tiếc nuối khi chốt non), robot vận hành hoàn toàn tự động theo kỷ luật toán học:

### 2.1. Cấu Hình & Trạng Thái Bot (Control Panel)
- **Nút Bật / Tắt Robot**: Bật để kích hoạt chu kỳ quét tự động mỗi 30 giây hoặc tắt để tạm dừng vào lệnh.
- **Nút "Thiết lập Paper Trading (100.000.000 đ)"**:
  - Tự động đưa hệ thống về trạng thái sẵn sàng cho phiên giao dịch thực chiến Ngày 1.
  - Vốn khởi điểm: **100,000,000 VNĐ**.
  - Mục tiêu lợi nhuận ngày: **+1,500,000 VNĐ (+1.5% NAV)**.
  - Cầu chì cắt mạch bảo vệ vốn: **-2,000,000 VNĐ (-2.0% NAV)**.
  - Làm sạch toàn bộ các lệnh thử nghiệm cũ trong cơ sở dữ liệu.

### 2.2. Chu Kỳ Quét Đa Tầng (8-Step Institutional Scanning)
Mỗi 30 giây trong phiên giao dịch, bot tự động kích hoạt chuỗi kiểm tra khắt khe:
1. Quản trị và dời trailing stop các vị thế đang nắm giữ.
2. Kiểm tra Cầu chì sụp đổ thị trường (DEFCON-1 Crash Protection).
3. Kiểm tra hạn mức lỗ tối đa trong ngày.
4. Kiểm tra khung giờ giao dịch HOSE/HNX (09:00 - 11:30 và 13:00 - 14:45).
5. Lọc cổ phiếu đạt chuẩn CANSLIM (Grade A/B, Score $\ge 75$).
6. Kiểm tra đồng thuận xu hướng đa khung thời gian W1 - D1 - H1 (Score $\ge 65$).
7. Kiểm tra đồ thị luân chuyển dòng tiền RRG (Loại bỏ góc Lagging suy kiệt).
8. Soi vi cấu trúc sổ lệnh Level-2 (OBI) và bẫy kê mua ảo (Spoofing Detector).

### 2.3. Bảng Điều Khiển Terminal Nhật Ký (Live Terminal Console)
- Kết nối luồng thời gian thực qua Server-Sent Events (SSE).
- Ghi nhận chi tiết từng quyết định: lý do loại bỏ cổ phiếu không đạt chuẩn, cảnh báo tường bán cản giá, phát hiện bẫy kê lệnh của lái, và thông báo khớp lệnh thành công.

---

## 3. Phòng Thí Nghiệm Kiểm Thử Định Chế (Institutional Backtest Laboratory)
*Đường dẫn truy cập:* `http://localhost:4200/backtest`

Khác biệt hoàn toàn với các công cụ backtest nghiệp dư chỉ lấy giá đóng cửa đơn giản, phòng thí nghiệm định lượng của VNTrade Pro mô phỏng đầy đủ mọi ma sát thực tế của thị trường Việt Nam:

### 3.1. Dữ Liệu Nến Thật 100% (Real Historical Candles)
- Truy xuất trực tiếp dữ liệu nến lịch sử từ VNDirect Dchart (120 đến 240 phiên).
- Phản ánh đúng từng biến động giá mở cửa, cao nhất, thấp nhất, đóng cửa và khối lượng khớp lệnh thật của các mã bluechip (FPT, HPG, SSI, MWG, TCB, VHM...).

### 3.2. Kiểm Toán Ràng Buộc Khắt Khe
- **Khóa Thanh Khoản T+2.5**: Lệnh mua bắt buộc phải nắm giữ ít nhất 2 phiên trước khi được phép kích hoạt tín hiệu bán.
- **Khấu Trừ Phí & Thuế Minh Bạch**: Tự động trừ 0.15% phí mua, 0.15% phí bán và 0.10% thuế TNCN cho mỗi giao dịch.
- **Tách Mẫu Huấn Luyện (In-Sample vs Out-of-Sample)**: 70% dữ liệu đầu để tối ưu tham số và 30% dữ liệu cuối để kiểm định mù (Blind Testing).

### 3.3. Bộ Chỉ Số Đánh Giá Quỹ
- **Tỷ số Sharpe / Sortino / Calmar**: Đo lường lợi nhuận trên từng đơn vị rủi ro và biến động sụt giảm.
- **Deflated Sharpe Ratio (DSR)**: Xác minh thuật toán không bị ăn may do thử nghiệm nhiều lần.
- **Walk-Forward Efficiency (WFE)**: Đo lường độ bền vững khi áp dụng thuật toán vào tương lai.
- **Mô Phỏng Monte Carlo 1,000 Lần**: Đánh giá kịch bản xấu nhất (Maximum Drawdown) và xác suất bảo toàn vốn.

---

## 4. Phân Hệ Quản Trị Rủi Ro & Phòng Vệ (Risk Management)
*Đường dẫn truy cập:* `http://localhost:4200/risk`

- **Máy Tính Phân Bổ Vị Thế Thích Ứng (Adaptive Position Sizing)**: Nhập mã cổ phiếu, hệ thống tự động tính toán số cổ phiếu tối ưu nên mua dựa trên ATR biến động và công thức Half-Kelly.
- **Kiểm Soát Tỷ Trọng Ngành**: Giới hạn tối đa 35% NAV cho bất kỳ một nhóm ngành nào (Bất động sản, Ngân hàng, Thép, Bán lẻ...) nhằm phân tán rủi ro tập trung.
- **Kịch Bản Thiên Nga Đen (Black Swan Stress Scenario)**: Mô phỏng danh mục sẽ biến động ra sao trong các đợt sập lịch sử (như Covid-19 tháng 3/2020 hay đợt siết trái phiếu tháng 11/2022).

---

## 5. Sổ Nhật Ký Giao Dịch & Quản Lý Danh Mục (Trading Journal)
*Đường dẫn truy cập:* `http://localhost:4200/journal`

- **Phân Tích MFE (Maximum Favorable Excursion)**: Đỉnh lãi cao nhất mà lệnh từng đạt được trước khi chốt, giúp nhận biết hệ thống có chốt non hay không.
- **Phân Tích MAE (Maximum Adverse Excursion)**: Đáy lỗ sâu nhất mà lệnh phải chịu đựng trước khi quay đầu, giúp tối ưu hóa khoảng cách đặt Stop Loss.
- **Ghi Chú Tâm Lý & Bài Học Kinh Nghiệm**: Lưu lại lý do mở vị thế, trạng thái kỷ luật và các sai lầm cần tránh.
