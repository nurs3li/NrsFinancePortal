import { useMemo } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { marketClient } from '../api/client';
import { RANGE_TO_DAYS, snapMarketHistoryDays, type ChartRangeId } from '../components/market/heatmapRange';
import { fetchInterestInflationMacroPanel } from '../services/marketDataService';
import { istanbulTodayYmd } from '../components/simulation/simDates';
import { downsampleByDate } from '../utils/chartDownsample';
import {
    PP_CHART_MAX_POINTS,
    computePurchasingPowerHistoryDays,
    type DateValue,
} from '../utils/marketPurchasingPower';
import {
    buildPreciousMetalTryComparison,
    type AssetInflationDepositComparison,
    resolvePreciousMetalComparisonUnit,
} from '../utils/preciousMetalComparison';
import { selectMacroSeries } from '../utils/macroPanelSeries';

type CandlePoint = { time: string; close: number };

type HistoryRow = { buyPrice?: number; sellPrice?: number; timestamp?: string; asOf?: string };

function unwrapHistory(res: unknown): HistoryRow[] {
    const r = res as { data?: { data?: HistoryRow[] } | HistoryRow[] };
    const d = r?.data;
    if (Array.isArray(d)) return d;
    if (d && typeof d === 'object' && 'data' in d && Array.isArray((d as { data: HistoryRow[] }).data)) {
        return (d as { data: HistoryRow[] }).data;
    }
    return [];
}

function mid(row: HistoryRow): number | null {
    const b = Number(row.buyPrice ?? 0);
    const s = Number(row.sellPrice ?? 0);
    if (b > 0 && s > 0) return (b + s) / 2;
    if (b > 0) return b;
    if (s > 0) return s;
    return null;
}

function mergeCandleSeries(candles: CandlePoint[]): DateValue[] {
    const byDate = new Map<string, number>();
    for (const c of candles) {
        const date = c.time.slice(0, 10);
        if (date && c.close > 0) byDate.set(date, c.close);
    }
    return [...byDate.entries()]
        .map(([date, value]) => ({ date, value }))
        .sort((a, b) => a.date.localeCompare(b.date));
}

function historyRowsToDateValues(rows: HistoryRow[]): DateValue[] {
    const byDate = new Map<string, number>();
    for (const row of rows) {
        const m = mid(row);
        const ts = row.timestamp ?? row.asOf;
        const date = ts ? String(ts).slice(0, 10) : '';
        if (m != null && date) byDate.set(date, m);
    }
    return [...byDate.entries()]
        .map(([date, value]) => ({ date, value }))
        .sort((a, b) => a.date.localeCompare(b.date));
}

function formatEuropeIstanbulDateOnly(date: Date): string {
    return date.toLocaleDateString('en-CA', { timeZone: 'Europe/Istanbul' });
}

function metalsHistoryParams(symbol: string, fromYmd: string, toYmd: string): Record<string, string> {
    const sym = symbol.trim().toUpperCase().replace(/\s+/g, '');
    const apiSym = sym === 'ALTIN_TRY' ? 'XAU_TRY' : sym;
    if (apiSym === 'XAU_TRY') {
        const span = Math.max(7, Math.ceil((Date.parse(`${toYmd}T12:00:00`) - Date.parse(`${fromYmd}T12:00:00`)) / 86_400_000) + 1);
        return { symbol: apiSym, days: String(snapMarketHistoryDays(Math.min(3650, span))) };
    }
    return { symbol: apiSym, from: fromYmd, to: toYmd };
}

export type PreciousMetalComparisonData = {
    comparison: AssetInflationDepositComparison | null;
    chartSeries: AssetInflationDepositComparison['series'];
    loading: boolean;
    isRefreshing: boolean;
    anchorDate: string;
    unit: ReturnType<typeof resolvePreciousMetalComparisonUnit>;
    initialAssetValueTRY: number | null;
};

export function usePreciousMetalComparisonData(
    enabled: boolean,
    symbol: string,
    range: ChartRangeId,
    candles: CandlePoint[],
    anchorDate: string,
): PreciousMetalComparisonData {
    const anchor = anchorDate.slice(0, 10);
    const today = istanbulTodayYmd();
    const chartDays = RANGE_TO_DAYS[range] ?? 365;
    const unit = useMemo(() => resolvePreciousMetalComparisonUnit(symbol), [symbol]);

    const historyDays = useMemo(
        () => (anchor ? computePurchasingPowerHistoryDays(chartDays, anchor, today) : chartDays),
        [chartDays, anchor, today],
    );

    const historyFrom = useMemo(() => {
        if (!anchor) return '';
        const fromMs = Date.now() - historyDays * 86_400_000;
        return formatEuropeIstanbulDateOnly(new Date(fromMs));
    }, [anchor, historyDays]);

    const candleAsset = useMemo(() => mergeCandleSeries(candles), [candles]);

    const { data: extendedMetalHistory, isPending: pendingMetalHist, isFetching: fetchingMetalHist } =
        useQuery({
            queryKey: ['market', 'metals-pp-history', symbol, historyFrom, today],
            queryFn: async ({ signal }) => {
                const sym = symbol.trim().toUpperCase().replace(/\s+/g, '');
                const apiSym = sym === 'ALTIN_TRY' ? 'XAU_TRY' : sym;
                const rows = await marketClient
                    .get<HistoryRow[]>('/api/market/metals/history', {
                        params: metalsHistoryParams(apiSym, historyFrom, today),
                        signal,
                    })
                    .then((r) => r.data);
                return historyRowsToDateValues(rows ?? []);
            },
            enabled: enabled && Boolean(symbol && anchor && historyFrom),
            staleTime: 300_000,
            placeholderData: keepPreviousData,
        });

    const assetUnit: DateValue[] = useMemo(() => {
        const map = new Map<string, number>();
        for (const o of extendedMetalHistory ?? []) map.set(o.date, o.value);
        for (const o of candleAsset) map.set(o.date, o.value);
        return [...map.entries()]
            .map(([date, value]) => ({ date, value }))
            .sort((a, b) => a.date.localeCompare(b.date));
    }, [extendedMetalHistory, candleAsset]);

    const { data: usdTryHistory, isPending: pendingFx, isFetching: fetchingFx } = useQuery({
        queryKey: ['market', 'usdtry-history', 'pp', historyFrom, today],
        queryFn: async ({ signal }) => {
            const res = await marketClient.get('/api/market/doviz/history', {
                params: { symbol: 'USDTRY', days: historyDays },
                signal,
            });
            return unwrapHistory(res);
        },
        enabled: enabled && !unit.assetInTry && Boolean(anchor),
        staleTime: 300_000,
        placeholderData: keepPreviousData,
    });

    const { data: panel, isPending: pendingMacro, isFetching: fetchingMacro } = useQuery({
        queryKey: ['market', 'macro', 'interest-inflation-panel'],
        queryFn: ({ signal }) => fetchInterestInflationMacroPanel(signal),
        enabled,
        staleTime: 300_000,
        placeholderData: keepPreviousData,
    });

    const usdTry: DateValue[] = useMemo(
        () =>
            (usdTryHistory ?? [])
                .map((h) => {
                    const m = mid(h);
                    const ts = h.timestamp ? String(h.timestamp).slice(0, 10) : '';
                    return m != null && ts ? { date: ts, value: m } : null;
                })
                .filter((x): x is DateValue => x != null),
        [usdTryHistory],
    );

    const cpi: DateValue[] = useMemo(() => {
        const s = selectMacroSeries(panel?.series, 'CPI_TR_INDEX');
        return (s?.observations ?? []).map((o) => ({
            date: String(o.date).slice(0, 10),
            value: Number(o.value),
        }));
    }, [panel?.series]);

    const deposit: DateValue[] = useMemo(() => {
        const s = selectMacroSeries(panel?.series, 'DEPOSIT_RATE_TRY_1M_WEEKLY');
        return (s?.observations ?? []).map((o) => ({
            date: String(o.date).slice(0, 10),
            value: Number(o.value),
        }));
    }, [panel?.series]);

    const comparison: AssetInflationDepositComparison | null = useMemo(() => {
        if (!enabled || !anchor) return null;
        return buildPreciousMetalTryComparison({
            symbol,
            anchorDate: anchor,
            assetUnitPriceByDate: assetUnit,
            usdTryByDate: usdTry,
            cpiIndexByDate: cpi,
            depositRatePctAnnual: deposit,
        });
    }, [enabled, anchor, symbol, assetUnit, usdTry, cpi, deposit]);

    const chartSeries = useMemo(() => {
        const s = comparison?.series ?? [];
        return downsampleByDate(
            s.map((p) => ({
                date: p.date,
                assetTry: p.assetValueTRY,
                inflationRealTry: p.inflationAdjustedValueTRY,
                inflationHurdleTry: p.inflationAdjustedValueTRY,
                depositTry: p.depositValueTRY,
            })),
            PP_CHART_MAX_POINTS,
            [anchor],
        ).map((p) => ({
            date: p.date,
            assetValueTRY: p.assetTry,
            inflationAdjustedValueTRY: p.inflationHurdleTry,
            depositValueTRY: p.depositTry,
        }));
    }, [comparison, anchor]);

    const metalRows: DateValue[] = extendedMetalHistory ?? [];
    const usdTryRows: HistoryRow[] = usdTryHistory ?? [];
    const loading =
        (enabled && !anchor) ||
        (enabled && pendingMacro && !panel) ||
        (enabled && pendingMetalHist && metalRows.length === 0) ||
        (enabled && !unit.assetInTry && pendingFx && usdTryRows.length === 0);
    const isRefreshing =
        enabled && (fetchingMetalHist || fetchingFx || fetchingMacro) && !loading;

    return {
        comparison,
        chartSeries,
        loading,
        isRefreshing,
        anchorDate: anchor,
        unit,
        initialAssetValueTRY: comparison?.initialAssetValueTRY ?? null,
    };
}
