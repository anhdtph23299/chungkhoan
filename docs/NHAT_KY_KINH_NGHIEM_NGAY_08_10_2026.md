# NHẬT KÝ KINH NGHIỆM THỰC CHIẾN & PHÉP THỬ THỊ TRƯỜNG NGÀY 08/10/2026

> **Ngày lập hồ sơ:** 15:15 Chiều (Thứ Năm, 08/10/2026) — Kết thúc phiên giao dịch Day 1  
> **Tác giả:** Antigravity Quant Engine & Nhà đầu tư  
> **Chủ đề trung tâm:** *Bài học về Nhóm Dầu Khí (PLX, PVT, BSR), Cú sập -14.42 điểm của VN-Index và Phép thử đối chiếu phiên ngày mai (09/10/2026)*

---

## 1. DỮ LIỆU ĐỊA CHẤN PHIÊN HÔM NAY (08/10/2026)

### 1.1. Bối cảnh Vĩ mô & Giá dầu Quốc tế
* **Giá dầu thế giới bùng nổ:**
  * Dầu Brent (BZ=F): **103.25 USD/thùng (+3.04%)**
  * Dầu thô WTI (CL=F): **90.74 USD/thùng (+2.79%)**
* **Tác động tâm lý:** Kích hoạt dòng tiền đầu cơ tìm kiếm nơi trú ẩn tại nhóm cổ phiếu dầu khí trong nước.

### 1.2. Diễn biến Chỉ số VN-Index: Xanh Vỏ Đỏ Lòng rồi Bán Tháo Cuối Phiên
* **Sáng:** Kéo ảo chỉ số qua trụ VIC, VN-Index có lúc tăng +12 điểm lên 1,753 điểm, thanh khoản teo tóp chỉ 6,100 tỷ. Khối ngoại bán ròng -181 tỷ (xả FPT, VHM, TCB, PNJ...).
* **Chiều:** Hàng T+2.5 về tài khoản tạo áp lực xả lũ ồ ạt.
* **Đóng cửa ATC:** **VN-Index rơi tự do mất -14.42 ĐIỂM (-0.82%)**, lùi về mốc 1,738.97 điểm. Toàn bộ các nhóm trụ (Ngân hàng, Chứng khoán, Thép, BĐS) bị bán rát.

### 1.3. Bảng Giá Nhóm Dầu Khí Đi Ngược Thị Trường
* **PLX (Xăng dầu Việt Nam):** Đóng cửa **37.70 đ (+4.14%)** — Tăng mạnh nhất rổ lớn.
* **PVT (Vận tải Dầu khí):** Đóng cửa **25.70 đ (+3.42%)**.
* **BSR (Lọc hóa dầu Bình Sơn):** Đóng cửa **32.10 đ (+1.10%)**.
* **GAS:** Quanh 81.3k - 82.2k (giằng co quanh tham chiếu).

---

## 2. TẠI SAO BOT NHẬN DIỆN PLX LÀ SIÊU CỔ PHIẾU NHƯNG KHÔNG MUA?

Trong suốt phiên, Bot quét được PLX đạt **RRG Alpha Leader (Điểm tin cậy 99/100, Score 77đ)** nhưng từ chối vào lệnh vì 3 chốt chặn:

1. **Rào cản Sổ lệnh Level-2 (Order Book Imbalance - OBI):**
   * Lúc 11:22: Tường bán đè 310,700 cp tại 37,300 đ (chiếm 52.2% độ sâu).
   * Lúc 13:37: Tường bán đè tại 37,250 đ.
   * Lúc 14:42: Tường bán tiếp tục lùi lên đè tại 37,650 đ.
   * *Thuật toán hiện tại quy định:* Nếu tường bán $\ge 45\%$ độ sâu sổ lệnh, lập tức hủy lệnh mua để tránh bẫy Bull-trap.
2. **Rủi ro Bán Bù T+2.5 khi VN-Index sập sâu:**
   * Khi thị trường chung mất hơn 14 điểm, 75% cổ phiếu sẽ bị cuốn theo xu hướng thị trường.
   * Mua cổ phiếu đi ngược bão khi thị trường đang rơi là con dao hai lưỡi: Nếu giá dầu thế giới đêm nay hạ nhiệt, ngày mai nhóm dầu rất dễ bị **bán bù**, trong khi nhà đầu tư bị kẹt hàng T+2.5 không thể thoát.
3. **Hạn mức nhóm ngành trong code (RiskService.java):**
   * Toàn bộ nhóm Dầu khí đang bị xếp chung vào nhóm KHÁC, bị vướng trần 35% NAV với GMD.

---

## 3. PHÉP THỬ THỰC NGHIỆM CHO NGÀY MAI (THỨ SÁU, 09/10/2026)

Đây là phép thử quan trọng được lưu lại theo yêu cầu của Nhà đầu tư để kiểm chứng tính đúng đắn của quyết định bot:

### 🧪 GIẢ THUYẾT A (Quan điểm của Bot & Quản trị Rủi ro):
> *Nhóm Dầu khí (PLX, PVT, BSR) hôm nay tăng chủ yếu do tin tức giá dầu thế giới bất ngờ, trong khi dòng tiền thị trường chung đang rút ra (-14.42 điểm). Ngày mai khi quán tính bán của phiên hôm nay tiếp diễn (hoặc giá dầu thế giới chững lại), nhóm Dầu khí sẽ đối mặt áp lực chốt lời T+0 và bị 'BÁN BÙ', giá sẽ quay đầu giảm hoặc lùi về test lại nền.*

* **Dấu hiệu nhận diện Giả thuyết A đúng:**
  * PLX không giữ được mốc 37.7k, quay đầu điều chỉnh về vùng 36.8k – 37.2k.
  * PVT lùi về vùng 25.0k – 25.2k.
  * BSR quay đầu đỏ hoặc lùi dưới 31.8k.
  * **Kết luận:** Quyết định KHÔNG MUA của Bot hôm nay là **hoàn toàn chính xác**, giúp tài khoản tránh được bẫy mua giá cao nhất ngày (Đu đỉnh ngắn hạn).

---

### 🧪 GIẢ THUYẾT B (Quan điểm Đà tăng Bứt phá - Momentum Continues):
> *Giá dầu thế giới duy trì trên 100 USD tạo ra một siêu sóng ngành độc lập. Nhóm Dầu khí tiếp tục hút tiền bất chấp thị trường chung đỏ lửa, PLX tiếp tục tăng vượt 38.5k, PVT vượt 26.5k.*

* **Dấu hiệu nhận diện Giả thuyết B đúng:**
  * Nhóm Dầu khí mở cửa xanh ngay từ ATO, dòng tiền tiếp tục đổ mạnh vào ăn hàng.
  * **Kết luận:** Bot đã **bỏ lỡ cơ hội thực sự** do bộ lọc Sổ lệnh Level-2 quá khắt khe (Over-Conservative).
  * **Hành động khắc phục ngay lập tức:** Tách riêng ngành Dầu khí trong RiskService và kích hoạt cơ chế *Breakout Stop-Limit Override* để bot dám mua bứt phá khi có sóng ngành mạnh.

---

## 4. MỐC THỜI GIAN & CHỈ SỐ THEO DÕI NGÀY MAI (09/10/2026)

| Mốc thời gian | Sự kiện cần quan sát | Mục tiêu kiểm chứng |
| :--- | :--- | :--- |
| **08:30 Sáng** | Giá dầu thế giới WTI & Brent đêm nay | Còn duy trì > 103 USD hay đã đảo chiều giảm? |
| **09:15 Sáng** | Kết thúc phiên ATO sàn HOSE | PLX, PVT, BSR mở cửa Xanh tiếp hay bị ép Đỏ theo VN-Index? |
| **11:30 Trưa** | Kết thúc phiên sáng | Nhóm Dầu khí giữ được giá đóng cửa hôm nay (37.70) hay đã tụt lùi? |
| **14:00 Chiều** | Áp lực hàng T+2.5 phiên chiều | Nhóm Dầu có bị bán tháo bù theo toàn thị trường hay không? |
| **15:00 Chiều** | Tổng kết phiên đối chiếu | **Ghi nhận phán quyết cuối cùng vào nhật ký!** |

---

## 5. THỰC TRẠNG TÀI SẢN BẢO TOÀN HÔM NAY
* **Tiền mặt hiện tại:** 100,000,000 VNĐ (Nguyên vẹn 100%, 0 đồng thua lỗ).
* **Vị thế:** Hoàn toàn chủ động, sẵn sàng cho mọi kịch bản của phiên Ngày 2.
