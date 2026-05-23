/**
 * Terminal grafik + ısı haritası — aynı takvim pencereleri (`Market.tsx` ile senkron).
 * 1G: hafta sonu/tatilde tek mum riski için ~5 takvim günü (market-data `days` ile uyumlu).
 */
export const CHART_RANGE_SEQUENCE = ['1D', '1W', '1M', '3M', '6M', '1Y', '2Y'] as const;
export type ChartRangeId = (typeof CHART_RANGE_SEQUENCE)[number];

export const RANGE_TO_DAYS: Record<ChartRangeId, number> = {
    '1D': 5,
    '1W': 7,
    '1M': 30,
    '3M': 90,
    '6M': 180,
    '1Y': 365,
    '2Y': 730,
};

/** market-data `/api/market/history/batch` ve `/metals/history?days=` izin listesi. */
export const MARKET_API_ALLOWED_DAYS = [1, 3, 5, 7, 14, 30, 90, 180, 365, 730] as const;

/** Takvim gününü API'nin kabul ettiği en yakın üst sınıra yuvarlar (731 → 730). */
export function snapMarketHistoryDays(rawDays: number): number {
    const d = Math.max(1, Math.floor(rawDays));
    if ((MARKET_API_ALLOWED_DAYS as readonly number[]).includes(d)) {
        return d;
    }
    let best: number = MARKET_API_ALLOWED_DAYS[0];
    for (const allowed of MARKET_API_ALLOWED_DAYS) {
        if (allowed <= d) {
            best = allowed;
        }
    }
    return best;
}

/** Geriye dönük isimler — `heatmapApproxPct` vb. */
export const HEATMAP_RANGE_SEQUENCE = CHART_RANGE_SEQUENCE;
export type HeatmapChartRangeId = ChartRangeId;
export const HEATMAP_RANGE_TO_DAYS = RANGE_TO_DAYS;
