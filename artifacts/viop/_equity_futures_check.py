"""
THYAO / EREGL / SASA / SISE pay vadelileri icin kapsama ve hacim analizi.

CSV'lerdeki F_<TICKER><MMYY> kodlarini taradigimizda:
- coverage = kac CSV'de var
- volume   = toplam islem hacmi
- traded   = toplam islem degeri (TL)

Cikti: her bir hisse icin en yakin/en likit vade onerisi.
"""
from __future__ import annotations

import csv
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FILES = sorted(ROOT.glob("viop_*.csv"))
TOTAL = len(FILES)
TICKERS = ("THYAO", "EREGL", "SASA", "SISE")


def safe_float(s: str) -> float:
    s = (s or "").strip().replace(",", ".")
    if not s:
        return 0.0
    try:
        return float(s)
    except ValueError:
        return 0.0


def scan() -> dict:
    data = defaultdict(lambda: {
        "files": set(),
        "volume": 0.0,
        "traded_value": 0.0,
        "underlying": "",
        "expiry": "",
        "type": "",
        "name": "",
    })
    for f in FILES:
        with f.open("r", encoding="utf-8-sig", newline="") as fh:
            r = csv.reader(fh, delimiter=";")
            next(r, None)
            next(r, None)
            for row in r:
                if not row or len(row) < 22:
                    continue
                code = row[1].strip()
                if not code.startswith("F_"):
                    continue
                if not any(t in code for t in TICKERS):
                    continue
                d = data[code]
                d["files"].add(f.name)
                d["volume"] += safe_float(row[19])
                d["traded_value"] += safe_float(row[17])
                if not d["underlying"]:
                    d["underlying"] = row[7].strip()
                    d["expiry"] = row[8].strip()
                    d["type"] = row[5].strip()
                    d["name"] = row[2].strip()
    return data


def main() -> int:
    print(f"Toplam {TOTAL} CSV dosyasi taraniyor.\n")
    data = scan()
    if not data:
        print("HIC pay vadelisi bulunamadi (THYAO/EREGL/SASA/SISE).")
        return 1

    per_ticker: dict[str, list[tuple[str, dict]]] = {t: [] for t in TICKERS}
    for code, meta in data.items():
        for t in TICKERS:
            if t in code:
                per_ticker[t].append((code, meta))
                break

    for t in TICKERS:
        rows = per_ticker[t]
        if not rows:
            print(f"--- {t}: HIC veri yok ---\n")
            continue
        rows.sort(key=lambda kv: (-len(kv[1]["files"]), kv[1]["expiry"] or "9999", -kv[1]["volume"]))
        print(f"--- {t} ({len(rows)} vade bulundu) ---")
        print(f"{'#':>2} {'KONTRAT':<20} {'VADE':<11} {'KAPS':>7} {'HACIM':>14} {'PRIM_TL':>18}")
        for i, (code, meta) in enumerate(rows[:10], 1):
            coverage = f"{len(meta['files'])}/{TOTAL}"
            print(f"{i:>2} {code:<20} {meta['expiry']:<11} {coverage:>7} {meta['volume']:>14,.0f} {meta['traded_value']:>18,.0f}")
        print()

    print("=== ONERI (her hisse icin en yuksek kapsama + en yakin vade) ===")
    recs: list[tuple[str, str, dict]] = []
    for t in TICKERS:
        rows = per_ticker[t]
        if not rows:
            continue
        # Once kapsama esigi >= TOTAL*0.6, sonra en yakin vade
        eligible = [r for r in rows if len(r[1]["files"]) >= max(8, int(TOTAL * 0.6))]
        if not eligible:
            eligible = rows
        eligible.sort(key=lambda kv: (kv[1]["expiry"] or "9999", -len(kv[1]["files"]), -kv[1]["volume"]))
        recs.append((t, eligible[0][0], eligible[0][1]))
    print(f"{'#':>2} {'HISSE':<8} {'KONTRAT':<20} {'VADE':<11} {'KAPS':>7} {'HACIM':>14}")
    for i, (t, code, meta) in enumerate(recs, 1):
        coverage = f"{len(meta['files'])}/{TOTAL}"
        print(f"{i:>2} {t:<8} {code:<20} {meta['expiry']:<11} {coverage:>7} {meta['volume']:>14,.0f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
