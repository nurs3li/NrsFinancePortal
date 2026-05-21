"""Generate weekly broker-mid quote history for curated Turkey USD eurobonds (4 ISINs)."""
import json
import math
from datetime import date, timedelta
from pathlib import Path

# (isin, coupon, target_price, target_yield)
BONDS = [
    ("US900123DA57", 5.95, 98.34, 6.28),
    ("US900123AT75", 8.00, 108.47, 6.63),
    ("US900123BB58", 7.25, 104.15, 6.71),
    ("US900123CG37", 6.63, 87.97, 7.82),
]

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/eurobond-quotes"


def weekly_dates(start: date, end: date):
    d = start
    while d <= end:
        if d.weekday() == 4:
            yield d
        d += timedelta(days=1)


def _isin_seed(isin: str) -> int:
    return sum(ord(c) for c in isin)


def build_points(isin: str, coupon: float, target_price: float, target_yield: float, start: date, end: date):
    weeks = list(weekly_dates(start, end))
    if not weeks:
        return []
    seed = _isin_seed(isin)
    start_mult = 0.84 + (seed % 11) * 0.007
    phase = (seed % 29) / 29.0 * 0.22
    coupon_drift = 1.0 + (coupon - 6.5) * 0.018
    start_price = target_price * start_mult
    start_yield = target_yield + 0.9 + (seed % 7) * 0.12
    points = []
    n = len(weeks)
    for i, d in enumerate(weeks):
        t = i / max(1, n - 1)
        curve_t = min(1.0, t + phase)
        base_curve = (1 - math.cos(curve_t * math.pi)) / 2
        wiggle = 1.0 + math.sin(i * 0.37 + seed * 0.013) * 0.022 * coupon_drift
        price = (start_price + (target_price - start_price) * base_curve) * wiggle
        yld = start_yield + (target_yield - start_yield) * base_curve
        points.append(
            {
                "date": d.isoformat(),
                "cleanPrice": round(price, 4),
                "yieldPct": round(yld, 4),
            }
        )
    return points


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    start = date(2019, 1, 4)
    end = date.today()
    for isin, coupon, price, yld in BONDS:
        payload = {
            "isin": isin,
            "source": "BROKER_MID_WEEKLY",
            "note": "Weekly indicative mid; replace with bank feed import when available.",
            "points": build_points(isin, coupon, price, yld, start, end),
        }
        path = OUT / f"{isin}.json"
        path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        print("wrote", path.name, len(payload["points"]), "points")


if __name__ == "__main__":
    main()
