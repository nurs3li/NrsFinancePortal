"""
CSV'leri tarayip donem dengesi olan whitelist icin kategori bazinda
en yakin vadeli + yeterli kapsama olan kontratlari listele.

Hedef:
- 10 Doviz (USDTRY + EURTRY/EURUSD en yakin vadeler)
- 1 Endeks (BIST30 en yakin vade)
- 2 Emtia (Gram Altin TRY + ONS Altin USD vadeli)
Toplam 13 kontrat.
"""
from __future__ import annotations

import csv
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FILES = sorted(ROOT.glob("viop_*.csv"))
TOTAL = len(FILES)


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
                if not code:
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


def is_futures(code: str) -> bool:
    return code.startswith("F_")


def pick(data: dict, predicate, *, top_n: int, label: str, min_coverage: int = 10):
    rows = [
        (code, meta) for code, meta in data.items()
        if is_futures(code)
        and len(meta["files"]) >= min_coverage
        and predicate(code, meta)
    ]
    rows.sort(key=lambda kv: (kv[1]["expiry"] or "9999", -kv[1]["volume"]))
    seen_expiry: set[str] = set()
    out: list[tuple[str, dict]] = []
    for code, meta in rows:
        if meta["expiry"] in seen_expiry:
            continue
        seen_expiry.add(meta["expiry"])
        out.append((code, meta))
        if len(out) >= top_n:
            break
    print(f"\n=== {label} (min coverage {min_coverage}/{TOTAL}) ===")
    print(f"{'#':>2} {'KONTRAT':<22} {'DAYANAK':<14} {'VADE':<11} {'KAPS':>5} {'HACIM':>14} {'PRIM_TL':>18}")
    for i, (code, meta) in enumerate(out, 1):
        coverage = f"{len(meta['files'])}/{TOTAL}"
        print(f"{i:>2} {code:<22} {meta['underlying']:<14} {meta['expiry']:<11} {coverage:>5} {meta['volume']:>14,.0f} {meta['traded_value']:>18,.0f}")
    return out


def main() -> int:
    print(f"Toplam {TOTAL} CSV dosyasi.")
    data = scan()

    fx_usd = pick(
        data,
        lambda c, m: m["underlying"].upper() in {"D_USDTRY", "USDTRY"},
        top_n=8,
        label="USDTRY VADELERI (en yakin 8)",
        min_coverage=15,
    )
    fx_eur = pick(
        data,
        lambda c, m: m["underlying"].upper() in {"D_EURTRY", "EURTRY", "D_EURUSD", "EURUSD"},
        top_n=2,
        label="EUR VADELERI (en yakin 2: EURTRY veya EURUSD)",
        min_coverage=10,
    )
    idx = pick(
        data,
        lambda c, m: m["underlying"].upper() in {"D_XU030D", "D_XU030", "XU030", "XU030D"} or "XU030" in c.upper(),
        top_n=1,
        label="BIST30 ENDEKS VADESI (en yakin 1)",
        min_coverage=10,
    )
    gold_try = pick(
        data,
        lambda c, m: m["underlying"].upper() in {"D_XAUTRY", "XAUTRY"} or "XAUTRYM" in c.upper(),
        top_n=1,
        label="GRAM ALTIN (XAUTRY) VADESI",
        min_coverage=10,
    )
    gold_usd = pick(
        data,
        lambda c, m: m["underlying"].upper() in {"D_XAUUSD", "XAUUSD"},
        top_n=1,
        label="ONS ALTIN (XAUUSD) VADESI",
        min_coverage=10,
    )

    final = fx_usd + fx_eur + idx + gold_try + gold_usd
    print("\n=== ONERILEN WHITELIST (kategori dengesi) ===")
    print(f"{'#':>2} {'KONTRAT':<22} {'KATEGORI':<12} {'DAYANAK':<14} {'VADE':<11}")
    cat_labels = (
        [("USDTRY", row) for row in fx_usd]
        + [("EUR", row) for row in fx_eur]
        + [("BIST30", row) for row in idx]
        + [("ALTIN_TRY", row) for row in gold_try]
        + [("ALTIN_USD", row) for row in gold_usd]
    )
    for i, (cat, (code, meta)) in enumerate(cat_labels, 1):
        print(f"{i:>2} {code:<22} {cat:<12} {meta['underlying']:<14} {meta['expiry']:<11}")
    print(f"\nToplam: {len(cat_labels)} kontrat")

    out_path = ROOT / "_recommended_whitelist.csv"
    with out_path.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["rank", "category", "contract", "underlying", "expiry", "coverage", "total_volume"])
        for i, (cat, (code, meta)) in enumerate(cat_labels, 1):
            w.writerow([i, cat, code, meta["underlying"], meta["expiry"], f"{len(meta['files'])}/{TOTAL}", int(meta["volume"])])
    print(f"Cikti: {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
