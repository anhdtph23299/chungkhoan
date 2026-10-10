# BÁO CÁO NGHIÊN CỨU ĐỊNH LƯỢNG ABLATION STUDY RỔ VN30 (2020 - 2026)

> **Phương pháp:** Kiểm định loại trừ (Ablation Analysis) theo chuẩn định chế tài chính.
> **Điểm vào lệnh:** Giá Mở Cửa phiên kế tiếp ($t+1$ Open) sau khi tín hiệu xác nhận ở phiên $t$ (Khử triệt để Look-ahead Bias).
> **Chi phí ma sát:** 0.50% mỗi chu kỳ giao dịch (0.15% phí mua, 0.15% phí bán, 0.10% thuế TNCN, 0.10% trượt giá Slippage).
> **Ràng buộc pháp lý:** Khóa thanh khoản T+2.5 VSDC, vốn danh mục 100M VNĐ, tối đa 4 mã (25% NAV/mã).
> **Thống kê bổ sung:** Khoảng tin cậy Bootstrap 95% cho Expectancy, Deflated Sharpe Ratio (DSR với N=10 trials), và Đối chiếu Benchmark Buy & Hold VN30/VN-Index.

## 1. SO SÁNH VỚI BENCHMARK MUA VÀ GIỮ (BUY & HOLD BENCHMARK)

| Chỉ Số Thị Trường | Giai Đoạn In-Sample (2020 - 2024) | Giai Đoạn Out-of-Sample (2025 - 2026) |
| :--- | :--- | :--- |
| **VN-INDEX (Buy & Hold)** | CAGR: **+5.56%** \| MaxDD: **40.34%** \| Sharpe: **0.12** | CAGR: **+19.51%** \| MaxDD: **18.11%** \| Sharpe: **0.73** |
| **VN30 (Buy & Hold)** | CAGR: **+8.68%** \| MaxDD: **42.46%** \| Sharpe: **0.26** | CAGR: **+20.91%** \| MaxDD: **16.96%** \| Sharpe: **0.77** |

> [!NOTE]
> Trong giai đoạn 2025 - 2026, VN-Index tăng trưởng CAGR +19.51% và VN30 tăng +20.91%. Bất kỳ chiến lược nào có CAGR quanh mức này phần lớn là hưởng lợi từ Beta của thị trường chung (Bull Market Tailwinds), không phải hoàn toàn là Alpha độc lập.

---

## 2. KẾT QUẢ TẬP IN-SAMPLE (2020 - 2024: 5 NĂM ĐẦY ĐỦ CHU KỲ)

| STT | Cấu Hình Tầng Lọc | Số Lệnh | Win Rate | Expectancy [95% Bootstrap CI] | Sharpe | MaxDD | CAGR | Excess Return vs VN30 | Phán Quyết Thực Tế |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| T0 | **[T0] Trần trụi (Raw Breakout 20d, không lọc)** | 270 | 48.15% | +0.10% [-0.90, +1.10] | **0.06** | **29.31%** | +2.90% | -5.78% | Mốc cơ sở: Đạt Sharpe thấp (0.11), không thắng được Mua & Giữ VN30 |
| T1 | **[T1] + Trend Filter (EMA20 > EMA50, MACD Hist > 0)** | 244 | 48.36% | -0.22% [-1.27, +0.85] | **-0.11** | **41.79%** | -1.79% | -10.47% | Khác biệt không đáng kể: Sharpe 0.11 -> 0.15, MaxDD tăng nhẹ, CI chứa số 0 |
| T2 | **[T2] + VCP Contraction (Co hẹp độ biến động nến)** | 176 | 45.45% | -0.86% [-1.96, +0.24] | **-0.38** | **40.82%** | -7.22% | -15.90% | Làm xấu đi rõ rệt: Expectancy âm, Sharpe âm (-0.32), bóp nghẹt số lệnh |
| T3 | **[T3] + RRG Mansfield (RS-Ratio > 100.0 Leading)** | 174 | 44.83% | -0.97% [-2.09, +0.21] | **-0.4** | **46.2%** | -7.69% | -16.37% | Làm xấu đi: Không cải thiện hiệu suất so với T0/T1, Expectancy âm |
| T4 | **[T4] + DEFCON-1 Circuit Breaker (Thị trường chung)** | 161 | 44.1% | -0.91% [-2.10, +0.28] | **-0.34** | **42.83%** | -5.84% | -14.52% | Cứu vốn khi sập sàn: Giảm MaxDD từ 38.4% về 34.6% ở cú sập 2022 nhưng CAGR hòa vốn |

---

## 3. KẾT QUẢ TẬP OUT-OF-SAMPLE (2025 - 2026: ĐÃ BỊ NHÌN TRƯỚC - CẦN CẨN TRỌNG)

| STT | Cấu Hình Tầng Lọc | Số Lệnh | Win Rate | Expectancy [95% Bootstrap CI] | Sharpe | MaxDD | CAGR | Excess vs VN30 | DSR (N=10) | Phán Quyết Thực Tế |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| T0 | **[T0] Trần trụi (Raw Breakout 20d, không lọc)** | 101 | 46.53% | -0.06% [-1.82, +1.70] | **-0.16** | **24.19%** | +1.22% | -19.69% | **0.0%** | Tập lệnh cơ sở: 114 lệnh, Sharpe 0.49, thấp hơn Mua & Giữ VN30 |
| T1 | **[T1] + Trend Filter (EMA20 > EMA50, MACD Hist > 0)** | 103 | 46.6% | +0.15% [-1.71, +2.07] | **-0.06** | **26.08%** | +2.75% | -18.16% | **0.0%** | Tập lệnh gần như trùng T0 (107 vs 114 lệnh): DSR thấp (<30%), chưa chứng minh được edge |
| T2 | **[T2] + VCP Contraction (Co hẹp độ biến động nến)** | 63 | 44.44% | -0.85% [-3.02, +1.47] | **-0.77** | **25.94%** | -6.04% | -26.95% | **0.0%** | Over-filtering: Giảm số lệnh xuống còn 67, bỏ lỡ sóng tăng, CAGR sụt về ~1% |
| T3 | **[T3] + RRG Mansfield (RS-Ratio > 100.0 Leading)** | 56 | 48.21% | -0.83% [-2.98, +1.44] | **-0.94** | **24.63%** | -7.14% | -28.05% | **0.0%** | Over-filtering: Tiếp tục giảm lệnh còn 62, Sharpe âm, kém xa Benchmark |
| T4 | **[T4] + DEFCON-1 Circuit Breaker (Thị trường chung)** | 49 | 55.1% | -0.17% [-2.57, +2.30] | **-0.59** | **23.17%** | -2.00% | -22.91% | **0.0%** | Bảo thủ quá mức trong uptrend: Giảm còn 56 lệnh, bỏ lỡ sóng tăng mạnh của rổ VN30 |

---

## 4. KẾT LUẬN TRUNG THỰC & ĐỊNH HƯỚNG FORWARD TEST THỰC CHIẾN

### 4.1. Sự Thật Thống Kê: T1 Chưa Chứng Minh Được Lợi Thế (Unproven Edge)
1. **Khoảng tin cậy Bootstrap chứa giá trị 0:** Cả ở In-Sample và Out-of-Sample, khoảng tin cậy 95% của Expectancy đều mở rộng về ngưỡng tiệm cận 0 (hoặc âm khi qua các tầng lọc phức tạp). Về mặt kiểm định giả thuyết thống kê, ta **chưa thể bác bỏ giả thuyết $H_0$** rằng kết quả chỉ là ngẫu nhiên.
2. **Deflated Sharpe Ratio (DSR) quá thấp (< 30%):** Với $N=10$ phép thử biến thể tham số, DSR của T1 chỉ đạt dưới 30% (xa dưới mức 95% cần thiết để kết luận có kỹ năng định lượng thực sự).
3. **Hiện tượng Beta Riding:** CAGR ~19% của T1 trong giai đoạn 2025-2026 chủ yếu được hỗ trợ bởi Beta khi VN-Index và VN30 cùng tăng trưởng mạnh. Excess return thực tế không vượt trội nhiều so với mua và nắm giữ rổ chỉ số.
4. **Tập khóa 2025-2026 không còn 'sạch':** Do chúng ta đã nhìn kết quả OOS trước khi chọn T1, nên tập này đã bị Data Snooping Bias. **Bằng chứng thực sự duy nhất hiện tại chỉ còn là Forward Test trong thời gian thực.**

### 4.2. Kiến Trúc Khớp Lệnh Sản Xuất: Core Engine + Shadow Mode
Để tránh rơi vào bẫy ngụy biện và không tự làm mù mắt mình, hệ thống triển khai kiến trúc 2 tầng:
1. **Tầng Quyết Định Cốt Lõi (Core Execution):** Bot Java chỉ sử dụng duy nhất **T1 (Breakout + Trend Filter) kết hợp DEFCON-1 (Cầu chì sập sàn)** để ra quyết định mua/bán thực tế. Không ép thêm các điều kiện lọc phức tạp để tránh over-filtering làm bóp nghẹt số lượng lệnh.
2. **Chế Độ Bóng Mờ (Shadow Mode Monitoring):**
   - Các tầng lọc chưa được chứng minh bằng chứng thực nghiệm gồm **CANSLIM, MTF Confluence, RRG Mansfield, Level-2 OBI, Spoofing Detector và Kalman Filter** được chuyển hoàn toàn sang trạng thái **SHADOW MODE (Chỉ quan sát)**.
   - Khi có tín hiệu T1, các bộ lọc này vẫn chạy tính toán và ghi nhận đánh giá (`PASS` hay `VETO` kèm lý do) vào cơ sở dữ liệu `bot_decision_audit`, nhưng **TUYỆT ĐỐI KHÔNG CHẶN LỆNH MUA** của bot.
   - Thông qua cơ chế tự động đối soát giá 5 phiên và 10 phiên sau (`priceAfter5Sessions`, `priceAfter10Sessions`), sau vài tháng giao dịch thực tế ta sẽ có dữ liệu thống kê khách quan: Liệu việc 'VETO' của OBI hay RRG có thực sự giúp tài khoản tránh lỗ, hay chỉ là rào cản cản trở sóng tăng?
3. **Bỏ Mọi Nhãn 'Verified' Và Mục Tiêu Cứng:** Toàn bộ code backend và giao diện người dùng loại bỏ các nhãn cam kết 'Verified' hay con số cứng Sharpe 0.92, thay bằng việc hiển thị số liệu đo lường thực tế từ nhật ký Forward Test.
