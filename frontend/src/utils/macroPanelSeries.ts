import type { NormalizedMacroSeries } from '../services/marketDataService';

/** Dokümantasyon / UI anahtarı → backend {@code logicalKey} (LoanRateCatalog). */
const LOAN_UI_TO_BACKEND: Record<string, string> = {
    LOAN_RATE_CONSUMER_WEEKLY: 'LOAN_RATE_CONSUMER_TRY_WEEKLY',
    LOAN_RATE_VEHICLE_WEEKLY: 'LOAN_RATE_VEHICLE_TRY_WEEKLY',
    LOAN_RATE_HOUSING_WEEKLY: 'LOAN_RATE_HOUSING_TRY_WEEKLY',
    LOAN_RATE_COMMERCIAL_WEEKLY: 'LOAN_RATE_COMMERCIAL_TRY_WEEKLY',
};

export const LOAN_CHART_LOGICAL_KEYS = [
    'LOAN_RATE_CONSUMER_WEEKLY',
    'LOAN_RATE_VEHICLE_WEEKLY',
    'LOAN_RATE_HOUSING_WEEKLY',
    'LOAN_RATE_COMMERCIAL_WEEKLY',
] as const;

export const DEPOSIT_TRY_CHART_LOGICAL_KEYS = [
    'DEPOSIT_RATE_TRY_1M_WEEKLY',
    'DEPOSIT_RATE_TRY_3M_WEEKLY',
    'DEPOSIT_RATE_TRY_6M_WEEKLY',
    'DEPOSIT_RATE_TRY_1Y_WEEKLY',
    'DEPOSIT_RATE_TRY_GT1Y_WEEKLY',
] as const;

/** logicalKey yoksa EVDS seri kodu ile eşleştirme (USD/EUR mevduat akım). */
export const DEPOSIT_FX_LOGICAL_TO_EVDS_CODE: Record<string, string> = {
    DEPOSIT_RATE_USD_1M_WEEKLY: 'TP_USD_MT01',
    DEPOSIT_RATE_USD_3M_WEEKLY: 'TP_USD_MT02',
    DEPOSIT_RATE_USD_6M_WEEKLY: 'TP_USD_MT03',
    DEPOSIT_RATE_USD_1Y_WEEKLY: 'TP_USD_MT04',
    DEPOSIT_RATE_EUR_1M_WEEKLY: 'TP_EUR_MT01',
    DEPOSIT_RATE_EUR_3M_WEEKLY: 'TP_EUR_MT02',
    DEPOSIT_RATE_EUR_6M_WEEKLY: 'TP_EUR_MT03',
    DEPOSIT_RATE_EUR_1Y_WEEKLY: 'TP_EUR_MT04',
};

export const DEPOSIT_USD_CHART_LOGICAL_KEYS = [
    'DEPOSIT_RATE_USD_1M_WEEKLY',
    'DEPOSIT_RATE_USD_3M_WEEKLY',
    'DEPOSIT_RATE_USD_6M_WEEKLY',
    'DEPOSIT_RATE_USD_1Y_WEEKLY',
] as const;

export const DEPOSIT_EUR_CHART_LOGICAL_KEYS = [
    'DEPOSIT_RATE_EUR_1M_WEEKLY',
    'DEPOSIT_RATE_EUR_3M_WEEKLY',
    'DEPOSIT_RATE_EUR_6M_WEEKLY',
    'DEPOSIT_RATE_EUR_1Y_WEEKLY',
] as const;

export function selectMacroSeries(
    series: NormalizedMacroSeries[] | null | undefined,
    uiKey: string,
): NormalizedMacroSeries | undefined {
    if (!series?.length) return undefined;
    const backend = LOAN_UI_TO_BACKEND[uiKey] ?? uiKey;
    const byLogical =
        series.find((s) => s.logicalKey === uiKey) ?? series.find((s) => s.logicalKey === backend);
    if (byLogical) return byLogical;
    const evds = DEPOSIT_FX_LOGICAL_TO_EVDS_CODE[uiKey] ?? DEPOSIT_FX_LOGICAL_TO_EVDS_CODE[backend];
    if (evds) {
        const u = evds.trim().toUpperCase();
        return series.find((s) => String(s.code ?? '').trim().toUpperCase() === u);
    }
    return undefined;
}

export function lastObservation(
    s: NormalizedMacroSeries | undefined,
): { date: string; value: number } | null {
    if (!s?.observations?.length) return null;
    const last = s.observations[s.observations.length - 1];
    if (!last || !Number.isFinite(Number(last.value))) return null;
    return { date: String(last.date), value: Number(last.value) };
}

/** EVDS mantıksal anahtar → çoklu grafik kolonu (mevcut kredi grafiği dataKey’leri ile uyumlu). */
const LOAN_LOGICAL_TO_CHART_COL: Record<string, string> = {
    LOAN_RATE_CONSUMER_TRY_WEEKLY: 'CONSUMER_TRY',
    LOAN_RATE_CONSUMER_WEEKLY: 'CONSUMER_TRY',
    LOAN_RATE_VEHICLE_TRY_WEEKLY: 'VEHICLE_TRY',
    LOAN_RATE_VEHICLE_WEEKLY: 'VEHICLE_TRY',
    LOAN_RATE_HOUSING_TRY_WEEKLY: 'HOUSING_TRY',
    LOAN_RATE_HOUSING_WEEKLY: 'HOUSING_TRY',
    LOAN_RATE_COMMERCIAL_TRY_WEEKLY: 'COMMERCIAL_TRY',
    LOAN_RATE_COMMERCIAL_WEEKLY: 'COMMERCIAL_TRY',
};

/** Kredi faizleri çoklu seri — tarih anahtarı `date` (yyyy-MM-dd). */
export function mergeCreditSeriesChart(
    series: NormalizedMacroSeries[] | null | undefined,
    keys: readonly string[],
): Record<string, string | number>[] {
    if (!series?.length) return [];
    const byDate = new Map<string, Record<string, string | number>>();
    for (const uiKey of keys) {
        const s = selectMacroSeries(series, uiKey);
        const lk = String(s?.logicalKey ?? LOAN_UI_TO_BACKEND[uiKey] ?? uiKey);
        const col = LOAN_LOGICAL_TO_CHART_COL[lk] ?? 'UNKNOWN';
        for (const o of s?.observations ?? []) {
            const d = String(o.date ?? '').slice(0, 10);
            if (!d) continue;
            const row = byDate.get(d) ?? { date: d };
            if (Number.isFinite(Number(o.value))) {
                row[col] = Number(o.value);
            }
            byDate.set(d, row);
        }
    }
    return Array.from(byDate.values()).sort((a, b) => String(a.date).localeCompare(String(b.date)));
}

/** TL mevduat — kolon anahtarı vade (1M, 3M, …). */
export function mergeDepositTryChart(
    series: NormalizedMacroSeries[] | null | undefined,
    keys: readonly string[],
): Record<string, string | number>[] {
    if (!series?.length) return [];
    const termByLogical: Record<string, string> = {
        DEPOSIT_RATE_TRY_1M_WEEKLY: 'TRY_1M',
        DEPOSIT_RATE_TRY_3M_WEEKLY: 'TRY_3M',
        DEPOSIT_RATE_TRY_6M_WEEKLY: 'TRY_6M',
        DEPOSIT_RATE_TRY_1Y_WEEKLY: 'TRY_1Y',
        DEPOSIT_RATE_TRY_GT1Y_WEEKLY: 'TRY_GT1Y',
    };
    const byDate = new Map<string, Record<string, string | number>>();
    for (const lk of keys) {
        const s = selectMacroSeries(series, lk);
        const col = termByLogical[lk] ?? lk;
        for (const o of s?.observations ?? []) {
            const d = String(o.date ?? '').slice(0, 10);
            if (!d) continue;
            const row = byDate.get(d) ?? { date: d };
            if (Number.isFinite(Number(o.value))) {
                row[col] = Number(o.value);
            }
            byDate.set(d, row);
        }
    }
    return Array.from(byDate.values()).sort((a, b) => String(a.date).localeCompare(String(b.date)));
}

/** USD veya EUR mevduat — kolon anahtarları USD_1M, EUR_3M vb. */
export function mergeDepositFxWeeklyChart(
    series: NormalizedMacroSeries[] | null | undefined,
    keys: readonly string[],
    columnByLogical: Record<string, string>,
): Record<string, string | number>[] {
    if (!series?.length) return [];
    const byDate = new Map<string, Record<string, string | number>>();
    for (const lk of keys) {
        const s = selectMacroSeries(series, lk);
        const col = columnByLogical[lk] ?? lk;
        for (const o of s?.observations ?? []) {
            const d = String(o.date ?? '').slice(0, 10);
            if (!d) continue;
            const row = byDate.get(d) ?? { date: d };
            if (Number.isFinite(Number(o.value))) {
                row[col] = Number(o.value);
            }
            byDate.set(d, row);
        }
    }
    return Array.from(byDate.values()).sort((a, b) => String(a.date).localeCompare(String(b.date)));
}

const USD_DEPOSIT_COL: Record<string, string> = {
    DEPOSIT_RATE_USD_1M_WEEKLY: 'USD_1M',
    DEPOSIT_RATE_USD_3M_WEEKLY: 'USD_3M',
    DEPOSIT_RATE_USD_6M_WEEKLY: 'USD_6M',
    DEPOSIT_RATE_USD_1Y_WEEKLY: 'USD_1Y',
};

const EUR_DEPOSIT_COL: Record<string, string> = {
    DEPOSIT_RATE_EUR_1M_WEEKLY: 'EUR_1M',
    DEPOSIT_RATE_EUR_3M_WEEKLY: 'EUR_3M',
    DEPOSIT_RATE_EUR_6M_WEEKLY: 'EUR_6M',
    DEPOSIT_RATE_EUR_1Y_WEEKLY: 'EUR_1Y',
};

export function mergeUsdDepositWeeklyChart(
    series: NormalizedMacroSeries[] | null | undefined,
): Record<string, string | number>[] {
    return mergeDepositFxWeeklyChart(series, [...DEPOSIT_USD_CHART_LOGICAL_KEYS], USD_DEPOSIT_COL);
}

export function mergeEurDepositWeeklyChart(
    series: NormalizedMacroSeries[] | null | undefined,
): Record<string, string | number>[] {
    return mergeDepositFxWeeklyChart(series, [...DEPOSIT_EUR_CHART_LOGICAL_KEYS], EUR_DEPOSIT_COL);
}

/** FX mevduat serilerinden en az birinde geçerli gözlem var mı */
export function hasFxDepositPanelData(series: NormalizedMacroSeries[] | null | undefined): boolean {
    const keys = [...DEPOSIT_USD_CHART_LOGICAL_KEYS, ...DEPOSIT_EUR_CHART_LOGICAL_KEYS] as string[];
    for (const k of keys) {
        if (lastObservation(selectMacroSeries(series, k))) return true;
    }
    return false;
}

/** Haftalık ihtiyaç kredisi % − TL 1M mevduat % (aynı gözlem tarihi). */
export function panelSpreadConsumerMinusDepositTry1m(
    series: NormalizedMacroSeries[] | null | undefined,
): { date: string; spread: number }[] {
    const cons = selectMacroSeries(series, 'LOAN_RATE_CONSUMER_WEEKLY');
    const dep = selectMacroSeries(series, 'DEPOSIT_RATE_TRY_1M_WEEKLY');
    const depByDate = new Map<string, number>();
    for (const o of dep?.observations ?? []) {
        const d = String(o.date ?? '').slice(0, 10);
        if (d && Number.isFinite(Number(o.value))) depByDate.set(d, Number(o.value));
    }
    const out: { date: string; spread: number }[] = [];
    for (const o of cons?.observations ?? []) {
        const d = String(o.date ?? '').slice(0, 10);
        if (!d) continue;
        const dv = depByDate.get(d);
        const cv = Number(o.value);
        if (dv == null || !Number.isFinite(cv) || !Number.isFinite(dv)) continue;
        out.push({ date: d, spread: cv - dv });
    }
    return out.sort((a, b) => a.date.localeCompare(b.date));
}

/** TÜFE / Yİ-ÜFE endeks seviyesi (oran değil). */
export function mergeIndexLevelChart(
    series: NormalizedMacroSeries[] | null | undefined,
): { period: string; cpi?: number; ppi?: number }[] {
    const cpi = selectMacroSeries(series, 'CPI_TR_INDEX');
    const ppi = selectMacroSeries(series, 'PPI_TR_INDEX');
    const byMonth = new Map<string, { period: string; cpi?: number; ppi?: number }>();
    for (const o of cpi?.observations ?? []) {
        const k = String(o.date ?? '').slice(0, 7);
        if (!k) continue;
        const row = byMonth.get(k) ?? { period: k };
        if (Number.isFinite(Number(o.value))) row.cpi = Number(o.value);
        byMonth.set(k, row);
    }
    for (const o of ppi?.observations ?? []) {
        const k = String(o.date ?? '').slice(0, 7);
        if (!k) continue;
        const row = byMonth.get(k) ?? { period: k };
        if (Number.isFinite(Number(o.value))) row.ppi = Number(o.value);
        byMonth.set(k, row);
    }
    return Array.from(byMonth.values()).sort((a, b) => a.period.localeCompare(b.period));
}

export function formatLocaleDate(isoDate: string | undefined, locale: string): string {
    if (!isoDate) return '—';
    const d = new Date(`${isoDate.slice(0, 10)}T12:00:00`);
    if (Number.isNaN(d.getTime())) return isoDate;
    return d.toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatPercent2(v: number | null | undefined, locale = 'tr-TR'): string {
    if (v == null || !Number.isFinite(Number(v))) return '—';
    const n = Number(v);
    const sign = n > 0 ? '+' : '';
    return `${sign}${n.toLocaleString(locale, { maximumFractionDigits: 2 })}%`;
}

export function formatIndex2(v: number | null | undefined, locale = 'tr-TR'): string {
    if (v == null || !Number.isFinite(Number(v))) return '—';
    return Number(v).toLocaleString(locale, { maximumFractionDigits: 2 });
}
