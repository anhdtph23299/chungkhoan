# VNTrade Pro | Hệ Thống Tự Động Giao Dịch & Quản Lý Danh Mục Chứng Khoán VN

> **Hệ thống giao dịch định lượng (Quant Algorithmic Trading)** kết nối trực tiếp dữ liệu nến thật sàn HOSE/HNX (qua VNDirect Dchart API), tích hợp kiểm soát rủi ro pháp lý T+2.5, bước giá sàn, chống bão thị trường DEFCON-1, và tối ưu hóa danh mục theo tiêu chuẩn tổ chức.

---

## 🚀 Hướng Dẫn Kéo Code & Chạy Trên Máy Mới (Quick Start)

### 1. Yêu Cầu Môi Trường (Prerequisites)
- **Java:** JDK 21 trở lên (`java -version`)
- **Maven:** 3.8+ (`mvn -version`)
- **Node.js:** v18 hoặc v20+ (`node -v`)
- **Git:** (`git --version`)

---

### 2. Kéo Code (Git Clone)
```bash
git clone https://github.com/anhdtph23299/chungkhoan.git
cd chungkhoan
```

---

### 3. Khởi Chạy Backend (Spring Boot 3.3.5)
Mở cửa sổ dòng lệnh thứ nhất (Terminal 1):
```bash
cd backend
mvn clean spring-boot:run
```
- **Backend Port:** `http://localhost:8085`
- **Kiểm tra trạng thái Bot:** `http://localhost:8085/api/bot/status`
- **Kiểm tra Phái sinh T+0:** `http://localhost:8085/api/futures/quote`
- **H2 Database Console:** `http://localhost:8085/h2-console`
  - JDBC URL: `jdbc:h2:file:./data/vntrade_db`
  - User: `sa`
  - Password: *(để trống)*

---

### 4. Khởi Chạy Frontend (Angular 22 / TypeScript)
Mở cửa sổ dòng lệnh thứ hai (Terminal 2):
```bash
cd frontend
npm install
npx ng serve --port 4200
```
- **Dashboard Web App:** [http://localhost:4200/dashboard](http://localhost:4200/dashboard)
- **Robot Giao Dịch Cơ Sở:** [http://localhost:4200/bot](http://localhost:4200/bot)
- **Terminal Phái Sinh T+0:** [http://localhost:4200/futures](http://localhost:4200/futures)

---

## 📚 Hệ Thống Tài Liệu Review Toàn Diện (Documentation Sitemap)

Tất cả tài liệu kỹ thuật, kế hoạch tác chiến và công thức định lượng được phân tách chuyên nghiệp thành 2 khu vực:

### 🏛️ Tài Liệu Hệ Thống Cốt Lõi (Thư mục [`docs/`](file:///c:/Users/Windows/Desktop/chungkhoan/docs))
1. 📖 **[Whitepaper & 12 Công Thức Kiếm Tiền](file:///c:/Users/Windows/Desktop/chungkhoan/docs/TONG_QUAN_DU_AN_VA_CONG_THUC_KIEM_TIEN.md)**: **(Tài liệu quan trọng nhất)** Tổng quan toàn dự án và chi tiết 12 công thức toán học sinh lời.
2. 🏛️ **[Kiến Trúc Kỹ Thuật (Architecture)](file:///c:/Users/Windows/Desktop/chungkhoan/docs/ARCHITECTURE.md)**: Thiết kế 8 sub-packages chuẩn hóa, pipeline dữ liệu VNDirect Dchart, SSE stream.
3. 🔬 **[Toán Học Định Lượng (Quant Algorithms)](file:///c:/Users/Windows/Desktop/chungkhoan/docs/QUANT_ALGORITHMS.md)**: Kalman Filter, GARCH(1,1), Half-Kelly, Walk-Forward, Monte Carlo.
4. 💰 **[Chiến Lược Kiếm Tiền Thật Sự (Monetization)](file:///c:/Users/Windows/Desktop/chungkhoan/docs/CHIEN_LUOC_MONETIZATION_VA_HIEN_TRANG.md)**: 4 mô hình kinh doanh tạo dòng tiền bền vững.
5. ⚡ **[SOP Vận Hành Trực Chiến (Live Runbook)](file:///c:/Users/Windows/Desktop/chungkhoan/docs/LIVE_TRADING_RUNBOOK.md)**: Lịch trình 5 khung giờ sàn HOSE/HNX từ 08:30 đến 15:00.
6. 🖥️ **[Cẩm Nang Tính Năng Giao Diện (Features Guide)](file:///c:/Users/Windows/Desktop/chungkhoan/docs/FEATURES_GUIDE.md)**: Hướng dẫn chi tiết từng tab màn hình Web.
7. 🔌 **[Đặc Tả API Backend (API Reference)](file:///c:/Users/Windows/Desktop/chungkhoan/docs/API_REFERENCE.md)**: Danh mục toàn bộ REST API endpoints.
8. 📊 **[Báo Cáo Nghiên Cứu Ablation VN30 2020-2026](file:///c:/Users/Windows/Desktop/chungkhoan/docs/BAO_CAO_ABLATION_TEST_VN30_2020_2026.md)**: Kiểm chứng khoa học In-Sample (2020-2024) vs Out-of-Sample (2025-2026) trên 100% nến thật 30 mã VN30.

### 📅 Nhật Ký & Kế Hoạch Tác Chiến Theo Ngày (Thư mục [`daily_strategies/`](file:///c:/Users/Windows/Desktop/chungkhoan/daily_strategies))
1. 📑 **[Báo Cáo Đánh Giá Ngày 1 (Bot Assessment)](file:///c:/Users/Windows/Desktop/chungkhoan/daily_strategies/BOT_ASSESSMENT_REPORT.md)**: Tổng kết bảo toàn 100M vốn phiên thị trường rơi -14.42 điểm.
2. 📝 **[Nhật Ký Thực Địa Ngày 1 (08/10/2026)](file:///c:/Users/Windows/Desktop/chungkhoan/daily_strategies/NHAT_KY_KINH_NGHIEM_NGAY_08_10_2026.md)**: Bài học nhóm Dầu khí, bẫy sổ lệnh OBI và tâm lý xả hàng T+2.5.
3. 🎯 **[Chiến Lược Tác Chiến Ngày 2 (09/10/2026)](file:///c:/Users/Windows/Desktop/chungkhoan/daily_strategies/CHIEN_LUOC_NGAY_2_09_10_2026.md)**: Kế hoạch đối chiếu giá dầu và kích hoạt Oversold Bounce.

---

## 📊 Cấu Trúc Dự Án (Project Structure)

```
chungkhoan/
├── backend/                        # Spring Boot 3.3.5 Backend (Java 21)
│   ├── src/main/java/              # Kiến trúc Clean Service, Controller, Entity, DTO
│   │   └── com/vntrade/backend/
│   │       ├── service/            # 8 Sub-packages: calculation, decision, execution, risk, futures...
│   │       ├── controller/         # 14 REST Controllers: Bot, Futures, Trades, Analysis...
│   │       ├── entity/             # JPA Entities: Trade, PortfolioSnapshot, BotConfig...
│   │       └── repository/         # Spring Data JPA Repositories
│   └── src/test/java/              # 60 Test classes (140 Tests PASS 100%)
├── frontend/                       # Angular 22 Single Page Application
│   ├── src/app/components/         # Dashboard, Bot, Futures (T+0), Portfolio, Risk, Analysis...
│   └── src/styles.scss             # Giao diện Cyber-Quant Dark Mode
├── docs/                           # 9 Tài liệu kỹ thuật, kiến trúc, công thức & báo cáo Ablation
├── daily_strategies/               # Nhật ký giao dịch thực địa & kế hoạch tác chiến từng ngày
└── README.md                       # Hướng dẫn khởi động nhanh
```

---

## 🛡️ Các Tính Năng Định Lượng Cốt Lõi

1. **Phái sinh VN30F (T+0 Long/Short):** Động cơ giao dịch 2 chiều, ăn chênh lệch điểm số ngay trong ngày cả khi thị trường sập.
2. **Tuân thủ bước giá pháp lý HOSE/HNX:** Tự động làm tròn giá lệnh theo quy định (<10k: 10đ, 10k-50k: 50đ, >=50k: 100đ).
3. **Khóa thanh khoản T+2.5:** Đếm chính xác ngày làm việc và kiểm tra 13:00 chiều T+2 mới cho phép mở quyền bán.
4. **Radar luân chuyển ngành RRG:** Quét RS-Ratio và RS-Momentum các nhóm ngành dẫn dắt (Leading) vs tụt hậu (Lagging).
5. **Cầu chì bảo vệ thị trường DEFCON-1:** Tự động khóa 100% lệnh mua mới khi thị trường chung bán tháo.
6. **Kỷ luật già làng 7 nguyên tắc:** Cắt lỗ 7%, chốt lời 50% gặt hái tiền mặt khi lãi $\ge 10\%$, dời hòa vốn bảo vệ thành quả.

---

## ⚡ Lệnh Nhanh Kiểm Tra Hệ Thống Sau Khi Chạy
```bash
# Kiểm tra Bot trạng thái (Port 8085)
curl -s http://localhost:8085/api/bot/status

# Kiểm tra Báo giá & Độ lệch Basis Phái Sinh VN30F
curl -s http://localhost:8085/api/futures/quote

# Kiểm tra Tín hiệu Phái Sinh Real-time
curl -s http://localhost:8085/api/futures/signal

# Kiểm tra tổng kết danh mục
curl -s http://localhost:8085/api/trades/summary
```
