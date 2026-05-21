export function fmtTry(v: number | null | undefined, locale: string): string {
    if (v == null || !Number.isFinite(v)) return '—';
    return new Intl.NumberFormat(locale, { style: 'currency', currency: 'TRY', maximumFractionDigits: 0 }).format(v);
}

export function fmtPct(v: number | null | undefined, locale: string): string {
    if (v == null || !Number.isFinite(v)) return '—';
    return `${new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(v)}%`;
}

export function fmtDate(iso: string | null | undefined, locale: string): string {
    if (!iso) return '—';
    try {
        return new Date(iso).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' });
    } catch {
        return iso;
    }
}
