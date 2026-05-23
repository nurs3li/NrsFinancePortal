import { approxPctByCalendarSpan, approxPctBySpan } from '../components/market/heatmapApproxPct';
import { RANGE_TO_DAYS } from '../components/market/heatmapRange';

function finitePct(v: unknown): number | null {
    if (v == null) return null;
    const n = typeof v === 'number' ? v : Number(v);
    return Number.isFinite(n) ? n : null;
}

type HorizonPcts = {
    pctDay: number | null;
    pctWeek: number | null;
    pctMonth: number | null;
    pctYear: number | null;
};

function spotSparkHorizonPct(
    spark: readonly number[],
    chartRange: '1W' | '1M' | '1Y',
): number | null {
    const calDays = RANGE_TO_DAYS[chartRange];
    const mapped = approxPctByCalendarSpan(spark, calDays, RANGE_TO_DAYS['2Y']);
    if (mapped != null && Math.abs(mapped) > 1e-6) return mapped;
    const win = Math.min(calDays, Math.max(2, spark.length));
    const slice = spark.slice(-win);
    if (slice.length >= 2) {
        const first = Number(slice[0]);
        const last = Number(slice[slice.length - 1]);
        if (Number.isFinite(first) && first > 0 && Number.isFinite(last)) {
            return ((last - first) / first) * 100;
        }
    }
    return approxPctBySpan(spark, calDays);
}

/** ABD hisse listesi ile aynı: spark kapanışlarından Gün/Hafta/Ay/Yıl %. */
export function horizonPctsFromSparkCloses(
    closes: readonly number[],
    dayOverride: number | null,
): HorizonPcts {
    const spark = closes.map((x) => Number(x)).filter((x) => Number.isFinite(x) && x > 0);
    if (spark.length < 2) {
        return { pctDay: dayOverride, pctWeek: null, pctMonth: null, pctYear: null };
    }
    return {
        pctDay: dayOverride ?? approxPctBySpan(spark, 1),
        pctWeek: spotSparkHorizonPct(spark, '1W'),
        pctMonth: spotSparkHorizonPct(spark, '1M'),
        pctYear: spotSparkHorizonPct(spark, '1Y'),
    };
}

export type BistHorizonSource = {
    symbol: string;
    sparkline?: number[];
    pctDay?: number | null;
    pctWeek?: number | null;
    pctMonth?: number | null;
    pctYear?: number | null;
    changePercent: number;
    dailyChangePercent?: number | null;
};

/**
 * BIST satırı: API horizon boşsa sparkline’dan doldurur; spark varsa ABD ile aynı takvim penceresi mantığı.
 */
export function enrichBistInstrumentHorizons<T extends BistHorizonSource>(
    row: T,
    externalSpark?: readonly number[],
): T & { sparkline: number[]; pctDay: number | null; pctWeek: number | null; pctMonth: number | null; pctYear: number | null } {
    const apiSpark = (row.sparkline ?? []).map((x) => Number(x)).filter((x) => Number.isFinite(x) && x > 0);
    const ext = (externalSpark ?? []).map((x) => Number(x)).filter((x) => Number.isFinite(x) && x > 0);
    const spark = ext.length >= 2 ? ext : apiSpark;
    const dayBase =
        finitePct(row.pctDay) ?? finitePct(row.dailyChangePercent) ?? finitePct(row.changePercent) ?? 0;
    const hz = horizonPctsFromSparkCloses(spark, dayBase);
    if (spark.length < 2) {
        return {
            ...row,
            sparkline: apiSpark,
            pctDay: finitePct(row.pctDay) ?? dayBase,
            pctWeek: finitePct(row.pctWeek),
            pctMonth: finitePct(row.pctMonth),
            pctYear: finitePct(row.pctYear),
        };
    }
    return {
        ...row,
        sparkline: spark,
        pctDay: hz.pctDay ?? dayBase,
        pctWeek: hz.pctWeek,
        pctMonth: hz.pctMonth,
        pctYear: hz.pctYear,
    };
}
