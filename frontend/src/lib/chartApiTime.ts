import type { Time } from 'lightweight-charts';

/**
 * Marketdata `LocalDateTime` → JSON’da çoğunlukla offset yok ("2026-05-13T02:17:56").
 * Tarayıcı bunu “yerel” okur; JVM/Docker UTC ile yazılmış naif değerlerde eksen kayar.
 *
 * - Varsayılan: offset yoksa Türkiye duvar saati (+03) eklenir (`VITE_CHART_TIMESTAMP_ASSUME_TZ` ile kapatılabilir).
 * - İnce ayar: `VITE_CHART_EXTRA_OFFSET_MS` (örn. 14400000 = +4 saat) tüm intraday unix’e eklenir.
 *
 * Günlük mum (yalnızca tarih): `YYYY-MM-DD` olarak döner; günlük grafiklerde timezone kayması olmaz.
 */
function envAssumeTz(): string | null {
    const raw = import.meta.env.VITE_CHART_TIMESTAMP_ASSUME_TZ as string | undefined;
    if (raw === undefined || String(raw).trim() === '') {
        return '+03:00';
    }
    const t = String(raw).trim().toLowerCase();
    if (t === 'local' || t === '0' || t === 'false' || t === 'off') {
        return null;
    }
    return String(raw).trim();
}

function extraOffsetMs(): number {
    const n = Number(import.meta.env.VITE_CHART_EXTRA_OFFSET_MS);
    return Number.isFinite(n) ? n : 0;
}

/** ISO benzeri stringte saniye kesirini en fazla 3 haneye indir (Date ayrıştırması için). */
function trimFractionalSeconds(iso: string): string {
    return iso.replace(/(\.\d{3})\d+/, '$1');
}

const HAS_CLOCK = /T\d{2}:\d{2}/;

export type ChartTimeParseOptions = {
    /**
     * Günlük mum: anlık İstanbul’da 00:00:00 ise `YYYY-MM-DD` iş günü zamanı döner.
     * Aksi halde aynı an Unix saniye olarak verilir; eksen/crosshair UTC odaklı “bir önceki gün 21:00” gibi yanıltıcı etiketler oluşabiliyordu.
     */
    preferIstanbulBusinessDay?: boolean;
};

function isIstanbulWallMidnightUtcMs(ms: number): boolean {
    const parts = new Intl.DateTimeFormat('en-GB', {
        timeZone: 'Europe/Istanbul',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
    }).formatToParts(new Date(ms));
    const num = (t: Intl.DateTimeFormatPartTypes) => Number(parts.find((p) => p.type === t)?.value ?? NaN);
    return num('hour') === 0 && num('minute') === 0 && num('second') === 0;
}

function istanbulCalendarDayEnCa(ms: number): string {
    return new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Europe/Istanbul',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    }).format(new Date(ms));
}

/**
 * API’den gelen `t` / `time` alanını lightweight-charts `Time` tipine çevirir.
 */
export function apiDatetimeToChartTime(value: string, opts?: ChartTimeParseOptions): Time {
    const v = (value ?? '').trim();
    if (!v) {
        return '1970-01-01' as Time;
    }

    if (!HAS_CLOCK.test(v)) {
        const day = v.slice(0, 10);
        if (/^\d{4}-\d{2}-\d{2}$/.test(day)) {
            return day as Time;
        }
        const d = new Date(v);
        if (Number.isNaN(d.getTime())) {
            return day as Time;
        }
        return d.toISOString().slice(0, 10) as Time;
    }

    const assume = envAssumeTz();
    let parseInput = v;
    if (assume) {
        const hasZone = /[zZ]$|[+-]\d{2}:\d{2}$/.test(v);
        if (!hasZone) {
            parseInput = `${trimFractionalSeconds(v)}${assume}`;
        }
    }

    const baseMs = new Date(parseInput).getTime();
    const ms = baseMs + extraOffsetMs();
    if (Number.isNaN(ms)) {
        const fb = new Date(v).getTime() + extraOffsetMs();
        if (Number.isNaN(fb)) {
            return v.slice(0, 10) as Time;
        }
        return Math.floor(fb / 1000) as Time;
    }
    if (opts?.preferIstanbulBusinessDay && Number.isFinite(baseMs) && isIstanbulWallMidnightUtcMs(baseMs)) {
        return istanbulCalendarDayEnCa(baseMs) as Time;
    }
    return Math.floor(ms / 1000) as Time;
}
