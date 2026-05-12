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
