import os
import csv
import json
import time
import urllib.request
import sys

sys.stdout.reconfigure(encoding='utf-8')

SYMBOLS = [
    "VNINDEX", "VN30",
    "ACB", "BCM", "BID", "BVH", "CTG", "FPT", "GAS", "GVR",
    "HDB", "HPG", "MBB", "MSN", "MWG", "PLX", "POW", "SAB",
    "SHB", "SSB", "SSI", "STB", "TCB", "TPB", "VCB", "VHM",
    "VIB", "VIC", "VJC", "VNM", "VPB", "VRE"
]

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "historical_data")
os.makedirs(OUT_DIR, exist_ok=True)

now_sec = int(time.time())
from_sec = int(time.mktime(time.strptime("2020-01-01", "%Y-%m-%d")))

print(f"Bắt đầu tải dữ liệu lịch sử nến sàn thật 2020 - 2026 cho {len(SYMBOLS)} mã...")

success_count = 0

for sym in SYMBOLS:
    url = f"https://dchart-api.vndirect.com.vn/dchart/history?resolution=D&symbol={sym}&from={from_sec}&to={now_sec}"
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)",
        "Accept": "*/*"
    })
    try:
        with urllib.request.urlopen(req, timeout=12) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            if data.get("s") == "ok" and len(data.get("t", [])) > 0:
                t_arr = data["t"]
                o_arr = data["o"]
                h_arr = data["h"]
                l_arr = data["l"]
                c_arr = data["c"]
                v_arr = data["v"]

                csv_file = os.path.join(OUT_DIR, f"{sym}.csv")
                with open(csv_file, "w", newline="", encoding="utf-8") as f:
                    writer = csv.writer(f)
                    writer.writerow(["date", "open", "high", "low", "close", "volume"])
                    for i in range(len(t_arr)):
                        date_str = time.strftime("%Y-%m-%d", time.localtime(t_arr[i]))
                        # Điều chỉnh đơn vị: Nếu là mã cổ phiếu và giá < 1000 thì quy về VNĐ
                        scale = 1000.0 if sym not in ["VNINDEX", "VN30"] and c_arr[i] < 1000.0 else 1.0
                        op = round(o_arr[i] * scale, 2)
                        hi = round(h_arr[i] * scale, 2)
                        lo = round(l_arr[i] * scale, 2)
                        cl = round(c_arr[i] * scale, 2)
                        vol = int(v_arr[i])
                        writer.writerow([date_str, op, hi, lo, cl, vol])
                
                first_date = time.strftime("%Y-%m-%d", time.localtime(t_arr[0]))
                last_date = time.strftime("%Y-%m-%d", time.localtime(t_arr[-1]))
                print(f"✅ {sym:7s}: {len(t_arr):4d} nến ({first_date} -> {last_date}) -> {csv_file}")
                success_count += 1
            else:
                print(f"⚠️ {sym:7s}: Không có dữ liệu (status={data.get('s')})")
    except Exception as e:
        print(f"❌ {sym:7s}: Lỗi tải dữ liệu: {e}")
    time.sleep(0.3)

print(f"\nHoàn tất! Đã lưu thành công {success_count}/{len(SYMBOLS)} mã vào thư mục {OUT_DIR}.")
