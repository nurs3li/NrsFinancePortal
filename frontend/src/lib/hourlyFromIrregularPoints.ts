/**
 * Düzensiz snapshot serilerini (ör. tek fiyat noktası / saat), spot piyasadaki saatlik mum görünümüne yaklaştırmak için kullanılır.
 * - days &lt; 7 (ör. 1G): son 96 saatlik veriden saatlik OHLC üretir, ardından son 24 yerel saat dilimini taşıyarak doldurur.
 * - days ≥ 7 (ör. 1H): [şimdi − days gün, şimdi] aralığında her saat için OHLC; boş saatte son kapanış taşınır.
 *
 * Tahvil / VIOP gibi günde bir kez güncellenen veride bu fonksiyon neredeyse hep düz çizgi üretir; bu yüzden
 * Market terminalinde tahvil ve VIOP için kullanılmaz.
 *
 * Saat anahtarı: tarayıcı yerel saatine göre saat tabanı (dakika/saniye sıfırlanır).
 */

export type IrregularPoint = {
    asOf: string | null | undefined;
    close: number;
    volume?: number | null;
};

export type HourlyCandle = {
    time: string;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
};

const HOUR_MS = 60 * 60 * 1000;

function parseMs(asOf: string | null | undefined): number | null {
    if (asOf == null || String(asOf).trim() === '') return null;
    const ms = new Date(asOf).getTime();
    return Number.isFinite(ms) ? ms : null;
}

function localHourFloorMs(ms: number): number {
    const d = new Date(ms);
    d.setMinutes(0, 0, 0);
    d.setSeconds(0, 0);
    d.setMilliseconds(0);
    return d.getTime();
}

function toIso(ms: number): string {
    return new Date(ms).toISOString();
}

function aggregateBucket(sortedPts: { ms: number; close: number; vol: number }[]): HourlyCandle | null {
    if (!sortedPts.length) return null;
    const closes = sortedPts.map((p) => p.close);
    const o = sortedPts[0]!.close;
    const c = sortedPts[sortedPts.length - 1]!.close;
    const h = Math.max(o, c, ...closes);
    const l = Math.min(o, c, ...closes);
    const v = sortedPts.reduce((s, p) => s + p.vol, 0);
    const hk = localHourFloorMs(sortedPts[0]!.ms);
    return {
        time: toIso(hk),
        open: o,
        high: h,
        low: l,
        close: c,
        volume: v,
    };
}

/**
 * @param days RANGE_TO_DAYS (ör. 1D=5, 1W=7)
 */
export function resampleSnapshotsToHourlyCandles(rows: IrregularPoint[], days: number): HourlyCandle[] {
    const pts = rows
        .map((r) => {
            const ms = parseMs(r.asOf);
            if (ms == null) return null;
            const close = Number(r.close);
            if (!Number.isFinite(close) || close <= 0) return null;
            const vol = Number(r.volume ?? 0);
            return { ms, close, vol: Number.isFinite(vol) ? vol : 0 };
        })
        .filter((p): p is NonNullable<typeof p> => p != null)
        .sort((a, b) => a.ms - b.ms);

    if (pts.length < 2) return [];

    const now = Date.now();
    const endHour = localHourFloorMs(now);

    if (days < 7) {
        const queryStart = endHour - 96 * HOUR_MS;
        const inWin = pts.filter((p) => p.ms >= queryStart && p.ms <= now + 60_000);
        const src = inWin.length >= 2 ? inWin : pts;
        const byHour = new Map<number, typeof pts>();
        for (const p of src) {
            const hk = localHourFloorMs(p.ms);
            const arr = byHour.get(hk) ?? [];
            arr.push(p);
            byHour.set(hk, arr);
        }
        const sparse: HourlyCandle[] = [];
        [...byHour.keys()]
            .sort((a, b) => a - b)
            .forEach((hk) => {
                const bucket = (byHour.get(hk) ?? []).sort((a, b) => a.ms - b.ms);
                const cndl = aggregateBucket(bucket);
                if (cndl) sparse.push(cndl);
            });
        if (sparse.length < 2) return [];
        return densifyLast24Hours(sparse, endHour);
    }

    const rangeStart = localHourFloorMs(now - days * 24 * HOUR_MS);
    return hourlyStripWithCarry(pts, rangeStart, endHour);
}

function densifyLast24Hours(sparse: HourlyCandle[], endHourMs: number): HourlyCandle[] {
    const sorted = [...sparse].sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime());
    const byHour = new Map<number, HourlyCandle>();
    for (const c of sorted) {
        const hk = localHourFloorMs(new Date(c.time).getTime());
        byHour.set(hk, c);
    }
    let carry = sorted[0]!.close;
    const startHour = endHourMs - 23 * HOUR_MS;
    for (const c of sorted) {
        const hk = localHourFloorMs(new Date(c.time).getTime());
        if (hk < startHour) {
            carry = c.close;
        }
    }
    const out: HourlyCandle[] = [];
    for (let h = startHour; h <= endHourMs; h += HOUR_MS) {
        const hit = byHour.get(h);
        if (hit) {
            carry = hit.close;
            out.push(hit);
        } else {
            out.push({
                time: toIso(h),
                open: carry,
                high: carry,
                low: carry,
                close: carry,
                volume: 0,
            });
        }
    }
    return out;
}

function hourlyStripWithCarry(
    pts: { ms: number; close: number; vol: number }[],
    rangeStartMs: number,
    endHourMs: number
): HourlyCandle[] {
    const byHour = new Map<number, { ms: number; close: number; vol: number }[]>();
    for (const p of pts) {
        if (p.ms < rangeStartMs || p.ms > endHourMs + HOUR_MS) continue;
        const hk = localHourFloorMs(p.ms);
        const arr = byHour.get(hk) ?? [];
        arr.push(p);
        byHour.set(hk, arr);
    }
    let carry: number | null = null;
    for (const p of pts) {
        if (p.ms < rangeStartMs) {
            carry = p.close;
        } else {
            break;
        }
    }
    if (carry == null) carry = pts[0]!.close;

    const out: HourlyCandle[] = [];
    for (let h = rangeStartMs; h <= endHourMs; h += HOUR_MS) {
        const bucket = (byHour.get(h) ?? []).sort((a, b) => a.ms - b.ms);
        if (bucket.length) {
            const cndl = aggregateBucket(bucket);
            if (cndl) {
                carry = cndl.close;
                out.push(cndl);
            }
        } else if (carry != null) {
            out.push({
                time: toIso(h),
                open: carry,
                high: carry,
                low: carry,
                close: carry,
                volume: 0,
            });
        }
    }
    return out;
}
