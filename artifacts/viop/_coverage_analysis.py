"""
VIOP CSV dosyalarını tarayıp her kontrat için günlük kapsama ve toplam hacmi raporlar.
Amac: 17/17 dosyada veri bulunan ve toplam işlem hacmi en yüksek olan 20 kontratı seçmek.
Bu kontratlar frontend/backend whitelist'i olarak kullanılacak.
"""
from __future__ import annotations

import csv
import os
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def safe_float(value: str) -> float:
    if value is None:
        return 0.0
    s = value.strip().replace(",", ".")
    if not s:
        return 0.0
    try:
        return float(s)
    except ValueError:
        return 0.0


def parse_file(path: Path) -> list[dict]:
    rows: list[dict] = []
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.reader(f, delimiter=";")
        header_tr = next(reader, None)
        header_en = next(reader, None)
        if not header_tr or not header_en:
            return rows
        for row in reader:
            if not row or len(row) < 22:
                continue
            rows.append(
                {
                    "date": row[0].strip(),
                    "contract": row[1].strip(),
                    "name": row[2].strip(),
                    "instrument_type": row[5].strip(),
                    "instrument_class": row[6].strip(),
                    "underlying": row[7].strip(),
                    "expiry": row[8].strip(),
                    "settlement": safe_float(row[9]),
                    "closing": safe_float(row[15]),
                    "traded_value": safe_float(row[17]),
                    "trade_volume_qty": safe_float(row[19]),
                    "trade_count": safe_float(row[20]),
                    "open_interest": safe_float(row[21]),
                }
            )
    return rows


def main() -> int:
    files = sorted(ROOT.glob("viop_*.csv"))
    if not files:
        print("CSV dosyasi bulunamadi.")
        return 1

    print(f"Toplam {len(files)} CSV dosyasi taranacak.")
    total_files = len(files)

    by_contract_files: dict[str, set[str]] = defaultdict(set)
    by_contract_volume: dict[str, float] = defaultdict(float)
    by_contract_traded_value: dict[str, float] = defaultdict(float)
    by_contract_count: dict[str, float] = defaultdict(float)
    by_contract_meta: dict[str, dict] = {}

    for f in files:
        rows = parse_file(f)
        seen_in_file: set[str] = set()
        for r in rows:
            code = r["contract"]
            if not code:
                continue
            seen_in_file.add(code)
            by_contract_volume[code] += r["trade_volume_qty"]
            by_contract_traded_value[code] += r["traded_value"]
            by_contract_count[code] += r["trade_count"]
            if code not in by_contract_meta:
                by_contract_meta[code] = {
                    "name": r["name"],
                    "underlying": r["underlying"],
                    "expiry": r["expiry"],
                    "instrument_type": r["instrument_type"],
                    "instrument_class": r["instrument_class"],
                }
        for c in seen_in_file:
            by_contract_files[c].add(f.name)

    print(f"\nToplam benzersiz kontrat sayisi: {len(by_contract_meta)}")

    coverage_buckets: dict[int, int] = defaultdict(int)
    for code, fileset in by_contract_files.items():
        coverage_buckets[len(fileset)] += 1
    print("\nKapsama dagilimi (kac dosyada gozukuyor -> kontrat sayisi):")
    for n in sorted(coverage_buckets.keys(), reverse=True):
        print(f"  {n}/{total_files} dosyada: {coverage_buckets[n]} kontrat")

    full_coverage = [
        c for c, fs in by_contract_files.items() if len(fs) == total_files
    ]
    print(f"\nTUM dosyalarda ({total_files}/{total_files}) bulunan kontrat: {len(full_coverage)}")

    ranked = sorted(
        full_coverage,
        key=lambda c: (by_contract_volume[c], by_contract_traded_value[c]),
        reverse=True,
    )

    top20 = ranked[:20]
    print("\n=== TOP 20 (17/17 kapsama, islem hacmine gore) ===")
    print(f"{'#':>2} {'KONTRAT':<22} {'UNDERLYING':<12} {'VADE':<11} {'HACIM_KONTRAT':>15} {'PRIM_TL':>18} {'ISLEM_SAY':>11}")
    for i, code in enumerate(top20, 1):
        meta = by_contract_meta[code]
        vol = by_contract_volume[code]
        tv = by_contract_traded_value[code]
        tc = by_contract_count[code]
        print(
            f"{i:>2} {code:<22} {meta['underlying']:<12} {meta['expiry']:<11} {vol:>15,.0f} {tv:>18,.0f} {tc:>11,.0f}"
        )

    # 2. asama: kapsama eşiği daha esnek olan ek aday liste (>=15/17)
    threshold_high = total_files - 2
    ranked_loose = sorted(
        [c for c, fs in by_contract_files.items() if len(fs) >= threshold_high],
        key=lambda c: (by_contract_volume[c], by_contract_traded_value[c]),
        reverse=True,
    )
    extra_after_full = [c for c in ranked_loose if c not in set(top20)][:10]
    if extra_after_full:
        print(f"\n--- Ek aday liste (>= {threshold_high}/{total_files} kapsama, fallback) ---")
        for i, code in enumerate(extra_after_full, 1):
            meta = by_contract_meta[code]
            files_count = len(by_contract_files[code])
            vol = by_contract_volume[code]
            print(
                f"{i:>2} {code:<22} {meta['underlying']:<12} {meta['expiry']:<11} {files_count}/{total_files}  hacim={vol:,.0f}"
            )

    out = ROOT / "_top20_full_coverage.csv"
    with out.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["rank", "contract", "underlying", "expiry", "instrument_type", "files_with_data", "total_volume", "total_traded_value", "total_trade_count"])
        for i, code in enumerate(top20, 1):
            meta = by_contract_meta[code]
            w.writerow([
                i,
                code,
                meta["underlying"],
                meta["expiry"],
                meta["instrument_type"],
                len(by_contract_files[code]),
                int(by_contract_volume[code]),
                int(by_contract_traded_value[code]),
                int(by_contract_count[code]),
            ])
    print(f"\nCikti yazildi: {out}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
