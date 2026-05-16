export type DateValue = { date: string; value: number };

export type PurchasingPowerPoint = {
    date: string;
    /** 1 lot / birim varlığın o günkü TRY piyasa değeri */
    assetTry: number;
    /** Başlangıç tutarının güncel satın alma gücü (enflasyon erimesi) */
    inflationRealTry: number;
    /** Enflasyonda aynı gücü korumak için gereken nominal TRY */
    inflationHurdleTry: number;
    /** Başlangıç tutarının TL 1A mevduatta günlük bileşik getirisi */
    depositTry: number;
};

export type PurchasingPowerSnapshot = {
    anchorDate: string;
    lotCostTry: number;
    assetTryToday: number;
    depositTryToday: number;
    inflationBreakEvenToday: number;
    inflationErosionTry: number;
    assetVsDepositDiff: number;
    assetVsInflationHurdleDiff: number;
};

const MS_PER_DAY = 24 * 3600 * 1000;

/** En yakın önceki veya eşit tarihli gözlem (yyyy-MM-dd). */
export function lastValueAtOrBefore(obs: DateValue[], dateYmd: string): number | null {
    let best: number | null = null;
    let bestKey = '';
    for (const o of obs) {
        const d = String(o.date).slice(0, 10);
        if (!d || d > dateYmd) continue;
        if (d >= bestKey) {
            bestKey = d;
            best = o.value;
        }
    }
    return best != null && Number.isFinite(best) && best > 0 ? best : null;
}

export function addDaysYmd(ymd: string, days: number): string {
    const d = new Date(`${ymd.slice(0, 10)}T12:00:00`);
    d.setDate(d.getDate() + days);
    return d.toISOString().slice(0, 10);
}

/** İki tarih arasındaki takvim günleri [from, to] dahil. */
export function enumerateDaysInclusive(fromYmd: string, toYmd: string): string[] {
    const from = fromYmd.slice(0, 10);
    const to = toYmd.slice(0, 10);
    if (!from || !to || to < from) return [];
    const out: string[] = [];
    let cur = from;
    while (cur <= to) {
        out.push(cur);
        if (cur === to) break;
        cur = addDaysYmd(cur, 1);
    }
    return out;
}

function daysBetween(fromYmd: string, toYmd: string): number {
    const a = new Date(`${fromYmd.slice(0, 10)}T12:00:00`).getTime();
    const b = new Date(`${toYmd.slice(0, 10)}T12:00:00`).getTime();
    if (!Number.isFinite(a) || !Number.isFinite(b)) return 0;
    return Math.max(0, Math.round((b - a) / MS_PER_DAY));
}

function sortPositiveIndexObs(obs: DateValue[]): { date: string; value: number }[] {
    return [...obs]
        .map((o) => ({ date: String(o.date).slice(0, 10), value: o.value }))
        .filter((o) => o.date && Number.isFinite(o.value) && o.value > 0)
        .sort((a, b) => a.date.localeCompare(b.date));
}

/** İki endeks gözlemi arasında günlük bileşik çarpan: I_start × mult^k. */
function fillCpiSegmentDaily(
    map: Map<string, number>,
    startDate: string,
    startIdx: number,
    endDate: string,
    endIdx: number,
) {
    const span = daysBetween(startDate, endDate);
    map.set(startDate, startIdx);
    if (span <= 0) return;
    const dailyMult = Math.pow(endIdx / startIdx, 1 / span);
    let idx = startIdx;
    for (let k = 1; k <= span; k++) {
        idx *= dailyMult;
        map.set(addDaysYmd(startDate, k), idx);
    }
}

/**
 * Aylık TÜFE endeks serisini günlük bileşik yola çevirir.
 * Ay gözlemleri arasında: günlük çarpan = (I_son / I_ilk)^(1/gün).
 * Grafik aralığı gözlemlerden önce/sonra ise aynı aylık oranla dışa uzatılır.
 */
export function buildDailyCpiIndexMap(
    obs: DateValue[],
    rangeFrom: string,
    rangeTo: string,
): Map<string, number> {
    const sorted = sortPositiveIndexObs(obs);
    const map = new Map<string, number>();
    const from = rangeFrom.slice(0, 10);
    const to = rangeTo.slice(0, 10);
    if (!sorted.length || !from || !to || to < from) return map;

    if (sorted.length === 1) {
        for (const d of enumerateDaysInclusive(from, to)) {
            map.set(d, sorted[0]!.value);
        }
        return map;
    }

    const first = sorted[0]!;
    const second = sorted[1]!;
    const beforeSpan = daysBetween(from, first.date);
    if (from < first.date && beforeSpan > 0) {
        const dailyMult = Math.pow(second.value / first.value, 1 / Math.max(1, daysBetween(first.date, second.date)));
        let idx = first.value;
        map.set(first.date, idx);
        for (let k = 1; k <= beforeSpan; k++) {
            idx /= dailyMult;
            map.set(addDaysYmd(first.date, -k), idx);
        }
    }

    for (let i = 0; i < sorted.length - 1; i++) {
        fillCpiSegmentDaily(map, sorted[i]!.date, sorted[i]!.value, sorted[i + 1]!.date, sorted[i + 1]!.value);
    }

    const last = sorted[sorted.length - 1]!;
    const prev = sorted[sorted.length - 2]!;
    if (to > last.date) {
        const span = daysBetween(prev.date, last.date) || 1;
        const dailyMult = Math.pow(last.value / prev.value, 1 / span);
        let idx = last.value;
        let d = last.date;
        while (d < to) {
            d = addDaysYmd(d, 1);
            idx *= dailyMult;
            map.set(d, idx);
        }
    }

    for (const d of enumerateDaysInclusive(from, to)) {
        if (!map.has(d)) {
            const fallback = lastValueAtOrBefore(obs, d);
            if (fallback != null) map.set(d, fallback);
        }
    }

    return map;
}

export function dailyCpiIndexAt(map: Map<string, number>, dateYmd: string): number | null {
    const d = dateYmd.slice(0, 10);
    const v = map.get(d);
    return v != null && Number.isFinite(v) && v > 0 ? v : null;
}

/**
 * Yıllık % mevduat faizi — her takvim günü için günlük bileşik: (1 + r/36500).
 * Haftalık akım serisindeki oran, o haftaya kadar geçerli yıllık gösterge olarak kullanılır.
 */
export function compoundDepositTryDaily(
    anchor: string,
    dateYmd: string,
    amount0: number,
    annualRatePctSeries: DateValue[],
): number {
    if (!(amount0 > 0)) return amount0;
    const end = dateYmd.slice(0, 10);
    const start = anchor.slice(0, 10);
    if (end <= start) return amount0;

    let value = amount0;
    const days = enumerateDaysInclusive(addDaysYmd(start, 1), end);
    for (const d of days) {
        const annualPct = lastValueAtOrBefore(annualRatePctSeries, d);
        if (annualPct == null || !Number.isFinite(annualPct)) continue;
        value *= 1 + annualPct / 100 / 365;
    }
    return value;
}

function sortedUnionDates(...lists: DateValue[][]): string[] {
    const set = new Set<string>();
    for (const list of lists) {
        for (const o of list) {
            const d = String(o.date).slice(0, 10);
            if (d) set.add(d);
        }
    }
    return [...set].sort((a, b) => a.localeCompare(b));
}

export type BuildPurchasingPowerParams = {
    anchorDate: string;
    lotCostTry: number;
    assetUnitPriceByDate: DateValue[];
    assetInTry: boolean;
    usdTryByDate: DateValue[];
    cpiIndexByDate: DateValue[];
    /** Yıllık % — haftalık EVDS akım */
    depositRatePctAnnual: DateValue[];
};

/** Mum/kur serisini günlük takvimde ileri doldurulmuş birim TRY haritasına çevirir. */
function buildForwardFilledUnitTryMap(
    assetUnit: DateValue[],
    usdTry: DateValue[],
    assetInTry: boolean,
    fromYmd: string,
    toYmd: string,
): Map<string, number> {
    const map = new Map<string, number>();
    const unitSorted = sortPositiveIndexObs(assetUnit);
    const fxSorted = sortPositiveIndexObs(usdTry);

    let ui = 0;
    let fi = 0;
    let lastU: number | null = null;
    let lastR: number | null = null;

    for (const d of enumerateDaysInclusive(fromYmd, toYmd)) {
        while (ui < unitSorted.length && unitSorted[ui]!.date <= d) {
            lastU = unitSorted[ui]!.value;
            ui++;
        }
        if (!assetInTry) {
            while (fi < fxSorted.length && fxSorted[fi]!.date <= d) {
                lastR = fxSorted[fi]!.value;
                fi++;
            }
        }
        const unitTry = assetInTry ? lastU : lastU != null && lastR != null ? lastU * lastR : null;
        if (unitTry != null && unitTry > 0) map.set(d, unitTry);
    }
    return map;
}

export const PP_CHART_MAX_POINTS = 180;

export function firstYmdFromCandles(candles: { time: string }[]): string {
    if (!candles.length) return '';
    const sorted = [...candles].sort((a, b) => String(a.time).localeCompare(String(b.time)));
    return String(sorted[0]?.time ?? '').slice(0, 10);
}

export function lastYmdFromCandles(candles: { time: string }[]): string {
    if (!candles.length) return '';
    const sorted = [...candles].sort((a, b) => String(a.time).localeCompare(String(b.time)));
    return String(sorted[sorted.length - 1]?.time ?? '').slice(0, 10);
}

/** Grafik penceresi içinde geçerli referans tarihi (yyyy-MM-dd). */
export function clampPpAnchorYmd(ymd: string, first: string, last: string, today: string): string {
    const d = ymd.slice(0, 10);
    if (!d) return first || today;
    const hi = [last, today].filter(Boolean).sort((a, b) => b.localeCompare(a))[0] ?? today;
    const lo = first || d;
    if (d > hi) return hi;
    if (d < lo) return lo;
    return d;
}

/** Satın alma gücü: referans tarihten bugüne kadar fiyat/kur geçmişi (grafik aralığından bağımsız). */
export function computePurchasingPowerHistoryDays(
    chartRangeDays: number,
    anchorDate: string,
    todayYmd: string,
): number {
    const anchor = anchorDate.slice(0, 10);
    const today = todayYmd.slice(0, 10);
    if (!anchor || anchor > today) return chartRangeDays;
    const span = daysBetween(anchor, today) + 14;
    return Math.min(3650, Math.max(chartRangeDays, span));
}

export function buildPurchasingPowerSeries(params: BuildPurchasingPowerParams): PurchasingPowerPoint[] {
    const anchor = params.anchorDate.slice(0, 10);
    const lotCost = params.lotCostTry;
    if (!anchor || !(lotCost > 0)) return [];

    const sparseEnd = sortedUnionDates(
        params.assetUnitPriceByDate,
        params.usdTryByDate,
        params.cpiIndexByDate,
        params.depositRatePctAnnual,
    ).filter((d) => d >= anchor);

    const endDate = sparseEnd.length ? sparseEnd[sparseEnd.length - 1]! : anchor;
    const cpiDaily = buildDailyCpiIndexMap(params.cpiIndexByDate, anchor, endDate);
    const anchorCpi = dailyCpiIndexAt(cpiDaily, anchor);
    if (anchorCpi == null) return [];

    const unitTryByDay = buildForwardFilledUnitTryMap(
        params.assetUnitPriceByDate,
        params.usdTryByDate,
        params.assetInTry,
        anchor,
        endDate,
    );
    const anchorUnitTry = unitTryByDay.get(anchor) ?? null;
    if (anchorUnitTry == null || anchorUnitTry <= 0) return [];

    const dates = enumerateDaysInclusive(anchor, endDate);
    const out: PurchasingPowerPoint[] = [];
    let depositTry = lotCost;

    for (const d of dates) {
        const cpi = dailyCpiIndexAt(cpiDaily, d);
        const unitTry = unitTryByDay.get(d);
        if (cpi == null || unitTry == null) continue;

        if (d > anchor) {
            const annualPct = lastValueAtOrBefore(params.depositRatePctAnnual, d);
            if (annualPct != null && Number.isFinite(annualPct)) {
                depositTry *= 1 + annualPct / 100 / 365;
            }
        }

        const assetTryNow = lotCost * (unitTry / anchorUnitTry);
        const inflationHurdleTry = lotCost * (cpi / anchorCpi);
        const inflationRealTry = lotCost * (anchorCpi / cpi);

        out.push({
            date: d,
            assetTry: assetTryNow,
            inflationRealTry,
            inflationHurdleTry,
            depositTry,
        });
    }

    return out;
}

export function computePurchasingPowerSnapshot(
    series: PurchasingPowerPoint[],
    lotCostTry: number,
    anchorDate: string,
): PurchasingPowerSnapshot | null {
    if (!series.length || !(lotCostTry > 0)) return null;
    const last = series[series.length - 1]!;
    const inflationBreakEvenToday = last.inflationHurdleTry;
    const inflationErosionTry = Math.max(0, lotCostTry - last.inflationRealTry);

    return {
        anchorDate,
        lotCostTry,
        assetTryToday: last.assetTry,
        depositTryToday: last.depositTry,
        inflationBreakEvenToday,
        inflationErosionTry,
        assetVsDepositDiff: last.assetTry - last.depositTry,
        assetVsInflationHurdleDiff: last.assetTry - last.inflationHurdleTry,
    };
}

/** 1 lot maliyeti: seçilen tarihte birim fiyat (TRY). */
export function lotCostTryAtAnchor(params: {
    anchorDate: string;
    assetUnitPriceByDate: DateValue[];
    assetInTry: boolean;
    usdTryByDate: DateValue[];
}): number | null {
    const anchor = params.anchorDate.slice(0, 10);
    if (params.assetInTry) {
        return lastValueAtOrBefore(params.assetUnitPriceByDate, anchor);
    }
    const u = lastValueAtOrBefore(params.assetUnitPriceByDate, anchor);
    const r = lastValueAtOrBefore(params.usdTryByDate, anchor);
    if (u == null || r == null) return null;
    return u * r;
}
