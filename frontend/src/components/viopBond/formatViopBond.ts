export function fmtMoney(n: number | null | undefined, locale: string): string {
    if (n == null || !Number.isFinite(n)) {
        return '—';
    }
    return n.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function fmtPct(n: number | null | undefined, locale: string): string {
    if (n == null || !Number.isFinite(n)) {
        return '—';
    }
    return `${n.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
}

export function fmtDate(ymd: string | null | undefined, locale: string): string {
    if (!ymd) return '—';
    const d = new Date(`${ymd}T12:00:00`);
    if (Number.isNaN(d.getTime())) return ymd;
    return d.toLocaleDateString(locale);
}

/** Para / fiyat alanları için locale formatı (örn. tr-TR: 20.552,00) */
export function fmtLocaleDecimal(n: number | null | undefined, locale: string, fractionDigits = 2): string {
    if (n == null || !Number.isFinite(n)) return '';
    return n.toLocaleString(locale, {
        minimumFractionDigits: fractionDigits,
        maximumFractionDigits: fractionDigits,
    });
}

/** Kullanıcı girişinden sayıya (20.552,00 veya 20552) */
export function parseLocaleDecimal(raw: string, locale: string): number | null {
    const trimmed = raw.trim();
    if (!trimmed) return null;
    if (locale.startsWith('tr')) {
        const normalized = trimmed.replace(/\s/g, '').replace(/\./g, '').replace(',', '.');
        const n = Number(normalized);
        return Number.isFinite(n) ? n : null;
    }
    const normalized = trimmed.replace(/,/g, '');
    const n = Number(normalized);
    return Number.isFinite(n) ? n : null;
}
