/**
 * VIOP sözleşme whitelist'i — backend `app.viop.query.allowed-contracts` ve
 * `app.market.viop.whitelist` ile uyumlu tutulmalıdır.
 */
export const VIOP_WHITELIST: readonly string[] = [
    'F_XU0301226',
    'F_XLBNK1226',
    'F_USDTRY1226',
    'F_EURTRY1226',
    'F_XAUTRYM1026',
    'F_XAUUSD1026',
    'F_GARAN0726',
    'F_THYAO0726',
    'F_ASELS0726',
    'F_AKBNK0726',
    'F_SISE0726',
    'F_EREGL0726',
] as const;

export type ViopCategory = 'FX' | 'INDEX' | 'COMMODITY' | 'EQUITY';

const VIOP_CATEGORY_MAP: Record<string, ViopCategory> = {
    F_XU0301226: 'INDEX',
    F_XLBNK1226: 'INDEX',
    F_USDTRY1226: 'FX',
    F_EURTRY1226: 'FX',
    F_XAUTRYM1026: 'COMMODITY',
    F_XAUUSD1026: 'COMMODITY',
    F_GARAN0726: 'EQUITY',
    F_THYAO0726: 'EQUITY',
    F_ASELS0726: 'EQUITY',
    F_AKBNK0726: 'EQUITY',
    F_SISE0726: 'EQUITY',
    F_EREGL0726: 'EQUITY',
};

export function viopCategoryFor(code: string | null | undefined): ViopCategory | null {
    if (!code) return null;
    const trimmed = code.trim().toUpperCase();
    if (VIOP_CATEGORY_MAP[trimmed]) return VIOP_CATEGORY_MAP[trimmed];
    if (!trimmed.startsWith('F_') && VIOP_CATEGORY_MAP[`F_${trimmed}`]) return VIOP_CATEGORY_MAP[`F_${trimmed}`];
    return null;
}

const WHITELIST_SET: Set<string> = new Set(VIOP_WHITELIST);

/** Verilen kontrat kodu whitelist'te mi? Ham kod, `F_` öneki olmayan kod veya boşluk içeren
 * varyasyonları tolere eder. */
export function isViopWhitelisted(code: string | null | undefined): boolean {
    if (!code) return false;
    const trimmed = code.trim().toUpperCase();
    if (!trimmed) return false;
    if (WHITELIST_SET.has(trimmed)) return true;
    if (!trimmed.startsWith('F_') && WHITELIST_SET.has(`F_${trimmed}`)) return true;
    if (trimmed.startsWith('F_') && WHITELIST_SET.has(trimmed.substring(2))) return true;
    return false;
}
