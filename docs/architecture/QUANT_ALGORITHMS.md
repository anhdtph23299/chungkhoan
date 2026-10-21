# Chi Tiết Thuật Toán Định Lượng & Mô Hình Xác Suất (Quantitative Algorithms)
> **Dự án:** VNTrade Pro — Phân Tích Định Lượng & Quản Lý Rủi Ro Đẳng Cấp Quỹ

VNTrade Pro tích hợp hệ thống hơn 15 mô hình toán học và thuật toán tài chính định lượng chuyên sâu, được thiết kế riêng nhằm khắc phục những đặc thù phức tạp của thị trường chứng khoán Việt Nam (chu kỳ T+2.5, biên độ trần sàn, thanh khoản không đồng đều, hiện tượng kê lệnh ảo).

---

## 1. Mô Hình Chấm Điểm Định Chế CANSLIM (CANSLIM Rating Engine)
*Service nguồn:* [`CanslimRatingService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/CanslimRatingService.java)

Dựa trên phương pháp kinh điển của William J. O'Neil, hệ thống định lượng hóa 7 tiêu chí theo thang điểm 100:
- **C (Current Earnings)**: Tăng trưởng lợi nhuận sau thuế quý gần nhất (trọng số 20 điểm). Yêu cầu tối thiểu tăng trưởng > 20% so với cùng kỳ.
- **A (Annual Earnings)**: Tăng trưởng EPS 3 năm liên tiếp và ROE > 17% (trọng số 15 điểm).
- **N (New Catalyst/Products/Highs)**: Doanh nghiệp có sản phẩm mới, mở rộng nhà máy hoặc giá tiệm cận đỉnh 52 tuần (trọng số 15 điểm).
- **S (Supply & Demand)**: Tương quan cung cầu, khối lượng bùng nổ vượt 150% so với bình quân 20 phiên (trọng số 15 điểm).
- **L (Leader or Laggard)**: Sức mạnh giá tương đối (RS Score) thuộc top 20% toàn thị trường (trọng số 15 điểm).
- **I (Institutional Sponsorship)**: Dòng vốn tổ chức, khối ngoại hoặc tự doanh gia tăng tỷ trọng (trọng số 10 điểm).
- **M (Market Direction)**: Xu hướng VN-Index đang trong xu hướng tăng (Uptrend) (trọng số 10 điểm).

> **Chuẩn Định Chế (Institutional Grade):** Điểm số tổng hợp $\ge 75$ điểm mới được đưa vào danh sách giải ngân của robot.

---

## 2. Mô Hình Thu Hẹp Biên Độ Biến Động VCP (Volatility Contraction Pattern)
*Service nguồn:* [`VcpPatternDetectorService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/VcpPatternDetectorService.java)

Phát hiện mẫu hình tích lũy kinh điển của phù thủy Mark Minervini:
- Thuật toán nhận diện các đợt thu hẹp liên tiếp (Contractions $T_1 \to T_2 \to T_3 \to T_4$):
  - Đợt co thắt 1 ($T_1$): Biên độ sụt giảm từ 15% - 25%.
  - Đợt co thắt 2 ($T_2$): Biên độ thu hẹp còn 8% - 12%.
  - Đợt co thắt 3 ($T_3$): Biên độ siết chặt còn 3% - 6% kèm **thanh khoản cạn kiệt (Volume Dry-up)**.
- **Điểm Pivot Breakout**: Khi giá vượt qua đỉnh ngắn hạn của $T_3$ với khối lượng lớn hơn ít nhất 1.8 lần MA(20) Volume, thuật toán phát tín hiệu mua gom theo dòng tiền lớn.

---

## 3. Đồ Thị Xoay Tua Tương Đối RRG & Sức Mạnh Mansfield
*Service nguồn:* [`RelativeRotationGraphService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/RelativeRotationGraphService.java)

Áp dụng phương pháp phân tích xoay tua ngành và cổ phiếu của Julius de Kempenaer:
- **Chỉ số Sức mạnh Tương đối Mansfield**:
  $$RS_t = \frac{Price_{Stock, t}}{Price_{VNIndex, t}} \times 100$$
- **RS-Ratio (Trục hoành)**: Đo lường xu hướng sức mạnh dài hạn (chu kỳ 10-12 tuần).
- **RS-Momentum (Trục tung)**: Đo lường xung lực ngắn hạn (đạo hàm bậc nhất của RS-Ratio).
- **4 Góc Phần Tư Luân Chuyển**:
  1. **LEADING (Dẫn dắt)**: $RS\text{-}Ratio > 100$, $RS\text{-}Momentum > 100$ $\to$ Siêu cổ phiếu dẫn dắt thị trường.
  2. **WEAKENING (Suy yếu)**: $RS\text{-}Ratio > 100$, $RS\text{-}Momentum < 100$ $\to$ Giảm tỷ trọng hoặc chốt lời từng phần.
  3. **LAGGING (Tụt hậu)**: $RS\text{-}Ratio < 100$, $RS\text{-}Momentum < 100$ $\to$ Bẫy thanh khoản, bot tự động loại bỏ.
  4. **IMPROVING (Cải thiện)**: $RS\text{-}Ratio < 100$, $RS\text{-}Momentum > 100$ $\to$ Cổ phiếu tạo đáy và chuẩn bị bứt phá.

---

## 4. Vi Cấu Trúc Sổ Lệnh Level-2 (Order Book Imbalance - OBI) & Bẫy Kê Lệnh Ảo
*Service nguồn:* [`OrderBookImbalanceService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/OrderBookImbalanceService.java), [`MicrostructureSpoofingDetectorService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/MicrostructureSpoofingDetectorService.java)

- **Hệ số Mất Cân Bằng Khối Lượng Đặt (OBI Ratio)**:
  $$OBI = \frac{\sum_{i=1}^3 BidVol_i - \sum_{i=1}^3 AskVol_i}{\sum_{i=1}^3 BidVol_i + \sum_{i=1}^3 AskVol_i}$$
  - $OBI > +0.35$: Áp lực gom mua chủ động mạnh mẽ.
  - $OBI < -0.35$: Áp lực bán đè giá lớn, robot hủy lệnh mua.
- **Phát hiện Bức Tường Khối Lượng (Liquidity Wall)**: Nhận diện khi 1 mức giá chiếm trên 45% tổng độ sâu sổ lệnh 3 bước giá (Ask Wall cản trên hoặc Bid Wall đỡ dưới).
- **Giải Thuật Chống Bẫy Kê Lệnh Ảo (Spoofing / Phantom Bid Wall)**: Phân tích tần suất đặt rồi hủy khối lượng lớn ở bên mua trước giờ khớp liên tục để lừa nhà đầu tư cá nhân mua đuổi (Bull Trap). Nếu điểm rủi ro thao túng $> 60/100$, robot kích hoạt khóa bảo vệ.

---

## 5. Bộ Lọc Kalman 2 Chiều Khử Độ Trễ (2-D Kalman Filter Trend & Velocity)
*Service nguồn:* [`KalmanFilterTrendService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/KalmanFilterTrendService.java)

Các đường trung bình động (SMA, EMA) luôn có độ trễ cố hữu. Bộ lọc Kalman ước lượng trạng thái thực sự của chuỗi giá:
- **Vector Trạng Thái**: $x_t = \begin{bmatrix} P_t \\ v_t \end{bmatrix}$ (trong đó $P_t$ là giá thực, $v_t$ là vận tốc xu hướng tính bằng VNĐ/phiên).
- **Mô Hình Động Lực**:
  $$x_{t|t-1} = \begin{bmatrix} 1 & \Delta t \\ 0 & 1 \end{bmatrix} x_{t-1}$$
- **Cập Nhật Đo Lường (Measurement Update)**: Tách nhiễu trắng ngẫu nhiên và tính toán hệ số khuếch đại Kalman Gain $K_t$.
- Nhờ đó, hệ thống xác định chính xác gia tốc giá tức thời mà không bị trễ nến như MACD hay SMA thông thường.

---

## 6. Dự Báo Biến Động GARCH(1,1) & Cắt Lỗ Động Co Giãn T+2.5
*Service nguồn:* [`GarchVolatilityForecastService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/GarchVolatilityForecastService.java)

Mô hình Generalized Autoregressive Conditional Heteroskedasticity:
$$\sigma_t^2 = \omega + \alpha \epsilon_{t-1}^2 + \beta \sigma_{t-1}^2$$
- Ước lượng phương sai có điều kiện trong khoảng thời gian cổ phiếu bị khóa theo luật T+2.5 (3 phiên giao dịch tiếp theo).
- **Ngưỡng Cắt Lỗ Động (Dynamic T+2.5 Stop-Loss)**:
  Thay vì cố định mức cắt lỗ cứng 7%, mô hình tự động điều chỉnh theo độ biến động của từng mã:
  - Cổ phiếu nhóm phòng thủ (VNM, FPT) với biến động thấp $\to$ Đặt SL hẹp 4% - 5.5%.
  - Cổ phiếu nhóm beta cao (Bất động sản, Thép, Chứng khoán) với biến động mạnh $\to$ Đặt SL giãn nở 6.5% - 7.0% kèm giảm khối lượng để tránh bị quét trúng trước khi cổ phiếu về tài khoản.

---

## 7. Định Lượng Quy Mô Vị Thế Thích Ứng (Adaptive Position Sizing & Half-Kelly)
*Service nguồn:* [`AdaptivePositionSizingService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/AdaptivePositionSizingService.java), [`KellyCriterionService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/KellyCriterionService.java)

Quy tắc phân bổ vốn dựa trên 3 tầng bảo vệ:
1. **Rủi Ro Cố Định Theo Độ Biến Động Thực Tế (ATR Sizing)**:
   $$\text{Shares} = \frac{\text{Capital} \times \text{RiskPercent}}{2.0 \times \text{ATR}(14)}$$
2. **Tiêu Chuẩn Half-Kelly (Bảo Thủ)**:
   $$f^* = \frac{1}{2} \left( p - \frac{1 - p}{b} \right)$$
   (với $p$ là tỷ lệ thắng lịch sử, $b$ là tỷ số Lời / Lỗ bình quân). Tránh rủi ro cháy tài khoản do sử dụng công thức Full-Kelly gốc.
3. **Hiệu Chỉnh Theo Trạng Thái Thị Trường (Regime Factor)**:
   - Thị trường Bullish: Phân bổ 100% tỷ trọng tính toán.
   - Thị trường Đi ngang (Sideway): Thu hẹp về 60% tỷ trọng.
   - Thị trường Bearish: Thu hẹp về 0% (Khóa 100% lệnh mua mới).

---

## 8. Mô Hình Trượt Giá Định Luật Căn Bậc Hai & Kiểm Toán Thanh Khoản
*Service nguồn:* [`LiquidityAdjustedReturnService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/LiquidityAdjustedReturnService.java)

Trong thực tế, việc mua bán khối lượng lớn sẽ làm giá bị trượt theo **Square-Root Law**:
$$\text{Slippage} = \gamma \times \sigma \times \sqrt{\frac{Q}{V}}$$
(trong đó $Q$ là khối lượng đặt, $V$ là thanh khoản trung bình 20 ngày ADTV, $\sigma$ là độ biến động ngày).
- Tự động chia các mã vào 3 phân tầng thanh khoản (Mega-Cap, Mid-Cap, Small-Cap).
- Giới hạn khối lượng mua không vượt quá 3% ADTV mỗi phiên để triệt tiêu chi phí tác động giá ngầm (Market Impact Cost).

---

## 9. Kiểm Định Tỷ Số Deflated Sharpe Ratio & Walk-Forward Optimization
*Service nguồn:* [`DeflatedSharpeAuditService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/DeflatedSharpeAuditService.java), [`WalkForwardOptimizationService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/WalkForwardOptimizationService.java)

Áp dụng phương pháp luận của Giáo sư Marcos López de Prado (AQR Capital):
- **Deflated Sharpe Ratio (DSR)**: Khắc phục lỗi "ảo tưởng hiệu năng" do thử nghiệm quá nhiều chiến lược (Multiple Testing Bias / Data Snooping). DSR tính toán xác suất để tỷ số Sharpe của thuật toán không phải do may mắn ngẫu nhiên:
  $$DSR = P\left( SR > 0 \mid N_{\text{trials}}, \text{Skewness}, \text{Kurtosis}, \text{Variance} \right)$$
- **Walk-Forward Efficiency (WFE)**: Chia dữ liệu lịch sử thành 70% In-Sample (huấn luyện) và 30% Out-of-Sample (kiểm tra mù).
  $$WFE = \frac{\text{Return}_{\text{Out-of-Sample}}}{\text{Return}_{\text{In-Sample}}} \times 100\%$$
  - Nếu $WFE \ge 50\%$: Chiến lược có tính bền vững cao trên dữ liệu tương lai.
  - Nếu $WFE < 30\%$: Dấu hiệu quá khớp (Overfitting), hệ thống từ chối giải ngân tiền thật.

---

## 10. Mô Phỏng Kiểm Tra Sức Chịu Đựng Monte Carlo (Monte Carlo Stress Testing)
*Service nguồn:* [`MonteCarloBacktestStressService.java`](file:///c:/Users/Windows/Desktop/chungkhoan/backend/src/main/java/com/vntrade/backend/service/MonteCarloBacktestStressService.java)

- Thực hiện **1,000 lần mô phỏng ngẫu nhiên (Bootstrap Resampling)** thứ tự các giao dịch trong quá khứ.
- Ước lượng mức sụt giảm tài sản xấu nhất ở độ tin cậy 95% (Value at Risk - VaR 95%) và sụt giảm có điều kiện (Conditional VaR / Expected Shortfall 99%).
- Xác định tỷ lệ phá sản lý thuyết (Probability of Ruin) trước khi triển khai hệ thống vào tài khoản thật.
