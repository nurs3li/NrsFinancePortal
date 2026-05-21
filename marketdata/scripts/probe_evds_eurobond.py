import requests
from datetime import date, timedelta

KEY = "uktb15QSQw"
BASE = "https://evds3.tcmb.gov.tr/igmevdsms-dis"
end = date.today()
start = end - timedelta(days=30)
fmt = lambda d: d.strftime("%d-%m-%Y")


def fetch(series: str) -> int:
    sc = series.replace("_", ".")
    uri = f"/series={sc}&startDate={fmt(start)}&endDate={fmt(end)}&type=json"
    r = requests.get(BASE + uri, headers={"key": KEY}, timeout=12)
    if r.status_code != 200:
        return 0
    return len(r.json().get("items", []))


US_ISINS = [
    "US900123AT75",
    "US900123AY60",
    "US900123BJ84",
    "US900123CM05",
    "US900123CG37",
    "US900123BB58",
    "US900123DA57",
]

for isin in US_ISINS:
    for suf in ["", "_ORAN"]:
        s = f"TP_{isin}{suf}"
        n = fetch(s)
        if n > 0:
            print("HIT", s, n)
