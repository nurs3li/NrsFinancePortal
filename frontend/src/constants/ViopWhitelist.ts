/**
 * VIOP sözleşme whitelist'i — backend `app.viop.query.allowed-contracts` ile birebir eşleşir.
 *
 * Kategori dengeli vitrin (toplam 17 kontrat):
 *  - 10 Döviz:  8 adet USDTRY vadeli (May'26 → Ara'26) + EURUSD12/26 + EURTRY12/26
 *  - 1 Endeks:  BIST30 (XU030) Haziran 2026
 *  - 2 Emtia:   Gram Altın TRY (XAUTRY) ve ONS Altın USD (XAUUSD), Haziran 2026
 *  - 4 Pay:     THYAO / EREGL / SASA / SISE, Haziran 2026 (en likit BIST hisseleri)
 *
 * Bu sayede UI'da "VİOP Enstrümanları: Döviz, Endeks, Emtia ve Pay kalemlerini kapsıyoruz"
 * sözünün arkasında somut, likit ve sürekli veri akışı olan kontratlar duruyor. Tüm seçimler
 * geçmiş CSV kapsamasında ≥12/19 aktif işlem gören sözleşmelerdir → grafikte boş alan kalmaz.
 *
 * Liste güncellenirken `marketdata/src/main/resources/application.yml` ve
 * `application-docker.yml` içindeki `app.viop.query.allowed-contracts` ile birlikte
 * güncellenmelidir. Kategori seçim metodolojisi: `artifacts/viop/_category_picks.py` ve
 * `artifacts/viop/_equity_futures_check.py`.
 */
export const VIOP_WHITELIST: readonly string[] = [
    // --- Döviz: USDTRY 8 vade (en yakın) ---
    'F_USDTRY0526',
    'F_USDTRY0626',
    'F_USDTRY0726',
    'F_USDTRY0826',
    'F_USDTRY0926',
    'F_USDTRY1026',
    'F_USDTRY1126',
    'F_USDTRY1226',
    // --- Döviz: EUR çaprazları (Aralık 2026) ---
    'F_EURUSD1226',
    'F_EURTRY1226',
    // --- Endeks: BIST30 vadeli (Haziran 2026) ---
    'F_XU0300626',
    // --- Emtia: Gram Altın TRY + ONS Altın USD (Haziran 2026) ---
    'F_XAUTRYM0626',
    'F_XAUUSD0626',
    // --- Pay vadelileri (Haziran 2026) ---
    'F_THYAO0626',
    'F_EREGL0626',
    'F_SASA0626',
    'F_SISE0626',
] as const;

/**
 * Kategori etiketleri — UI'de "Döviz / Endeks / Emtia / Pay" grupları göstermek için
 * kullanılır. Whitelist'e eklenen yeni kontratları kategoriye eklemeyi unutma.
 */
export type ViopCategory = 'FX' | 'INDEX' | 'COMMODITY' | 'EQUITY';

const VIOP_CATEGORY_MAP: Record<string, ViopCategory> = {
    F_USDTRY0526: 'FX',
    F_USDTRY0626: 'FX',
    F_USDTRY0726: 'FX',
    F_USDTRY0826: 'FX',
    F_USDTRY0926: 'FX',
    F_USDTRY1026: 'FX',
    F_USDTRY1126: 'FX',
    F_USDTRY1226: 'FX',
    F_EURUSD1226: 'FX',
    F_EURTRY1226: 'FX',
    F_XU0300626: 'INDEX',
    F_XAUTRYM0626: 'COMMODITY',
    F_XAUUSD0626: 'COMMODITY',
    F_THYAO0626: 'EQUITY',
    F_EREGL0626: 'EQUITY',
    F_SASA0626: 'EQUITY',
    F_SISE0626: 'EQUITY',
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
