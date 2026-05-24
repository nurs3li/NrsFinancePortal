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

/** Kaldıraç: 25,75x */
export function fmtLeverageX(n: number | null | undefined, locale: string): string {
    if (n == null || !Number.isFinite(n)) return '—';
    return `${n.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}x`;
}

/** Oran (0–1) → yüzde: 0,0388 → %3,88 */
export function fmtRatioPercent(ratio: number | null | undefined, locale: string): string {
    if (ratio == null || !Number.isFinite(ratio)) return '—';
    return fmtPct(ratio * 100, locale);
}

export function fmtNativeAmount(
    n: number | null | undefined,
    currency: string,
    locale: string,
): string {
    if (n == null || !Number.isFinite(n)) return '—';
    const v = n.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 4 });
    return currency === 'USD' ? `$ ${v}` : currency === 'EUR' ? `€ ${v}` : `₺ ${v}`;
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
