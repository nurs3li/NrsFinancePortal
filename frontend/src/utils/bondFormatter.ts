function toFullYear(twoDigitYear: number): number {
    return 2000 + twoDigitYear;
}

export function extractMaturityDate(isin: string): Date | null {
    const s = String(isin ?? '').trim().toUpperCase();
    if (!s) return null;
    // Common TR bond style: TRD150328T32 -> dd=15 mm=03 yy=28
    const m = s.match(/^TR[A-Z0-9](\d{2})(\d{2})(\d{2})[A-Z0-9]*$/);
    if (!m) return null;
    const dd = Number(m[1]);
    const mm = Number(m[2]);
    const yy = Number(m[3]);
    if (!Number.isInteger(dd) || !Number.isInteger(mm) || !Number.isInteger(yy)) return null;
    if (dd < 1 || dd > 31 || mm < 1 || mm > 12) return null;
    const yyyy = toFullYear(yy);
    const d = new Date(Date.UTC(yyyy, mm - 1, dd));
    if (
        d.getUTCFullYear() !== yyyy ||
        d.getUTCMonth() !== mm - 1 ||
        d.getUTCDate() !== dd
    ) {
        return null;
    }
    return d;
}

export function formatBondDisplayName(isin: string): string {
    const d = extractMaturityDate(isin);
    if (!d) return String(isin ?? '').trim() || '—';
    const formatted = d.toLocaleDateString('tr-TR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        timeZone: 'UTC',
    });
    return `Devlet Tahvili ${formatted}`;
}

export function getRemainingDays(isin: string): number {
    const d = extractMaturityDate(isin);
    if (!d) return NaN;
    const today = new Date();
    const startOfTodayUtc = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
    const targetUtc = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
    return Math.ceil((targetUtc - startOfTodayUtc) / 86400000);
}

