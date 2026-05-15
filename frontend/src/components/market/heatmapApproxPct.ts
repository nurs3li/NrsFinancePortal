import { HEATMAP_RANGE_TO_DAYS, type HeatmapChartRangeId } from './heatmapRange';

/**
 * Sparkline kapanışlarından dönem yüzdesi — `Market.tsx` `effectiveChangePercent` ile aynı pencere:
 * son `days` noktanın ilki → sonuncu ((last-first)/first)*100.
 *
 * Eski sürümde `days === 14` iken `steps = n-1` oluyordu; bu tüm (~30 noktalı) seriyi baz alıyordu.
 * Detaylı ısı haritası 14D seçiliyken piyasa listesi 14G’den belirgin daha yüksek % gösteriyordu.
 */
export function approxPctByDays(closes: readonly number[], days: 1 | 7 | 14): number | null {
    if (!closes || closes.length < 2) return null;
    const n = closes.length;
    const last = closes[n - 1];
    if (!Number.isFinite(last)) return null;
    // 1G: tabloda spark için en az 2 nokta; kısa hareket ≈ son iki örnek (TREND 1D → days:2 ile uyumlu).
    const windowPoints = days === 1 ? 2 : Math.min(days, n);
    const span = Math.min(Math.max(windowPoints, 2), n);
    const baseIdx = n - span;
    const base = closes[baseIdx];
    if (!Number.isFinite(base) || base === 0) return null;
    return ((last - base) / base) * 100;
}

/**
 * Günlük kapanış dizisinden yaklaşık % değişim (son `tradingDays` nokta veya mevcut veri kadarı).
 * Ay ≈ 30, yıl ≈ 252 iş günü; seri kısaysa tüm seri kullanılır.
 */
export function approxPctBySpan(closes: readonly number[], tradingDays: number): number | null {
    if (!closes || closes.length < 2) return null;
    const n = closes.length;
    const last = closes[n - 1];
    if (!Number.isFinite(last)) return null;
    const d = Math.max(1, Math.floor(tradingDays));
    const windowPoints = d === 1 ? 2 : Math.min(d, n);
    const span = Math.min(Math.max(windowPoints, 2), n);
    const base = closes[n - span];
    if (!Number.isFinite(base) || base === 0) return null;
    return ((last - base) / base) * 100;
}

/**
 * Downsample / seyrek sparkline: dizideki n nokta ≈ `assumedSparkCoversCalendarDays` takvim günü
 * kabul edilerek, `calendarDaysRequested` günlük pencereye denk gelen uç noktalar arası %.
 * (Eski mantık: `tradingDays` kadar *array elemanı* alıyordu; 280 nokta / 730 gün seride 180 "gün"
 * sanıp 180 eleman kesmek yanlıştı.)
 */
export function approxPctByCalendarSpan(
    closes: readonly number[],
    calendarDaysRequested: number,
    assumedSparkCoversCalendarDays: number,
): number | null {
    if (!closes || closes.length < 2) return null;
    const n = closes.length;
    const last = closes[n - 1];
    if (!Number.isFinite(last)) return null;
    const req = Math.max(1, Math.floor(calendarDaysRequested));
    const total = Math.max(req, Math.max(7, Math.floor(assumedSparkCoversCalendarDays)));
    const spanPts = Math.max(2, Math.min(n, Math.round((req / total) * n)));
    const base = closes[n - spanPts];
    if (!Number.isFinite(base) || base === 0) return null;
    return ((last - base) / base) * 100;
}

function isUsdOunceMetal(sym: string): boolean {
    return sym.trim().toUpperCase().endsWith('_USD_OZ');
}

/**
 * Isı haritası (detay + rail) — `Market.tsx` `effectiveChangePercent` ile aynı mantık:
 * 1D için hisse tile % önceliği; diğer aralıklarda takvim günü → downsample nokta oranı.
 */
export function approxHeatmapPctFromSpark(
    closes: readonly number[],
    assetClass: string,
    symbol: string,
    range: HeatmapChartRangeId,
    tileChangePercent: number,
): number {
    const calDays = HEATMAP_RANGE_TO_DAYS[range];
    const ac = assetClass.trim().toUpperCase();
    if (range === '1D' && (ac === 'STOCK' || ac === 'BIST')) {
        const t = Number(tileChangePercent);
        if (Number.isFinite(t) && Math.abs(t) > 1e-6) return t;
        const a1 = approxPctByDays(closes as number[], 1);
        if (a1 != null && Math.abs(a1) > 1e-6) return a1;
        const mapped = approxPctByCalendarSpan(closes, calDays, HEATMAP_RANGE_TO_DAYS['2Y']);
        if (mapped != null && Number.isFinite(mapped)) return mapped;
        return Number.isFinite(t) ? t : 0;
    }
    if (range === '1D') {
        const a1 = approxPctByDays(closes as number[], 1);
        if (a1 != null && Math.abs(a1) > 1e-6) return a1;
    }
    const assumed =
        ac === 'STOCK' || ac === 'BIST'
            ? HEATMAP_RANGE_TO_DAYS['2Y']
            : ac === 'METAL' && isUsdOunceMetal(symbol)
              ? HEATMAP_RANGE_TO_DAYS['2Y']
              : 90;
    const mapped = approxPctByCalendarSpan(closes, calDays, assumed);
    if (mapped != null && Math.abs(mapped) > 1e-6) return mapped;
    const win = Math.min(calDays, Math.max(2, closes.length));
    const slice = closes.slice(-win);
    if (slice.length >= 2) {
        const first = Number(slice[0]);
        const last = Number(slice[slice.length - 1]);
        if (Number.isFinite(first) && first > 0 && Number.isFinite(last)) {
            return ((last - first) / first) * 100;
        }
    }
    const approx = approxPctBySpan(closes as number[], calDays);
    if (approx != null && Number.isFinite(approx)) return approx;
    return Number(tileChangePercent) || 0;
}
