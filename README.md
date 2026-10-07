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
- **Backend Port:** `http://localhost:8080`
- **Kiểm tra trạng thái Bot:** `http://localhost:8080/api/bot/status`
- **H2 Database Console:** `http://localhost:8080/h2-console`
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

---

## 📊 Cấu Trúc Dự Án (Project Structure)

```
chungkhoan/
├── backend/                        # Spring Boot 3.3.5 Backend (Java 21)
│   ├── src/main/java/              # Kiến trúc Clean Service, Controller, Entity, DTO
│   │   └── com/vntrade/backend/
│   │       ├── service/            # Động cơ định lượng: RRG, GARCH, Kalman, VCP, Risk, Bot...
│   │       ├── controller/         # REST APIs: Bot, Trades, Income, Backtest, Analysis...
│   │       ├── entity/             # JPA Entities: Trade, PortfolioSnapshot, BotConfig...
│   │       └── repository/         # Spring Data JPA Repositories
│   └── src/test/java/              # 54 Unit & Integration Tests (100% Pass)
├── frontend/                       # Angular 22 Single Page Application
│   ├── src/app/                    # Components & Pages: Dashboard, Trades, Backtest, Screener...
│   └── src/styles.scss             # Giao diện hiện đại Dark/Light Mode, Glassmorphism
├── docs/                           # Tài liệu hướng dẫn định lượng & quy trình SOP
│   ├── QUANT_ALGORITHMS.md         # Toán học định lượng & công thức thuật toán
│   └── SYSTEM_GUIDE.md             # Hướng dẫn chi tiết tính năng toàn hệ thống
├── .gitignore                      # Cấu hình bỏ qua thư viện và file tạm
└── README.md                       # Hướng dẫn cài đặt & vận hành
```

---

## 🛡️ Các Tính Năng Định Lượng Cốt Lõi

1. **Dữ liệu thật 100% từ sàn:** Lấy nến lịch sử qua VNDirect API, loại bỏ hoàn toàn dữ liệu giả.
2. **Tuân thủ bước giá pháp lý HOSE/HNX:** Tự động làm tròn giá lệnh theo quy định (<10k: 10đ, 10k-50k: 50đ, >=50k: 100đ).
3. **Khóa thanh khoản T+2.5:** Đếm chính xác ngày làm việc (bỏ qua T7, CN) và kiểm tra 13:00 chiều T+2 mới cho phép mở quyền bán.
4. **Radar luân chuyển ngành RRG:** Quét RS-Ratio và RS-Momentum các nhóm ngành dẫn dắt (Leading) vs tụt hậu (Lagging).
5. **Cầu chì bảo vệ thị trường DEFCON-1:** Theo dõi VN-INDEX thời gian thực và đếm cổ phiếu sàn trắng bán tháo để tự động khóa lệnh mua.
6. **Kỷ luật già làng 7 nguyên tắc:** Cắt lỗ dứt khoát 7%, chốt lời 50% gặt hái tiền mặt khi lãi $\ge 10\%$, dời hòa vốn bảo vệ thành quả.

---

## ⚡ Lệnh Nhanh Kiểm Tra Hệ Thống Sau Khi Chạy
```bash
# Kiểm tra Bot trạng thái Day 1 (Vốn 100M sạch)
curl -s http://localhost:8080/api/bot/status

# Kiểm tra tổng kết danh mục
curl -s http://localhost:8080/api/trades/summary

# Kiểm tra Radar xoay tua dòng tiền ngành RRG
curl -s http://localhost:8080/api/analysis/sector-rotation
```
