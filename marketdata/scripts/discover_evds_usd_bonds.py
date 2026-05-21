"""Discover EVDS series that look like USD Hazine instruments (price + ORAN)."""
import requests
from datetime import date, timedelta

KEY = "uktb15QSQw"
BASE = "https://evds3.tcmb.gov.tr/igmevdsms-dis"
end = date.today()
start = end - timedelta(days=14)
fmt = lambda d: d.strftime("%d-%m-%Y")

# Candidates from Ziraat list + common TRT/TRD patterns
candidates = []
for y in ["26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "38", "40", "41", "43", "45", "47"]:
    for m in ["01", "02", "03", "05", "06", "07", "08", "09", "10", "11", "12"]:
        for d in ["01", "05", "08", "14", "15", "17", "20", "24", "26", "30"]:
            candidates.append(f"TRT{m}{d}{y}")
            candidates.append(f"TRD{m}{d}{y}")

seen = set()
for code in candidates:
    if code in seen:
        continue
    seen.add(code)
    sc = f"TP.{code}"
    uri = f"/series={sc}&startDate={fmt(start)}&endDate={fmt(end)}&type=json"
    r = requests.get(BASE + uri, headers={"key": KEY}, timeout=8)
    if r.status_code != 200:
        continue
    n = len(r.json().get("items", []))
    if n > 0:
        items = r.json()["items"]
        val = None
        for k, v in items[-1].items():
            if k not in ("Tarih", "DATE"):
                val = v
                break
        print(code, "price", val)
