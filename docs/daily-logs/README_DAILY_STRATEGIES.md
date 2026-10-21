# 📅 Nhật Ký & Kế Hoạch Tác Chiến Thực Tế Theo Ngày (Daily Trading Journal & Battle Plans)
> **Thư mục:** `daily_strategies/`  
> **Mục đích:** Lưu trữ toàn bộ nhật ký giao dịch thực địa, kế hoạch tác chiến từng phiên và báo cáo đánh giá hiệu suất bot theo từng ngày giao dịch trên sàn HOSE/HNX.

---

## 🗂️ Danh Mục Các Hồ Sơ Tác Chiến Đã Ghi Chép

| Ngày GD | File Hồ Sơ | Phân Loại | Tóm Tắt Tình Huống Thị Trường & Quyết Định |
| :---: | :--- | :---: | :--- |
| **08/10/2026** (Day 1) | [`BOT_ASSESSMENT_REPORT.md`](file:///c:/Users/Windows/Desktop/chungkhoan/daily_strategies/BOT_ASSESSMENT_REPORT.md) | **Báo Cáo Tổng Kết Phiên** | VN-Index rơi tự do **-14.42 điểm (-0.82%)** vào phiên chiều. Bot kích hoạt DEFCON-1 lúc 14:44, kiên quyết từ chối giải ngân vào bão, bảo toàn vẹn nguyên **100.000.000 VNĐ tiền mặt (0 đồng lỗ)**. |
| **08/10/2026** (Day 1) | [`NHAT_KY_KINH_NGHIEM_NGAY_08_10_2026.md`](file:///c:/Users/Windows/Desktop/chungkhoan/daily_strategies/NHAT_KY_KINH_NGHIEM_NGAY_08_10_2026.md) | **Nhật Ký Thực Địa** | Phân tích hiện tượng "kéo trụ xả danh mục" sáng xanh chiều sập; mổ xẻ lý do tại sao nhóm Dầu khí (PLX +4.14%, PVT +3.42%) tăng mạnh nhưng thuật toán OBI chặn mua vì tường bán đè gom hàng. |
| **09/10/2026** (Day 2) | [`CHIEN_LUOC_NGAY_2_09_10_2026.md`](file:///c:/Users/Windows/Desktop/chungkhoan/daily_strategies/CHIEN_LUOC_NGAY_2_09_10_2026.md) | **Kế Hoạch Tác Chiến** | Kế hoạch đối chiếu 2 kịch bản nhóm Dầu khí (Bán bù vs Đua tiếp); Kích hoạt module vũ khí **Oversold Bounce Detector** sẵn sàng bắt đáy khi chỉ số hoảng loạn quá đà. |

---

## 🧭 Phân Tách Giữa Tài Liệu Hệ Thống và Nhật Ký Tác Chiến

- 🏛️ **Tài liệu cốt lõi dự án & Công thức toán kiếm tiền:** Lưu trữ tại [`docs/`](file:///c:/Users/Windows/Desktop/chungkhoan/docs)
  - [`TONG_QUAN_DU_AN_VA_CONG_THUC_KIEM_TIEN.md`](file:///c:/Users/Windows/Desktop/chungkhoan/docs/TONG_QUAN_DU_AN_VA_CONG_THUC_KIEM_TIEN.md) — Master Whitepaper & 12 công thức kiếm tiền.
  - [`ARCHITECTURE.md`](file:///c:/Users/Windows/Desktop/chungkhoan/docs/ARCHITECTURE.md) — Kiến trúc 8 sub-packages chuẩn hóa.
  - [`QUANT_ALGORITHMS.md`](file:///c:/Users/Windows/Desktop/chungkhoan/docs/QUANT_ALGORITHMS.md) — Cơ sở toán học định lượng chuyên sâu.
  - [`CHIEN_LUOC_MONETIZATION_VA_HIEN_TRANG.md`](file:///c:/Users/Windows/Desktop/chungkhoan/docs/CHIEN_LUOC_MONETIZATION_VA_HIEN_TRANG.md) — 4 mô hình thương mại hóa tạo doanh thu.
- ⏱️ **Nhật ký & Kế hoạch theo ngày:** Lưu trữ tại thư mục này (`daily_strategies/`). Mỗi phiên giao dịch mới sẽ được tạo file tương ứng để theo dõi tiến độ thực chiến.
