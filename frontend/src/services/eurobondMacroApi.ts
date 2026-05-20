import { marketClient } from '../api/client';
import type {
    EurobondMacroBatchHistory,
    EurobondMacroOverview,
    EurobondMacroSeriesPoint,
} from '../types/eurobondMacro';

export const eurobondMacroQueryKeys = {
    overview: () => ['macro', 'eurobond', 'overview'] as const,
    batchHistory: (series: string, from: string, to: string) =>
        ['macro', 'eurobond', 'batch-history', series, from, to] as const,
};

export const EUROBOND_MACRO_SERIES = {
    bookValue: 'TP_EBONDYAZDEG_ST',
    marketValue: 'TP_EBONDPIYDEG_ST',
    total: 'TP_EBONDVADE_C8_ST',
    originalShort: 'TP_EBONDVADE_C1_ST',
    originalLong: 'TP_EBONDVADE_C2_ST',
    remainingShort: 'TP_EBONDVADE_C3_ST',
    remainingLong: 'TP_EBONDVADE_C4_ST',
    usd: 'TP_EBONDVADE_C5_ST',
    eur: 'TP_EBONDVADE_C6_ST',
    jpy: 'TP_EBONDVADE_C7_ST',
} as const;

type ApiOverview = {
    available: boolean;
    frequencyLabel: string;
    unitLabel: string;
    sourceLabel: string;
    asOfDate?: string | null;
    latest?: {
        marketValue?: number | null;
        bookValue?: number | null;
        totalDistribution?: number | null;
        remainingShort?: number | null;
        remainingLong?: number | null;
        originalShort?: number | null;
        originalLong?: number | null;
        usdIssues?: number | null;
        eurIssues?: number | null;
        jpyIssues?: number | null;
    };
    shares?: {
        usdSharePct?: number | null;
        eurSharePct?: number | null;
        jpySharePct?: number | null;
        remainingLongSharePct?: number | null;
        remainingShortSharePct?: number | null;
    };
};

type ApiBatchHistory = {
    available: boolean;
    frequencyLabel: string;
    unitLabel: string;
    sourceLabel: string;
    series: Record<
        string,
        {
            seriesCode: string;
            label: string;
            seriesKey: string;
            points: { date: string; value: number | string }[];
        }
    >;
};

function ymdYearsAgo(years: number): string {
    const d = new Date();
    d.setFullYear(d.getFullYear() - years);
    return d.toISOString().slice(0, 10);
}

export function eurobondMacroDefaultFromYmd(years = 5): string {
    return ymdYearsAgo(years);
}

export function eurobondMacroDefaultToYmd(): string {
    return new Date().toISOString().slice(0, 10);
}

function mapOverview(raw: ApiOverview | undefined): EurobondMacroOverview {
    const latest = raw?.latest ?? {};
    const shares = raw?.shares ?? {};
    return {
        available: Boolean(raw?.available),
        frequencyLabel: raw?.frequencyLabel ?? 'Haftalık',
        unitLabel: raw?.unitLabel ?? 'milyon ABD doları',
        sourceLabel: raw?.sourceLabel ?? 'TCMB EVDS',
        asOfDate: raw?.asOfDate ?? null,
        marketValue: latest.marketValue ?? null,
        bookValue: latest.bookValue ?? null,
        totalDistribution: latest.totalDistribution ?? null,
        remainingShort: latest.remainingShort ?? null,
        remainingLong: latest.remainingLong ?? null,
        originalShort: latest.originalShort ?? null,
        originalLong: latest.originalLong ?? null,
        usdIssues: latest.usdIssues ?? null,
        eurIssues: latest.eurIssues ?? null,
        jpyIssues: latest.jpyIssues ?? null,
        distribution: {
            usdSharePct: shares.usdSharePct ?? null,
            eurSharePct: shares.eurSharePct ?? null,
            jpySharePct: shares.jpySharePct ?? null,
            remainingLongSharePct: shares.remainingLongSharePct ?? null,
            remainingShortSharePct: shares.remainingShortSharePct ?? null,
        },
    };
}

function mapBatchHistory(raw: ApiBatchHistory | undefined): EurobondMacroBatchHistory {
    const series: EurobondMacroBatchHistory['series'] = {};
    if (raw?.series) {
        for (const [code, s] of Object.entries(raw.series)) {
            const points: EurobondMacroSeriesPoint[] = (s.points ?? [])
                .map((p) => ({
                    date: String(p.date),
                    value: Number(p.value),
                }))
                .filter((p) => p.date && Number.isFinite(p.value));
            series[code] = {
                seriesCode: s.seriesCode ?? code,
                label: s.label ?? code,
                seriesKey: s.seriesKey ?? code,
                points,
            };
        }
    }
    return {
        available: Boolean(raw?.available),
        frequencyLabel: raw?.frequencyLabel ?? 'Haftalık',
        unitLabel: raw?.unitLabel ?? 'milyon ABD doları',
        sourceLabel: raw?.sourceLabel ?? 'TCMB EVDS',
        series,
    };
}

export async function fetchEurobondMacroOverview(signal?: AbortSignal): Promise<EurobondMacroOverview> {
    const { data } = await marketClient.get<ApiOverview>('/api/market/eurobonds/overview', { signal });
    return mapOverview(data);
}

export async function fetchEurobondMacroBatchHistory(
    params: { series: string[]; from: string; to: string },
    signal?: AbortSignal,
): Promise<EurobondMacroBatchHistory> {
    const { data } = await marketClient.get<ApiBatchHistory>('/api/market/eurobonds/batch-history', {
        params: {
            series: params.series.join(','),
            from: params.from,
            to: params.to,
        },
        signal,
    });
    return mapBatchHistory(data);
}
