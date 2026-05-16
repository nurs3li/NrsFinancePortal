import { useDeferredValue, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { marketClient } from '../api/client';
import { RANGE_TO_DAYS, type ChartRangeId } from '../components/market/heatmapRange';
import { fetchInterestInflationMacroPanel } from '../services/marketDataService';
import { downsampleByDate } from '../utils/chartDownsample';
import {
    PP_CHART_MAX_POINTS,
    buildPurchasingPowerSeries,
    computePurchasingPowerSnapshot,
    lotCostTryAtAnchor,
    type DateValue,
    type PurchasingPowerPoint,
    type PurchasingPowerSnapshot,
} from '../utils/marketPurchasingPower';
import { selectMacroSeries } from '../utils/macroPanelSeries';

type CandlePoint = { time: string; close: number };

type HistoryRow = { buyPrice?: number; sellPrice?: number; timestamp?: string };

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

export type PurchasingPowerData = {
    series: PurchasingPowerPoint[];
    chartSeries: PurchasingPowerPoint[];
    snapshot: PurchasingPowerSnapshot | null;
    lotCost: number | null;
    loading: boolean;
    anchorDate: string;
};

export function usePurchasingPowerData(
    enabled: boolean,
    range: ChartRangeId,
    candles: CandlePoint[],
    chartAnchorDate: string,
    assetInTry: boolean,
    summaryAnchorDate?: string,
): PurchasingPowerData {
    const chartAnchor = chartAnchorDate.slice(0, 10);
    const summaryAnchor = (summaryAnchorDate ?? chartAnchorDate).slice(0, 10);
    const deferredChartAnchor = useDeferredValue(chartAnchor);
    const chartDays = RANGE_TO_DAYS[range] ?? 365;

    const assetUnit: DateValue[] = useMemo(() => mergeCandleSeries(candles), [candles]);

    const { data: usdTryHistory, isLoading: loadingFx } = useQuery({
        queryKey: ['market', 'usdtry-history', chartDays],
        queryFn: async () => {
            const res = await marketClient.get('/api/market/doviz/history', {
                params: { symbol: 'USDTRY', days: chartDays },
            });
            return unwrapHistory(res);
        },
        enabled: enabled && !assetInTry,
        staleTime: 300_000,
    });

    const { data: panel, isLoading: loadingMacro } = useQuery({
        queryKey: ['market', 'macro', 'interest-inflation-panel'],
        queryFn: ({ signal }) => fetchInterestInflationMacroPanel(signal),
        enabled,
        staleTime: 300_000,
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

    const buildParams = useMemo(
        () => ({
            assetUnitPriceByDate: assetUnit,
            assetInTry,
            usdTryByDate: usdTry,
            cpiIndexByDate: cpi,
            depositRatePctAnnual: deposit,
        }),
        [assetUnit, assetInTry, usdTry, cpi, deposit],
    );

    const chartLotCost = useMemo(() => {
        if (!chartAnchor) return null;
        return lotCostTryAtAnchor({ anchorDate: chartAnchor, ...buildParams });
    }, [chartAnchor, buildParams]);

    const series: PurchasingPowerPoint[] = useMemo(() => {
        if (!enabled || !deferredChartAnchor || chartLotCost == null || chartLotCost <= 0) return [];
        return buildPurchasingPowerSeries({
            anchorDate: deferredChartAnchor,
            lotCostTry: chartLotCost,
            ...buildParams,
        });
    }, [enabled, deferredChartAnchor, chartLotCost, buildParams]);

    const chartSeries = useMemo(
        () => downsampleByDate(series, PP_CHART_MAX_POINTS, [deferredChartAnchor]),
        [series, deferredChartAnchor],
    );

    const snapshot: PurchasingPowerSnapshot | null = useMemo(() => {
        if (!enabled || !summaryAnchor) return null;
        const lot = lotCostTryAtAnchor({ anchorDate: summaryAnchor, ...buildParams });
        if (lot == null || lot <= 0) return null;
        const snapSeries = buildPurchasingPowerSeries({
            anchorDate: summaryAnchor,
            lotCostTry: lot,
            ...buildParams,
        });
        if (!snapSeries.length) return null;
        return computePurchasingPowerSnapshot(snapSeries, lot, summaryAnchor);
    }, [enabled, summaryAnchor, buildParams]);

    const loading = (enabled && !chartAnchor) || loadingMacro || (!assetInTry && loadingFx);

    return {
        series,
        chartSeries,
        snapshot,
        lotCost: chartLotCost,
        loading,
        anchorDate: summaryAnchor || chartAnchor,
    };
}
