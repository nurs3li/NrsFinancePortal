import type { AssetType } from '../../constants/OrderConstants';
import type { EquityMarketMetadata } from '../market/marketTypes';
import { formatSimMoney, type SimDisplayCurrency } from './simCurrency';
import type {
    ChartMetricMode,
    SimulationPerformancePoint,
    SimulationResultItem,
    SimulationSummaryStats,
} from './types';

export function unwrapData<T>(res: unknown): T {
    const r = res as { data?: { data?: T } | T };
    return (r?.data && typeof r.data === 'object' && 'data' in (r.data as object)
        ? (r.data as { data: T }).data
        : r?.data) as T;
}

export function seriesChartKey(res: SimulationResultItem): string {
    return `${res.assetType}-${res.assetName}-${res.id.slice(-4)}`;
}

export function seriesDisplayName(res: SimulationResultItem): string {
    return res.scenarioLabel?.trim() ? `${res.assetName} · ${res.scenarioLabel.trim()}` : res.assetName;
}

export function assetTypeOptionIcon(t: AssetType): string {
    switch (t) {
        case 'CRYPTO':
            return '₿';
        case 'FX':
            return '💱';
        case 'METAL':
            return '◆';
        case 'FUND':
            return '▣';
        case 'STOCK':
            return '📈';
        case 'BIST':
            return '🏛';
        default:
            return '•';
    }
}

export function formatExportDecimal(n: number, decimals: number): string {
    const f = 10 ** decimals;
    const v = Math.round(n * f) / f;
    return String(v);
}

export function buildSimulationExportRow(
    r: SimulationResultItem,
    fmt: {
        assetType: (a: AssetType) => string;
        quality: (q: string) => string;
        source: (s: string) => string;
    },
): string[] {
    return [
        fmt.assetType(r.assetType),
        r.assetName,
        r.buyDate,
        formatExportDecimal(r.initialAmount, 2),
        formatExportDecimal(r.buyPrice, 4),
        formatExportDecimal(r.currentPrice, 4),
        formatExportDecimal(r.currentValue, 2),
        formatExportDecimal(r.pnl, 2),
        formatExportDecimal(r.pnlPct, 2),
        fmt.source(r.buyPriceSource),
        r.historicalPriceDate,
        fmt.quality(r.qualityFlag),
    ];
}

export function sourceLabel(source: string, t: (k: string, d: string) => string): string {
    const s = String(source ?? '').toUpperCase();
    if (s === 'SYSTEM_HISTORY') return t('simulation.sourceHistory', 'Sistem geçmiş fiyatı');
    if (s === 'SYSTEM_LATEST_FALLBACK') return t('simulation.sourceLatestFallback', 'Sistem son fiyat (yedek)');
    if (s === 'USER_INPUT') return t('simulation.sourceManual', 'Kullanıcı manuel fiyatı');
    return source || t('simulation.sourceUnknown', 'Sistem');
}

export function qualityLabel(quality: string, t: (k: string, d: string) => string): string {
    const q = String(quality ?? '').toUpperCase();
    if (q === 'EXACT') return t('simulation.qualityExact', 'EXACT — seçilen gün');
    if (q === 'EXACT_HOURLY') return t('simulation.qualityExactHourly', 'EXACT_HOURLY — seçilen günün son saatlik verisi');
    if (q === 'PREVIOUS_DAY') return t('simulation.qualityPrevious', 'PREVIOUS_DAY — önceki işlem günü');
    if (q === 'FALLBACK') return t('simulation.qualityFallback', 'FALLBACK — en yakın geçerli tarih');
    return quality || t('simulation.qualityUnknown', 'Belirtilmedi');
}

export function qualityPillClass(quality: string): string {
    const q = String(quality ?? '').toUpperCase();
    if (q === 'EXACT' || q === 'EXACT_HOURLY') return 'quality-pill quality-pill-exact';
    if (q === 'PREVIOUS_DAY') return 'quality-pill quality-pill-prev';
    return 'quality-pill quality-pill-fallback';
}

export function buyDateYmdMonthsAgo(months: number): string {
    const d = new Date();
    d.setMonth(d.getMonth() - months);
    return d.toISOString().slice(0, 10);
}

export function computeSummaryStats(results: SimulationResultItem[]): SimulationSummaryStats {
    if (results.length === 0) {
        return {
            count: 0,
            totalInitial: 0,
            totalCurrent: 0,
            totalPnl: 0,
            avgReturnPct: 0,
            best: null,
            worst: null,
        };
    }
    const totalInitial = results.reduce((s, r) => s + r.initialAmount, 0);
    const totalCurrent = results.reduce((s, r) => s + r.currentValue, 0);
    const totalPnl = totalCurrent - totalInitial;
    const avgReturnPct = results.reduce((s, r) => s + r.pnlPct, 0) / results.length;
    const byPnl = [...results].sort((a, b) => b.pnl - a.pnl);
    return {
        count: results.length,
        totalInitial,
        totalCurrent,
        totalPnl,
        avgReturnPct,
        best: byPnl[0] ?? null,
        worst: byPnl[byPnl.length - 1] ?? null,
    };
}

type OverviewPriceRow = { buyPrice?: number; sellPrice?: number } & EquityMarketMetadata;

export type OverviewLite = {
    doviz?: Record<string, OverviewPriceRow>;
    metals?: Record<string, OverviewPriceRow>;
    crypto?: Record<string, OverviewPriceRow>;
    funds?: Record<string, OverviewPriceRow>;
    stocks?: Record<string, OverviewPriceRow>;
};

function midFromRow(row?: OverviewPriceRow | null): number | null {
    if (!row) return null;
    const b = Number(row.buyPrice ?? 0);
    const s = Number(row.sellPrice ?? 0);
    if (b > 0 && s > 0) return (b + s) / 2;
    if (b > 0) return b;
    if (s > 0) return s;
    return null;
}

export function liveTryFromOverview(
    o: OverviewLite | null | undefined,
    assetType: AssetType,
    symbol: string,
): number | null {
    if (!o) return null;
    const sym = String(symbol ?? '').toUpperCase();
    const usdTry = midFromRow(o.doviz?.['USDTRY']);
    const usdMul = (usd: number | null) => {
        if (usd == null || usd <= 0) return null;
        if (!usdTry || usdTry <= 0) return null;
        return usd * usdTry;
    };
    switch (assetType) {
        case 'FX':
            return midFromRow(o.doviz?.[sym]);
        case 'METAL':
            return midFromRow(o.metals?.[sym]);
        case 'CRYPTO':
            return usdMul(midFromRow(o.crypto?.[sym]));
        case 'FUND':
            return usdMul(midFromRow(o.funds?.[sym]));
        case 'STOCK':
            return usdMul(midFromRow(o.stocks?.[sym]));
        case 'BIST':
            return null;
        default:
            return null;
    }
}

export function mergeLiveIntoSeries(
    series: SimulationPerformancePoint[],
    buyPricePerUnit: number,
    liveUnitPrice: number,
): SimulationPerformancePoint[] {
    if (buyPricePerUnit <= 0 || liveUnitPrice <= 0) return series;
    const today = new Date().toISOString().slice(0, 10);
    const cumulativeReturnPct = ((liveUnitPrice - buyPricePerUnit) / buyPricePerUnit) * 100;
    const sorted = [...series].sort((a, b) => a.date.localeCompare(b.date));
    const last = sorted[sorted.length - 1];
    if (last?.date === today) {
        sorted[sorted.length - 1] = { date: today, priceTry: liveUnitPrice, cumulativeReturnPct };
        return sorted;
    }
    sorted.push({ date: today, priceTry: liveUnitPrice, cumulativeReturnPct });
    return sorted;
}

/** Ortak tarih ekseninde serileri birleştir; metrik moduna göre değer üret */
export function buildSimulationChartData(
    visibleResults: SimulationResultItem[],
    metricMode: ChartMetricMode,
): Array<Record<string, number | string>> {
    if (!visibleResults.length) return [];

    const keys = visibleResults.map((res) => seriesChartKey(res));
    const units = visibleResults.map((res) => (res.buyPrice > 0 ? res.initialAmount / res.buyPrice : 0));
    const seriesMaps = visibleResults.map((res) => new Map(res.series.map((p) => [p.date, p] as const)));

    const dateSet = new Set<string>();
    visibleResults.forEach((res) => res.series.forEach((p) => dateSet.add(p.date)));
    const sortedDates = [...dateSet].sort((a, b) => a.localeCompare(b));

    const rows: Array<Record<string, number | string>> = [];
    const lastPoints = new Array<SimulationPerformancePoint | null>(visibleResults.length).fill(null);

    for (const date of sortedDates) {
        const row: Record<string, number | string> = { date };
        let hasAny = false;
        visibleResults.forEach((res, i) => {
            const key = keys[i]!;
            const pt = seriesMaps[i]!.get(date) ?? lastPoints[i];
            if (pt == null) return;
            lastPoints[i] = pt;
            hasAny = true;
            if (metricMode === 'RETURN_PCT' || metricMode === 'NORMALIZE') {
                row[key] = pt.cumulativeReturnPct;
            } else if (metricMode === 'VALUE_TRY') {
                row[key] = units[i]! * pt.priceTry;
            } else if (metricMode === 'PNL_TRY') {
                row[key] = units[i]! * pt.priceTry - res.initialAmount;
            }
        });
        if (hasAny) rows.push(row);
    }

    if (metricMode === 'NORMALIZE' && rows.length > 0) {
        const baseline: Record<string, number> = {};
        for (const key of keys) {
            for (const row of rows) {
                const v = row[key];
                if (typeof v === 'number' && Number.isFinite(v)) {
                    baseline[key] = v;
                    break;
                }
            }
        }
        for (const row of rows) {
            for (const key of keys) {
                const v = row[key];
                if (typeof v === 'number' && key in baseline) row[key] = v - baseline[key]!;
            }
        }
    }

    return rows;
}

export function chartYAxisFormatter(
    metricMode: ChartMetricMode,
    locale: string,
    v: number,
    currency: SimDisplayCurrency = 'TRY',
): string {
    if (metricMode === 'RETURN_PCT' || metricMode === 'NORMALIZE') {
        return `${new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(v)}%`;
    }
    return formatSimMoney(locale, v, currency);
}

export function chartTooltipValue(
    metricMode: ChartMetricMode,
    locale: string,
    v: number | undefined,
    currency: SimDisplayCurrency = 'TRY',
): string {
    if (v == null || !Number.isFinite(v)) return '—';
    if (metricMode === 'RETURN_PCT' || metricMode === 'NORMALIZE') {
        return `${Number(v).toLocaleString(locale, { maximumFractionDigits: 2 })}%`;
    }
    return formatSimMoney(locale, Number(v), currency);
}

export function usdTryFromOverview(o: OverviewLite | null | undefined): number | null {
    return midFromRow(o?.doviz?.['USDTRY']);
}

export function livePriceInDisplayCurrency(
    liveTry: number | null,
    displayCurrency: SimDisplayCurrency,
    usdTry: number | null,
): number | null {
    if (liveTry == null || liveTry <= 0) return null;
    if (displayCurrency === 'USD') {
        if (usdTry == null || usdTry <= 0) return null;
        return liveTry / usdTry;
    }
    return liveTry;
}
