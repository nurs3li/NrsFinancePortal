import requests

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "X-Requested-With": "XMLHttpRequest",
    "Accept": "application/json,text/plain,*/*",
    "Referer": "https://www.investing.com/rates-bonds/us900123at75-historical-data",
}

pair_id = "1162745"  # Turkey 2034 from search
url = "https://www.investing.com/instruments/HistoricalDataAjax"
data = {
    "curr_id": pair_id,
    "smlID": "200018",
    "header": "US900123AT75 Historical Data",
    "st_date": "01/01/2024",
    "end_date": "05/01/2026",
    "interval_sec": "Daily",
    "sort_col": "date",
    "sort_ord": "DESC",
    "action": "historical_data",
}

r = requests.post(url, headers=headers, data=data, timeout=20)
print("status", r.status_code)
print(r.text[:500])
