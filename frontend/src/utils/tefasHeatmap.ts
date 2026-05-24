import type { TreemapTile } from '../components/market/MarketFinvizTreemap';
import type { ChartRangeId } from '../components/market/heatmapRange';
import type { MarketTerminalListItem } from '../services/marketTerminalListApi';
import { terminalListItemToVm } from './marketTerminalListVm';

/** Detaylı ısı haritasında tüm TEFAS fonları tek sektör kutusunda (şemsiye türüne göre ayrılmaz). */
export const HEATMAP_SECTOR_TEFAS_FUNDS = 'TEFAS_FUNDS';

function finitePct(v: unknown): number | null {
    if (v == null) return null;
    const n = typeof v === 'number' ? v : Number(v);
    return Number.isFinite(n) ? n : null;
}

/** TEFAS satırındaki getiri kolonları → seçili grafik / ısı haritası aralığı. */
export function tefasReturnPctForChartRange(
    row: {
        pctDay?: number | null;
        pctWeek?: number | null;
        pctMonth?: number | null;
        pctYear?: number | null;
        fundReturn3m?: number | null;
        fundReturn6m?: number | null;
        changePercent?: number;
    },
    range: ChartRangeId,
): number {
    const day = finitePct(row.pctDay);
    const week = finitePct(row.pctWeek);
    const month = finitePct(row.pctMonth);
    const ret3m = finitePct(row.fundReturn3m);
    const ret6m = finitePct(row.fundReturn6m);
    const year = finitePct(row.pctYear);
    const ytd = finitePct(row.changePercent);
    switch (range) {
        case '1D':
            return day ?? ytd ?? 0;
        case '1W':
            return week ?? day ?? ytd ?? 0;
        case '1M':
            return month ?? week ?? day ?? ytd ?? 0;
        case '3M':
            return ret3m ?? month ?? week ?? day ?? 0;
        case '6M':
            return ret6m ?? ret3m ?? month ?? week ?? day ?? 0;
        case '1Y':
            return year ?? month ?? 0;
        case '2Y':
            return year ?? ytd ?? month ?? 0;
        default:
            return month ?? day ?? 0;
    }
}

export function tefasHeatmapSortForRange(range: ChartRangeId): string {
    switch (range) {
        case '1D':
            return 'return1d';
        case '1W':
            return 'return1w';
        case '1M':
            return 'return1m';
        case '3M':
            return 'return3m';
        case '6M':
            return 'return6m';
        case '1Y':
        case '2Y':
            return 'return1y';
        default:
            return 'return1m';
    }
}

export function buildTefasTreemapTiles(items: MarketTerminalListItem[], range: ChartRangeId): TreemapTile[] {
    return items
        .filter((item) => item.symbol && Number.isFinite(item.price) && item.price > 0)
        .map((item) => {
            const vm = terminalListItemToVm(item);
            const changePercent = tefasReturnPctForChartRange(vm, range);
            const umbrellaType = (item.sector ?? item.displayName ?? '').trim() || null;
            return {
                sector: HEATMAP_SECTOR_TEFAS_FUNDS,
                industry: umbrellaType,
                symbol: item.symbol,
                assetClass: 'FUND',
                changePercent,
                layoutWeight: Math.max(0.35, Math.abs(changePercent) + 0.25),
                changeHorizon: range,
            };
        });
}
