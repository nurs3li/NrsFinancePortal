import { viopCategoryFor } from '../../constants/ViopWhitelist';

export type ViopContractSuffix = {
    underlying: string;
    month: number;
    year: number;
};

const MONTH_LONG_TR = [
    'Ocak',
    'Şubat',
    'Mart',
    'Nisan',
    'Mayıs',
    'Haziran',
    'Temmuz',
    'Ağustos',
    'Eylül',
    'Ekim',
    'Kasım',
    'Aralık',
] as const;

const MONTH_LONG_EN = [
    'January',
    'February',
    'March',
    'April',
    'May',
    'June',
    'July',
    'August',
    'September',
    'October',
    'November',
    'December',
] as const;

/** XU0301226 → { underlying: XU030, month: 12, year: 2026 } */
export function parseViopContractSuffix(symbol: string): ViopContractSuffix | null {
    const s = symbol.trim().toUpperCase().replace(/^F_/, '');
    const m = s.match(/^([A-Z0-9]+?)(\d{2})(\d{2})$/);
    if (!m) return null;
    const month = Number(m[2]);
    const year = 2000 + Number(m[3]);
    if (month < 1 || month > 12) return null;
    return { underlying: m[1], month, year };
}

export function expiryIsoFromSuffix(suffix: ViopContractSuffix): string {
    const lastDay = new Date(suffix.year, suffix.month, 0).getDate();
    return `${suffix.year}-${String(suffix.month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;
}

export type ViopExpiryDisplay = {
    expiryDate: string;
    displayLong: string;
    displayShort: string;
};

export function resolveViopExpiry(
    symbol: string,
    locale: string,
    options?: { contractMonth?: string; expiryDate?: string | null },
): ViopExpiryDisplay | null {
    const iso = options?.expiryDate?.trim();
    if (iso && /^\d{4}-\d{2}-\d{2}$/.test(iso)) {
        const [y, mo] = iso.split('-').map(Number);
        return {
            expiryDate: iso,
            displayLong: formatMonthYearLong(mo, y, locale),
            displayShort: `${String(mo).padStart(2, '0')}/${y}`,
        };
    }

    const suffix = parseViopContractSuffix(symbol);
    if (suffix) {
        return {
            expiryDate: expiryIsoFromSuffix(suffix),
            displayLong: formatMonthYearLong(suffix.month, suffix.year, locale),
            displayShort: `${String(suffix.month).padStart(2, '0')}/${suffix.year}`,
        };
    }

    const cm = options?.contractMonth?.trim();
    if (cm) {
        const m1 = cm.match(/^(\d{4})-(\d{2})/);
        if (m1) {
            const y = Number(m1[1]);
            const mo = Number(m1[2]);
            if (mo >= 1 && mo <= 12) {
                const expiryDate = expiryIsoFromSuffix({ underlying: '', month: mo, year: y });
                return {
                    expiryDate,
                    displayLong: formatMonthYearLong(mo, y, locale),
                    displayShort: `${String(mo).padStart(2, '0')}/${y}`,
                };
            }
        }
        const m2 = cm.match(/(\d{1,2})\s*\/\s*(\d{4})/);
        if (m2) {
            const mo = Number(m2[1]);
            const y = Number(m2[2]);
            if (mo >= 1 && mo <= 12) {
                const expiryDate = expiryIsoFromSuffix({ underlying: '', month: mo, year: y });
                return {
                    expiryDate,
                    displayLong: formatMonthYearLong(mo, y, locale),
                    displayShort: `${String(mo).padStart(2, '0')}/${y}`,
                };
            }
        }
    }

    return null;
}

function formatMonthYearLong(month: number, year: number, locale: string): string {
    if (month < 1 || month > 12) return `${month}/${year}`;
    const names = locale.startsWith('tr') ? MONTH_LONG_TR : MONTH_LONG_EN;
    return `${names[month - 1]} ${year}`;
}

/** Dayanak: XU030 / BIST 30, EREGL Pay Vadeli, USD/TRY */
export function formatViopUnderlyingDisplay(symbol: string): string {
    const suffix = parseViopContractSuffix(symbol);
    const raw = suffix?.underlying ?? symbol.trim().toUpperCase().replace(/^F_/, '');
    const cat = viopCategoryFor(symbol);

    if (raw === 'XU030' || raw === 'XU') return 'XU030 / BIST 30';
    if (raw === 'XLBNK') return 'XLBNK / Banka Endeksi';
    if (raw === 'USDTRY') return 'USD/TRY';
    if (raw === 'EURTRY') return 'EUR/TRY';
    if (raw === 'XAUTRYM' || raw === 'XAUUSD' || raw === 'ALTIN') {
        if (raw === 'XAUUSD') return 'XAU/USD';
        if (raw === 'XAUTRYM') return 'Altın / TRY';
        return 'Altın Vadeli';
    }

    if (cat === 'EQUITY') return `${raw} Pay Vadeli`;
    if (cat === 'INDEX') return `${raw} / Endeks Vadeli`;
    if (cat === 'FX') return `${raw.replace(/([A-Z]{3})([A-Z]{3})/, '$1/$2')}`;
    if (cat === 'COMMODITY') return `${raw} Emtia Vadeli`;

    return raw;
}

export function viopUnderlyingCode(symbol: string): string {
    return parseViopContractSuffix(symbol)?.underlying ?? symbol.trim().toUpperCase().replace(/^F_/, '');
}
