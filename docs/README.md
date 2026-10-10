# VNTrade Pro - Quantitative Trading & Portfolio Management System
> **Hệ thống Định lượng & Tự động Giao dịch Chứng khoán Việt Nam (HOSE / HNX / UPCOM)**

Chào mừng bạn đến với bộ tài liệu hướng dẫn toàn diện của **VNTrade Pro**. Hệ thống được thiết kế theo tiêu chuẩn quỹ định chế (Institutional Grade), tích hợp công nghệ Full-Stack hiện đại cùng các mô hình toán học định lượng chuyên sâu cho thị trường chứng khoán Việt Nam.

---

## 📚 Mục Lục Tài Liệu Hệ Thống

Thư mục `docs/` được tổ chức thành các chuyên đề rõ ràng, giúp nhà đầu tư, lập trình viên và chuyên gia định lượng dễ dàng nghiên cứu, vận hành và mở rộng:

| Tài liệu | Mô tả chi tiết |
| :--- | :--- |
| 📖 [**TONG_QUAN_DU_AN_VA_CONG_THUC_KIEM_TIEN.md**](file:///c:/Users/Windows/Desktop/chungkhoan/docs/TONG_QUAN_DU_AN_VA_CONG_THUC_KIEM_TIEN.md) | **(Master Review)** Tổng quan toàn dự án và 12 công thức toán học trade kiếm tiền thực tế. |
| 🏛️ [**ARCHITECTURE.md**](file:///c:/Users/Windows/Desktop/chungkhoan/docs/ARCHITECTURE.md) | Kiến trúc kỹ thuật 8 sub-packages chuẩn hóa, luồng dữ liệu thời gian thực (SSE), Spring Boot 3.3.5 và Angular 22. |
| 🧮 [**QUANT_ALGORITHMS.md**](file:///c:/Users/Windows/Desktop/chungkhoan/docs/QUANT_ALGORITHMS.md) | Chi tiết các thuật toán định lượng: Kalman Filter, GARCH(1,1), Half-Kelly, DSR, VCP, RRG Mansfield, OBI Sổ lệnh. |
| 💰 [**CHIEN_LUOC_MONETIZATION_VA_HIEN_TRANG.md**](file:///c:/Users/Windows/Desktop/chungkhoan/docs/CHIEN_LUOC_MONETIZATION_VA_HIEN_TRANG.md) | 4 mô hình kinh doanh tạo dòng tiền bền vững (Proprietary Trading, VIP Telegram Signals, Broker IB, Copy-Trading). |
| 🖥️ [**FEATURES_GUIDE.md**](file:///c:/Users/Windows/Desktop/chungkhoan/docs/FEATURES_GUIDE.md) | Cẩm nang trải nghiệm và vận hành từng phân hệ: Dashboard, Phái sinh VN30F (T+0), Bot Trading, Portfolio, Risk. |
| 🔌 [**API_REFERENCE.md**](file:///c:/Users/Windows/Desktop/chungkhoan/docs/API_REFERENCE.md) | Danh mục đầy đủ tất cả REST API endpoints, tham số truy vấn, mẫu JSON request/response và sự kiện SSE. |
| ⏱️ [**LIVE_TRADING_RUNBOOK.md**](file:///c:/Users/Windows/Desktop/chungkhoan/docs/LIVE_TRADING_RUNBOOK.md) | Quy trình vận hành thực chiến cho phiên giao dịch thực tế: Khung giờ ATO/Liên tục/ATC và xử lý sự cố. |
| 📅 [**daily_strategies/**](file:///c:/Users/Windows/Desktop/chungkhoan/daily_strategies) | **(Thư mục riêng)** Hồ sơ nhật ký giao dịch thực địa, bài học thị trường và kế hoạch tác chiến theo từng ngày. |

---

## 🌟 Điểm Nhấn Độc Bản Của Hệ Thống

1. **100% Dữ Liệu Giá Nến Thật từ Thị Trường**:
   - Dữ liệu nến lịch sử và realtime được tích hợp trực tiếp qua VNDirect Dchart API (`dchart-api.vndirect.com.vn`), loại bỏ hoàn toàn dữ liệu ngẫu nhiên hoặc mock data.
   - Các chỉ số đại diện thị trường chuẩn: **VN-INDEX**, **VN30**, **HNX-INDEX**, **UPCOM** cập nhật theo phiên.

2. **Tuân Thủ Pháp Lý Thị Trường Chứng Khoán Việt Nam**:
   - **Ràng buộc T+2.5**: Lệnh mua chỉ được phép bán khi cổ phiếu đã thực sự về tài khoản (qua ngày T+2).
   - **Biên độ dao động trần/sàn**: HOSE (±7%), HNX (±10%), UPCOM (±15%).
   - **Khấu trừ chi phí giao dịch minh bạch**: Phí môi giới 0.15% chiều mua, 0.15% chiều bán và thuế thu nhập cá nhân (TNCN) 0.10% khi bán.

3. **Cơ Chế Phòng Vệ DEFCON-1 & Cầu Chì Bảo Toàn Vốn (Circuit Breaker)**:
   - Tự động nhận diện rủi ro sụp đổ thị trường (Market Crash Protection).
   - Cắt lỗ tự động theo mô hình dao động co giãn GARCH(1,1), dời Trailing Stop lên điểm hòa vốn khi vị thế đạt lãi +7% và Chandelier Stop theo ATR(14) khi lãi >12%.

---

## 🚀 Hướng Dẫn Khởi Chạy Nhanh (Quick Start)

### 1. Yêu cầu môi trường
- **Java**: OpenJDK 21 LTS
- **Maven**: 3.9+
- **Node.js**: v20+ & npm 10+
- **Angular CLI**: v19+

### 2. Khởi động Backend (Spring Boot 3.3.5)
```bash
cd backend
mvn spring-boot:run
```
- Server chạy tại: `http://localhost:8080`
- Cơ sở dữ liệu nhúng H2 Web Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/vntrade_db`, User: `sa`, Password: *trống*)

### 3. Khởi động Frontend (Angular 19)
```bash
cd frontend
npm start
```
- Truy cập giao diện ứng dụng tại: `http://localhost:4200`
