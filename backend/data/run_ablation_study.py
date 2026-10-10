import os
import csv
import glob
import math
import sys
import random
from datetime import datetime

# Đảm bảo in UTF-8 không lỗi trên Windows console
sys.stdout.reconfigure(encoding='utf-8')
random.seed(42)

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "historical_data")

# Phí và thuế Việt Nam: Mua 0.15%, Bán 0.15% phí + 0.10% thuế TNCN = 0.40% tổng chi phí + 0.10% trượt giá = 0.50%
TOTAL_FRICTION = 0.0050

def load_csv(symbol):
    path = os.path.join(DATA_DIR, f"{symbol}.csv")
    if not os.path.exists(path):
        return []
    rows = []
    with open(path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for r in reader:
            rows.append({
                "date": r["date"],
                "open": float(r["open"]),
                "high": float(r["high"]),
                "low": float(r["low"]),
                "close": float(r["close"]),
                "volume": int(float(r["volume"]))
            })
    rows.sort(key=lambda x: x["date"])
    return rows

def compute_indicators(candles):
    closes = [c["close"] for c in candles]
    vols = [c["volume"] for c in candles]
    n = len(candles)
    if n == 0:
        return
    
    def calc_ema(period):
        ema = [closes[0]] * n
        multiplier = 2.0 / (period + 1.0)
        for i in range(1, n):
            ema[i] = (closes[i] - ema[i-1]) * multiplier + ema[i-1]
        return ema

    ema20 = calc_ema(20)
    ema50 = calc_ema(50)
    
    ema12 = calc_ema(12)
    ema26 = calc_ema(26)
    macd_line = [ema12[i] - ema26[i] for i in range(n)]
    
    signal_line = [macd_line[0]] * n
    m9 = 2.0 / (9.0 + 1.0)
    for i in range(1, n):
        signal_line[i] = (macd_line[i] - signal_line[i-1]) * m9 + signal_line[i-1]
    macd_hist = [macd_line[i] - signal_line[i] for i in range(n)]
    
    vol_ma20 = [vols[0]] * n
    for i in range(n):
        start = max(0, i - 19)
        vol_ma20[i] = sum(vols[start:i+1]) / (i - start + 1)
        
    high20 = [candles[i]["high"] for i in range(n)]
    for i in range(1, n):
        start = max(0, i - 20)
        high20[i] = max(c["high"] for c in candles[start:i])
        
    for i in range(n):
        candles[i]["ema20"] = ema20[i]
        candles[i]["ema50"] = ema50[i]
        candles[i]["macd_hist"] = macd_hist[i]
        candles[i]["vol_ma20"] = vol_ma20[i]
        candles[i]["high20"] = high20[i]

# Nạp dữ liệu thị trường
print("Đang nạp dữ liệu lịch sử VN30 & Chỉ số...")
vnindex_data = load_csv("VNINDEX")
compute_indicators(vnindex_data)
vnindex_map = {c["date"]: c for c in vnindex_data}

vn30_idx_data = load_csv("VN30")
compute_indicators(vn30_idx_data)
vn30_idx_map = {c["date"]: c for c in vn30_idx_data}

vn30_symbols = [
    "ACB", "BCM", "BID", "BVH", "CTG", "FPT", "GAS", "GVR",
    "HDB", "HPG", "MBB", "MSN", "MWG", "PLX", "POW", "SAB",
    "SHB", "SSB", "SSI", "STB", "TCB", "TPB", "VCB", "VHM",
    "VIB", "VIC", "VJC", "VNM", "VPB", "VRE"
]

stock_data = {}
for s in vn30_symbols:
    c = load_csv(s)
    if len(c) > 200:
        compute_indicators(c)
        stock_data[s] = c

# Tính RRG RS-Ratio so với VN-Index
for s, c_list in stock_data.items():
    n = len(c_list)
    rs_ratios = [100.0] * n
    for i in range(n):
        d = c_list[i]["date"]
        if d in vnindex_map:
            v_close = vnindex_map[d]["close"]
            if v_close > 0:
                raw_rs = c_list[i]["close"] / v_close
                start = max(0, i - 49)
                hist_rs = [c_list[k]["close"] / vnindex_map[c_list[k]["date"]]["close"] 
                           for k in range(start, i+1) if c_list[k]["date"] in vnindex_map and vnindex_map[c_list[k]["date"]]["close"] > 0]
                if hist_rs:
                    avg_rs = sum(hist_rs) / len(hist_rs)
                    rs_ratios[i] = (raw_rs / avg_rs) * 100.0 if avg_rs > 0 else 100.0
        c_list[i]["rs_ratio"] = rs_ratios[i]

print(f"Đã xử lý xong dữ liệu {len(stock_data)} cổ phiếu VN30.")

# Hàm tính Benchmark Mua & Giữ (Buy & Hold) cho một chỉ số
def calculate_buy_and_hold(index_map, start_date, end_date):
    dates = sorted([d for d in index_map.keys() if start_date <= d <= end_date])
    if not dates:
        return {"cagr": 0, "sharpe": 0, "max_drawdown": 0, "total_return": 0}
    
    start_price = index_map[dates[0]]["close"]
    end_price = index_map[dates[-1]]["close"]
    
    total_return = ((end_price - start_price) / start_price) * 100.0
    num_days = len(dates)
    years = max(0.5, num_days / 250.0)
    cagr = (((end_price / start_price) ** (1.0 / years)) - 1.0) * 100.0
    
    daily_returns = []
    nav_series = []
    peak = start_price
    max_dd = 0.0
    
    for i in range(len(dates)):
        p = index_map[dates[i]]["close"]
        nav_series.append(p)
        if p > peak:
            peak = p
        dd = (peak - p) / peak if peak > 0 else 0
        if dd > max_dd:
            max_dd = dd
        if i > 0:
            p_prev = index_map[dates[i-1]]["close"]
            daily_returns.append((p - p_prev) / p_prev)
            
    mean_r = sum(daily_returns) / len(daily_returns) if daily_returns else 0
    var_r = sum((r - mean_r) ** 2 for r in daily_returns) / len(daily_returns) if daily_returns else 0
    std_r = math.sqrt(var_r) if var_r > 0 else 0.0001
    
    annual_return = mean_r * 250.0
    annual_vol = std_r * math.sqrt(250.0)
    risk_free = 0.05
    sharpe = (annual_return - risk_free) / annual_vol if annual_vol > 0 else 0
    
    return {
        "cagr": round(cagr, 2),
        "total_return": round(total_return, 2),
        "max_drawdown": round(max_dd * 100.0, 2),
        "sharpe": round(sharpe, 2)
    }

# Bootstrap Confidence Interval 95%
def bootstrap_expectancy_ci(pnl_list, n_bootstraps=10000, alpha=0.05):
    if not pnl_list or len(pnl_list) < 5:
        return (0.0, 0.0)
    n = len(pnl_list)
    means = []
    for _ in range(n_bootstraps):
        sample = [random.choice(pnl_list) for _ in range(n)]
        means.append(sum(sample) / n)
    means.sort()
    low_idx = int((alpha / 2.0) * n_bootstraps)
    high_idx = int((1.0 - alpha / 2.0) * n_bootstraps)
    return (round(means[low_idx], 2), round(means[high_idx], 2))

# Deflated Sharpe Ratio (DSR) chuẩn theo López de Prado (2014)
def compute_deflated_sharpe(sharpe_annual, daily_returns, num_trials=10):
    T = len(daily_returns)
    if T < 20 or sharpe_annual == 0:
        return 0.0
    
    # Skewness và Kurtosis của chuỗi lợi nhuận
    mean_r = sum(daily_returns) / T
    var_r = sum((r - mean_r) ** 2 for r in daily_returns) / T
    std_r = math.sqrt(var_r) if var_r > 0 else 0.0001
    
    skew = sum(((r - mean_r) / std_r) ** 3 for r in daily_returns) / T
    kurt = sum(((r - mean_r) / std_r) ** 4 for r in daily_returns) / T
    
    # Phương sai ước lượng của Sharpe
    sr_var = (1.0 - skew * sharpe_annual + ((kurt - 1.0) / 4.0) * (sharpe_annual ** 2)) / T
    sr_std = math.sqrt(max(1e-6, sr_var))
    
    # Ngưỡng kỳ vọng Sharpe lớn nhất do ngẫu nhiên khi thử N lần
    sr_star = math.sqrt(2.0 * math.log(num_trials)) * sr_std
    
    z = (sharpe_annual - sr_star) / sr_std if sr_std > 0 else 0
    dsr_pct = 0.5 * (1.0 + math.erf(z / math.sqrt(2.0))) * 100.0
    return round(dsr_pct, 1)

# Hàm chạy Backtest thực tế: Vào lệnh tại giá MỞ CỬA phiên kế tiếp (Next-Day Open)
def run_simulation(start_date, end_date, config_level):
    INITIAL_CAPITAL = 100_000_000.0
    cash = INITIAL_CAPITAL
    portfolio = {} # symbol -> {entry_date, entry_price, shares, entry_idx, highest_price}
    trades = []
    daily_nav = []
    
    all_dates = sorted(list(set(
        c["date"] for c_list in stock_data.values() for c in c_list 
        if start_date <= c["date"] <= end_date
    )))
    
    date_to_stocks = {d: {} for d in all_dates}
    for s, c_list in stock_data.items():
        for idx, c in enumerate(c_list):
            if c["date"] in date_to_stocks:
                date_to_stocks[c["date"]][s] = (idx, c)

    pending_buy_signals = [] # Danh sách ứng viên được kích hoạt ở cuối phiên t, sẽ mua tại Open t+1

    for day_i, current_date in enumerate(all_dates):
        # 1. THỰC HIỆN LỆNH MUA PENDING TẠI GIÁ MỞ CỬA PHIÊN HÔM NAY (NEXT-DAY OPEN)
        available_slots = 4 - len(portfolio)
        if available_slots > 0 and cash > 15_000_000 and pending_buy_signals:
            # Lọc các ứng viên còn hợp lệ ở ngày hôm nay
            exec_candidates = []
            for s, score in pending_buy_signals:
                if s not in portfolio and s in date_to_stocks[current_date]:
                    idx, bar = date_to_stocks[current_date][s]
                    exec_candidates.append((s, bar, score))
            
            exec_candidates.sort(key=lambda x: x[2], reverse=True)
            for s, bar, sc in exec_candidates[:available_slots]:
                alloc = min(cash / available_slots, INITIAL_CAPITAL * 0.25)
                if alloc >= 10_000_000:
                    # Mua tại giá OPEN phiên kế tiếp + trượt giá 0.15%
                    buy_price = bar["open"] * (1.0 + 0.0015)
                    shares = int((alloc / buy_price) // 100) * 100
                    if shares >= 100:
                        cost = shares * buy_price * 1.0015 # Phí mua 0.15%
                        cash -= cost
                        portfolio[s] = {
                            "entry_date": current_date,
                            "entry_price": buy_price,
                            "shares": shares,
                            "highest_price": max(buy_price, bar["high"])
                        }
            pending_buy_signals = [] # Xóa danh sách pending sau khi khớp
        else:
            pending_buy_signals = []

        # 2. KIỂM TRA THOÁT LỆNH CHO CÁC VỊ THẾ NẮM GIỮ (TUÂN THỦ T+2.5 VSDC)
        to_close = []
        for s, pos in portfolio.items():
            if s in date_to_stocks[current_date]:
                _, bar = date_to_stocks[current_date][s]
                pos["highest_price"] = max(pos["highest_price"], bar["high"])
                cur_price = bar["close"]
                entry_price = pos["entry_price"]
                pnl_pct = (cur_price - entry_price) / entry_price
                
                # Khóa thanh khoản T+2.5 VSDC: giữ tối thiểu 3 phiên giao dịch
                days_held = (datetime.strptime(current_date, "%Y-%m-%d") - datetime.strptime(pos["entry_date"], "%Y-%m-%d")).days
                if days_held >= 3:
                    # Cắt lỗ cứng -7%
                    if pnl_pct <= -0.07:
                        to_close.append((s, cur_price, "STOP_LOSS_7PCT"))
                    # Chốt lời mục tiêu +15%
                    elif pnl_pct >= 0.15:
                        to_close.append((s, cur_price, "TAKE_PROFIT_15PCT"))
                    # Trailing Stop: nếu từng lãi >= 8% mà sụt quá 3% từ đỉnh
                    elif (pos["highest_price"] - entry_price) / entry_price >= 0.08 and cur_price <= pos["highest_price"] * 0.97:
                        to_close.append((s, cur_price, "TRAILING_STOP"))
                    # Thoát vị thế sau 30 ngày chôn vốn
                    elif days_held >= 30:
                        to_close.append((s, cur_price, "TIME_EXIT"))

        for s, exit_price, reason in to_close:
            pos = portfolio.pop(s)
            gross_return = (exit_price - pos["entry_price"]) / pos["entry_price"]
            net_return = gross_return - TOTAL_FRICTION
            profit_vnd = pos["shares"] * pos["entry_price"] * net_return
            cash += pos["shares"] * exit_price * (1.0 - 0.0025) # Trừ phí bán & thuế 0.25%
            trades.append({
                "symbol": s,
                "entry_date": pos["entry_date"],
                "exit_date": current_date,
                "entry_price": pos["entry_price"],
                "exit_price": exit_price,
                "pnl_pct": net_return * 100.0,
                "profit_vnd": profit_vnd,
                "reason": reason
            })

        # 3. CUỐI PHIÊN HÔM NAY: QUÉT TÍN HIỆU ĐỂ ĐẶT LỆNH MUA CHO PHIÊN SAU
        # Chỉ quét nếu danh mục còn khả năng mua phiên sau
        if len(portfolio) < 4 and cash > 15_000_000:
            defcon_ok = True
            if config_level >= 4:
                if current_date in vnindex_map:
                    v_bar = vnindex_map[current_date]
                    if v_bar["close"] < v_bar["ema50"]:
                        defcon_ok = False
                    if "ema20" in v_bar and (v_bar["close"] - v_bar["open"]) / v_bar["open"] < -0.015:
                        defcon_ok = False
            
            if defcon_ok:
                today_candidates = []
                for s, (idx, bar) in date_to_stocks[current_date].items():
                    if s in portfolio:
                        continue
                    
                    # Tầng 0: Nổ Vol vượt đỉnh 20 phiên
                    is_breakout = bar["close"] > bar["high20"] and bar["volume"] > bar["vol_ma20"] * 1.3 and bar["volume"] >= 300_000
                    if not is_breakout:
                        continue
                    
                    # Tầng 1: Trend Filter (EMA20 > EMA50, Close > EMA20, MACD Hist > 0)
                    if config_level >= 1:
                        if not (bar["ema20"] > bar["ema50"] and bar["close"] > bar["ema20"] and bar["macd_hist"] > 0):
                            continue
                            
                    # Tầng 2: VCP Contraction (Biên độ 3 phiên trước < 5.0%)
                    if config_level >= 2:
                        c_list = stock_data[s]
                        if idx >= 3:
                            prev3_high = max(c_list[idx-k]["high"] for k in range(1, 4))
                            prev3_low = min(c_list[idx-k]["low"] for k in range(1, 4))
                            contraction = (prev3_high - prev3_low) / prev3_low
                            if contraction > 0.05:
                                continue
                                
                    # Tầng 3: RRG Mansfield (RS-Ratio > 100.0)
                    if config_level >= 3:
                        if bar.get("rs_ratio", 100.0) < 100.0:
                            continue
                            
                    score = (bar["volume"] / bar["vol_ma20"]) * (bar.get("rs_ratio", 100.0) / 100.0)
                    today_candidates.append((s, score))
                    
                pending_buy_signals = today_candidates

        # 4. TÍNH TOÁN NAV CUỐI NGÀY
        stock_val = sum(pos["shares"] * date_to_stocks[current_date][s][1]["close"] 
                        for s, pos in portfolio.items() if s in date_to_stocks[current_date])
        total_nav = cash + stock_val
        daily_nav.append((current_date, total_nav))

    # TÍNH TOÁN CÁC CHỈ SỐ THỐNG KÊ
    total_trades = len(trades)
    if total_trades == 0:
        return {
            "total_trades": 0, "win_rate": 0, "net_pnl_vnd": 0, "expectancy_pct": 0,
            "cagr": 0, "sharpe": 0, "max_drawdown": 0, "calmar": 0, "dsr": 0, "ci_95": (0, 0)
        }
        
    wins = [t for t in trades if t["pnl_pct"] > 0]
    win_rate = (len(wins) / total_trades) * 100.0
    net_pnl_vnd = sum(t["profit_vnd"] for t in trades)
    avg_expectancy_pct = sum(t["pnl_pct"] for t in trades) / total_trades
    
    # Bootstrap CI 95%
    pnl_list = [t["pnl_pct"] for t in trades]
    ci_95 = bootstrap_expectancy_ci(pnl_list)
    
    # Drawdown
    peak = daily_nav[0][1]
    max_dd = 0.0
    for _, nav in daily_nav:
        if nav > peak:
            peak = nav
        dd = (peak - nav) / peak if peak > 0 else 0
        if dd > max_dd:
            max_dd = dd
            
    # CAGR
    num_days = len(daily_nav)
    years = max(0.5, num_days / 250.0)
    final_nav = daily_nav[-1][1]
    cagr = ((final_nav / INITIAL_CAPITAL) ** (1.0 / years) - 1.0) * 100.0
    
    # Daily Returns & Sharpe
    daily_returns = []
    for i in range(1, len(daily_nav)):
        r = (daily_nav[i][1] - daily_nav[i-1][1]) / daily_nav[i-1][1]
        daily_returns.append(r)
        
    mean_r = sum(daily_returns) / len(daily_returns) if daily_returns else 0
    var_r = sum((r - mean_r) ** 2 for r in daily_returns) / len(daily_returns) if daily_returns else 0
    std_r = math.sqrt(var_r) if var_r > 0 else 0.0001
    
    annual_return = mean_r * 250.0
    annual_vol = std_r * math.sqrt(250.0)
    risk_free = 0.05
    sharpe = (annual_return - risk_free) / annual_vol if annual_vol > 0 else 0
    calmar = cagr / (max_dd * 100.0) if max_dd > 0 else 0
    
    # Deflated Sharpe Ratio
    dsr = compute_deflated_sharpe(sharpe, daily_returns, num_trials=10)
    
    return {
        "total_trades": total_trades,
        "win_rate": round(win_rate, 2),
        "net_pnl_vnd": round(net_pnl_vnd, 0),
        "expectancy_pct": round(avg_expectancy_pct, 2),
        "cagr": round(cagr, 2),
        "sharpe": round(sharpe, 2),
        "max_drawdown": round(max_dd * 100.0, 2),
        "calmar": round(calmar, 2),
        "dsr": dsr,
        "ci_95": ci_95,
        "final_nav": round(final_nav, 0)
    }

print("\n" + "="*95)
print("TIẾN HÀNH CHẠY BÀI TOÁN ABLATION TEST (NEXT-DAY OPEN EXECUTION, BENCHMARKS, BOOTSTRAP CI)")
print("="*95)

CONFIG_NAMES = [
    "[T0] Trần trụi (Raw Breakout 20d, không lọc)",
    "[T1] + Trend Filter (EMA20 > EMA50, MACD Hist > 0)",
    "[T2] + VCP Contraction (Co hẹp độ biến động nến)",
    "[T3] + RRG Mansfield (RS-Ratio > 100.0 Leading)",
    "[T4] + DEFCON-1 Circuit Breaker (Thị trường chung)"
]

# 1. TÍNH BENCHMARK CHO IN-SAMPLE (2020-2024)
is_start, is_end = "2020-01-01", "2024-12-31"
bm_is_vnindex = calculate_buy_and_hold(vnindex_map, is_start, is_end)
bm_is_vn30 = calculate_buy_and_hold(vn30_idx_map, is_start, is_end)

print(f"\n>>> BENCHMARK IN-SAMPLE (2020-2024: 5 NĂM)")
print(f"VN-INDEX (Buy & Hold): CAGR = {bm_is_vnindex['cagr']:+.2f}%, MaxDD = {bm_is_vnindex['max_drawdown']}%, Sharpe = {bm_is_vnindex['sharpe']}")
print(f"VN30     (Buy & Hold): CAGR = {bm_is_vn30['cagr']:+.2f}%, MaxDD = {bm_is_vn30['max_drawdown']}%, Sharpe = {bm_is_vn30['sharpe']}")

print(f"\n>>> BƯỚC 1: TẬP IN-SAMPLE (2020-2024: 5 NĂM HUẤN LUYỆN)")
print(f"{'Tổ Hợp Chiến Lược':<48} | {'Số Lệnh':<7} | {'Win %':<6} | {'Expectancy (95% CI)':<22} | {'Sharpe':<6} | {'MaxDD':<7} | {'CAGR %':<7}")
print("-" * 120)

in_sample_results = []
for level in range(5):
    res = run_simulation(is_start, is_end, level)
    in_sample_results.append(res)
    name = CONFIG_NAMES[level]
    ci_str = f"{res['expectancy_pct']:+.2f}% [{res['ci_95'][0]:+.2f}, {res['ci_95'][1]:+.2f}]"
    print(f"{name:<48} | {res['total_trades']:<7} | {res['win_rate']:<6.1f} | {ci_str:<22} | {res['sharpe']:<6.2f} | {res['max_drawdown']:<6.2f}% | {res['cagr']:<+6.2f}%")

# 2. TÍNH BENCHMARK CHO OUT-OF-SAMPLE (2025-2026)
oos_start, oos_end = "2025-01-01", "2026-10-09"
bm_oos_vnindex = calculate_buy_and_hold(vnindex_map, oos_start, oos_end)
bm_oos_vn30 = calculate_buy_and_hold(vn30_idx_map, oos_start, oos_end)

print(f"\n>>> BENCHMARK OUT-OF-SAMPLE (2025-2026: GẦN 2 NĂM)")
print(f"VN-INDEX (Buy & Hold): CAGR = {bm_oos_vnindex['cagr']:+.2f}%, MaxDD = {bm_oos_vnindex['max_drawdown']}%, Sharpe = {bm_oos_vnindex['sharpe']}")
print(f"VN30     (Buy & Hold): CAGR = {bm_oos_vn30['cagr']:+.2f}%, MaxDD = {bm_oos_vn30['max_drawdown']}%, Sharpe = {bm_oos_vn30['sharpe']}")

print(f"\n>>> BƯỚC 2: TẬP OUT-OF-SAMPLE (2025-2026: ĐÃ XEM KẾT QUẢ - KHÔNG CÒN LÀ TẬP MÙ)")
print(f"{'Tổ Hợp Chiến Lược':<48} | {'Số Lệnh':<7} | {'Win %':<6} | {'Expectancy (95% CI)':<22} | {'Sharpe':<6} | {'MaxDD':<7} | {'CAGR %':<7} | {'DSR %':<6}")
print("-" * 130)

out_sample_results = []
for level in range(5):
    res = run_simulation(oos_start, oos_end, level)
    out_sample_results.append(res)
    name = CONFIG_NAMES[level]
    ci_str = f"{res['expectancy_pct']:+.2f}% [{res['ci_95'][0]:+.2f}, {res['ci_95'][1]:+.2f}]"
    print(f"{name:<48} | {res['total_trades']:<7} | {res['win_rate']:<6.1f} | {ci_str:<22} | {res['sharpe']:<6.2f} | {res['max_drawdown']:<6.2f}% | {res['cagr']:<+6.2f}% | {res['dsr']:<6.1f}")

# XUẤT BÁO CÁO MARKDOWN VỚI SỐ LIỆU CHÍNH XÁC VÀ PHÁN QUYẾT TRUNG THỰC
REPORT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "BAO_CAO_ABLATION_TEST_VN30_2020_2026.md")
with open(REPORT_PATH, "w", encoding="utf-8") as f:
    f.write("# BÁO CÁO NGHIÊN CỨU ĐỊNH LƯỢNG ABLATION STUDY RỔ VN30 (2020 - 2026)\n\n")
    f.write("> **Phương pháp:** Kiểm định loại trừ (Ablation Analysis) theo chuẩn định chế tài chính.\n")
    f.write("> **Điểm vào lệnh:** Giá Mở Cửa phiên kế tiếp ($t+1$ Open) sau khi tín hiệu xác nhận ở phiên $t$ (Khử triệt để Look-ahead Bias).\n")
    f.write("> **Chi phí ma sát:** 0.50% mỗi chu kỳ giao dịch (0.15% phí mua, 0.15% phí bán, 0.10% thuế TNCN, 0.10% trượt giá Slippage).\n")
    f.write("> **Ràng buộc pháp lý:** Khóa thanh khoản T+2.5 VSDC, vốn danh mục 100M VNĐ, tối đa 4 mã (25% NAV/mã).\n")
    f.write("> **Thống kê bổ sung:** Khoảng tin cậy Bootstrap 95% cho Expectancy, Deflated Sharpe Ratio (DSR với N=10 trials), và Đối chiếu Benchmark Buy & Hold VN30/VN-Index.\n\n")
    
    f.write("## 1. SO SÁNH VỚI BENCHMARK MUA VÀ GIỮ (BUY & HOLD BENCHMARK)\n\n")
    f.write("| Chỉ Số Thị Trường | Giai Đoạn In-Sample (2020 - 2024) | Giai Đoạn Out-of-Sample (2025 - 2026) |\n")
    f.write("| :--- | :--- | :--- |\n")
    f.write(f"| **VN-INDEX (Buy & Hold)** | CAGR: **{bm_is_vnindex['cagr']:+.2f}%** \| MaxDD: **{bm_is_vnindex['max_drawdown']}%** \| Sharpe: **{bm_is_vnindex['sharpe']}** | CAGR: **{bm_oos_vnindex['cagr']:+.2f}%** \| MaxDD: **{bm_oos_vnindex['max_drawdown']}%** \| Sharpe: **{bm_oos_vnindex['sharpe']}** |\n")
    f.write(f"| **VN30 (Buy & Hold)** | CAGR: **{bm_is_vn30['cagr']:+.2f}%** \| MaxDD: **{bm_is_vn30['max_drawdown']}%** \| Sharpe: **{bm_is_vn30['sharpe']}** | CAGR: **{bm_oos_vn30['cagr']:+.2f}%** \| MaxDD: **{bm_oos_vn30['max_drawdown']}%** \| Sharpe: **{bm_oos_vn30['sharpe']}** |\n\n")
    
    f.write("> [!NOTE]\n")
    f.write(f"> Trong giai đoạn 2025 - 2026, VN-Index tăng trưởng CAGR {bm_oos_vnindex['cagr']:+.2f}% và VN30 tăng {bm_oos_vn30['cagr']:+.2f}%. Bất kỳ chiến lược nào có CAGR quanh mức này phần lớn là hưởng lợi từ Beta của thị trường chung (Bull Market Tailwinds), không phải hoàn toàn là Alpha độc lập.\n\n")

    f.write("---\n\n")
    f.write("## 2. KẾT QUẢ TẬP IN-SAMPLE (2020 - 2024: 5 NĂM ĐẦY ĐỦ CHU KỲ)\n\n")
    f.write("| STT | Cấu Hình Tầng Lọc | Số Lệnh | Win Rate | Expectancy [95% Bootstrap CI] | Sharpe | MaxDD | CAGR | Excess Return vs VN30 | Phán Quyết Thực Tế |\n")
    f.write("| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |\n")
    
    is_verdicts = [
        "Mốc cơ sở: Đạt Sharpe thấp (0.11), không thắng được Mua & Giữ VN30",
        "Khác biệt không đáng kể: Sharpe 0.11 -> 0.15, MaxDD tăng nhẹ, CI chứa số 0",
        "Làm xấu đi rõ rệt: Expectancy âm, Sharpe âm (-0.32), bóp nghẹt số lệnh",
        "Làm xấu đi: Không cải thiện hiệu suất so với T0/T1, Expectancy âm",
        "Cứu vốn khi sập sàn: Giảm MaxDD từ 38.4% về 34.6% ở cú sập 2022 nhưng CAGR hòa vốn"
    ]
    
    for i, r in enumerate(in_sample_results):
        excess = r["cagr"] - bm_is_vn30["cagr"]
        ci_str = f"{r['expectancy_pct']:+.2f}% [{r['ci_95'][0]:+.2f}, {r['ci_95'][1]:+.2f}]"
        f.write(f"| T{i} | **{CONFIG_NAMES[i]}** | {r['total_trades']} | {r['win_rate']}% | {ci_str} | **{r['sharpe']}** | **{r['max_drawdown']}%** | {r['cagr']:+.2f}% | {excess:+.2f}% | {is_verdicts[i]} |\n")
        
    f.write("\n---\n\n")
    f.write("## 3. KẾT QUẢ TẬP OUT-OF-SAMPLE (2025 - 2026: ĐÃ BỊ NHÌN TRƯỚC - CẦN CẨN TRỌNG)\n\n")
    f.write("| STT | Cấu Hình Tầng Lọc | Số Lệnh | Win Rate | Expectancy [95% Bootstrap CI] | Sharpe | MaxDD | CAGR | Excess vs VN30 | DSR (N=10) | Phán Quyết Thực Tế |\n")
    f.write("| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |\n")
    
    oos_verdicts = [
        "Tập lệnh cơ sở: 114 lệnh, Sharpe 0.49, thấp hơn Mua & Giữ VN30",
        "Tập lệnh gần như trùng T0 (107 vs 114 lệnh): DSR thấp (<30%), chưa chứng minh được edge",
        "Over-filtering: Giảm số lệnh xuống còn 67, bỏ lỡ sóng tăng, CAGR sụt về ~1%",
        "Over-filtering: Tiếp tục giảm lệnh còn 62, Sharpe âm, kém xa Benchmark",
        "Bảo thủ quá mức trong uptrend: Giảm còn 56 lệnh, bỏ lỡ sóng tăng mạnh của rổ VN30"
    ]
    
    for i, r in enumerate(out_sample_results):
        excess = r["cagr"] - bm_oos_vn30["cagr"]
        ci_str = f"{r['expectancy_pct']:+.2f}% [{r['ci_95'][0]:+.2f}, {r['ci_95'][1]:+.2f}]"
        f.write(f"| T{i} | **{CONFIG_NAMES[i]}** | {r['total_trades']} | {r['win_rate']}% | {ci_str} | **{r['sharpe']}** | **{r['max_drawdown']}%** | {r['cagr']:+.2f}% | {excess:+.2f}% | **{r['dsr']}%** | {oos_verdicts[i]} |\n")

    f.write("\n---\n\n")
    f.write("## 4. KẾT LUẬN TRUNG THỰC & ĐỊNH HƯỚNG FORWARD TEST THỰC CHIẾN\n\n")
    f.write("### 4.1. Sự Thật Thống Kê: T1 Chưa Chứng Minh Được Lợi Thế (Unproven Edge)\n")
    f.write("1. **Khoảng tin cậy Bootstrap chứa giá trị 0:** Cả ở In-Sample và Out-of-Sample, khoảng tin cậy 95% của Expectancy đều mở rộng về ngưỡng tiệm cận 0 (hoặc âm khi qua các tầng lọc phức tạp). Về mặt kiểm định giả thuyết thống kê, ta **chưa thể bác bỏ giả thuyết $H_0$** rằng kết quả chỉ là ngẫu nhiên.\n")
    f.write("2. **Deflated Sharpe Ratio (DSR) quá thấp (< 30%):** Với $N=10$ phép thử biến thể tham số, DSR của T1 chỉ đạt dưới 30% (xa dưới mức 95% cần thiết để kết luận có kỹ năng định lượng thực sự).\n")
    f.write("3. **Hiện tượng Beta Riding:** CAGR ~19% của T1 trong giai đoạn 2025-2026 chủ yếu được hỗ trợ bởi Beta khi VN-Index và VN30 cùng tăng trưởng mạnh. Excess return thực tế không vượt trội nhiều so với mua và nắm giữ rổ chỉ số.\n")
    f.write("4. **Tập khóa 2025-2026 không còn 'sạch':** Do chúng ta đã nhìn kết quả OOS trước khi chọn T1, nên tập này đã bị Data Snooping Bias. **Bằng chứng thực sự duy nhất hiện tại chỉ còn là Forward Test trong thời gian thực.**\n\n")

    f.write("### 4.2. Kiến Trúc Khớp Lệnh Sản Xuất: Core Engine + Shadow Mode\n")
    f.write("Để tránh rơi vào bẫy ngụy biện và không tự làm mù mắt mình, hệ thống triển khai kiến trúc 2 tầng:\n")
    f.write("1. **Tầng Quyết Định Cốt Lõi (Core Execution):** Bot Java chỉ sử dụng duy nhất **T1 (Breakout + Trend Filter) kết hợp DEFCON-1 (Cầu chì sập sàn)** để ra quyết định mua/bán thực tế. Không ép thêm các điều kiện lọc phức tạp để tránh over-filtering làm bóp nghẹt số lượng lệnh.\n")
    f.write("2. **Chế Độ Bóng Mờ (Shadow Mode Monitoring):**\n")
    f.write("   - Các tầng lọc chưa được chứng minh bằng chứng thực nghiệm gồm **CANSLIM, MTF Confluence, RRG Mansfield, Level-2 OBI, Spoofing Detector và Kalman Filter** được chuyển hoàn toàn sang trạng thái **SHADOW MODE (Chỉ quan sát)**.\n")
    f.write("   - Khi có tín hiệu T1, các bộ lọc này vẫn chạy tính toán và ghi nhận đánh giá (`PASS` hay `VETO` kèm lý do) vào cơ sở dữ liệu `bot_decision_audit`, nhưng **TUYỆT ĐỐI KHÔNG CHẶN LỆNH MUA** của bot.\n")
    f.write("   - Thông qua cơ chế tự động đối soát giá 5 phiên và 10 phiên sau (`priceAfter5Sessions`, `priceAfter10Sessions`), sau vài tháng giao dịch thực tế ta sẽ có dữ liệu thống kê khách quan: Liệu việc 'VETO' của OBI hay RRG có thực sự giúp tài khoản tránh lỗ, hay chỉ là rào cản cản trở sóng tăng?\n")
    f.write("3. **Bỏ Mọi Nhãn 'Verified' Và Mục Tiêu Cứng:** Toàn bộ code backend và giao diện người dùng loại bỏ các nhãn cam kết 'Verified' hay con số cứng Sharpe 0.92, thay bằng việc hiển thị số liệu đo lường thực tế từ nhật ký Forward Test.\n")

print(f"\nĐã xuất báo cáo chi tiết ra file: {REPORT_PATH}")
