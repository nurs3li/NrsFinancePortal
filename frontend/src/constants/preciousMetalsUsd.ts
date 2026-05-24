/** USD/ons spot — fiyat değil; ürün açıklamaları (İş Yatırım serisi). */
export const PRECIOUS_USD_OZ_DISPLAY: Record<string, string> = {
    XAU_USD_OZ: 'Altın Ons',
    XAG_USD_OZ: 'Gümüş Ons',
    XPT_USD_OZ: 'Platin Ons',
    XPD_USD_OZ: 'Paladyum Ons',
};

export const PRECIOUS_USD_OZ_DESCRIPTION: Record<string, string> = {
    XAU_USD_OZ:
        'Güvenli liman algısı, enflasyon beklentileri, faiz oranları, dolar endeksi ve merkez bankası rezervlerinden etkilenir.',
    XAG_USD_OZ:
        'Hem değerli metal hem de endüstriyel kullanım alanı olan bir üründür. Altına göre daha volatil hareket edebilir.',
    XPT_USD_OZ:
        'Otomotiv katalizörleri, endüstriyel talep, arz koşulları ve küresel büyüme beklentilerinden etkilenir.',
    XPD_USD_OZ:
        'Otomotiv katalizör talebi, arz riski ve yüksek volatilite ile öne çıkan bir kıymetli metaldir.',
};

/** Piyasa listesi “Kıymetli madenler” sekmesi: yalnızca bu semboller (sıra korunur). */
export const PRECIOUS_METAL_SYMBOLS = ['XAU_TRY', 'XAU_USD_OZ', 'XAG_USD_OZ', 'XPT_USD_OZ', 'XPD_USD_OZ'] as const;

export const PRECIOUS_METAL_GRAM_SYMBOLS = ['XAU_TRY'] as const;
export const PRECIOUS_METAL_OUNCE_SYMBOLS = ['XAU_USD_OZ', 'XAG_USD_OZ', 'XPT_USD_OZ', 'XPD_USD_OZ'] as const;

export type PreciousMetalSymbol = (typeof PRECIOUS_METAL_SYMBOLS)[number];

/** Gram (TRY) ile ons (USD) listeleri ve ısı haritası ayrı gösterilir. */
export type MetalsSubmarket = 'GRAM' | 'OUNCE';

export type PreciousMetalDisplayMeta = {
    displayName: string;
    subtitle: string;
    unitLabel: string;
    sourceLabel?: string;
    delayLabel?: string;
    /** Arama (Türkçe/İngilizce anahtar kelimeler) */
    searchTerms: string[];
};

function normSym(symbol: string | undefined): string {
    return String(symbol ?? '')
        .trim()
        .replace(/\s+/g, '')
        .toUpperCase();
}

export const PRECIOUS_METAL_DISPLAY_META: Record<string, PreciousMetalDisplayMeta> = {
    XAU_TRY: {
        displayName: 'Gram Altın',
        subtitle: 'XAU/TRY',
        unitLabel: 'TRY/gram',
        searchTerms: ['gram', 'altın', 'altin', 'xau', 'try', 'xau_try', 'gram altın', 'gram altin'],
    },
    XAU_USD_OZ: {
        displayName: 'Altın Ons',
        subtitle: 'XAU/USD',
        unitLabel: 'USD/ons',
        sourceLabel: 'İş Yatırım',
        delayLabel: 'Gecikmeli veri',
        searchTerms: ['altın', 'altin', 'ons', 'ounce', 'gold', 'xau', 'xau_usd_oz', 'altın ons', 'altin ons'],
    },
    XAG_USD_OZ: {
        displayName: 'Gümüş Ons',
        subtitle: 'XAG/USD',
        unitLabel: 'USD/ons',
        sourceLabel: 'İş Yatırım',
        delayLabel: 'Gecikmeli veri',
        searchTerms: ['gümüş', 'gumus', 'silver', 'xag', 'ons', 'xag_usd_oz'],
    },
    XPT_USD_OZ: {
        displayName: 'Platin Ons',
        subtitle: 'XPT/USD',
        unitLabel: 'USD/ons',
        sourceLabel: 'İş Yatırım',
        delayLabel: 'Gecikmeli veri',
        searchTerms: ['platin', 'platinum', 'xpt', 'ons', 'xpt_usd_oz'],
    },
    XPD_USD_OZ: {
        displayName: 'Paladyum Ons',
        subtitle: 'XPD/USD',
        unitLabel: 'USD/ons',
        sourceLabel: 'İş Yatırım',
        delayLabel: 'Gecikmeli veri',
        searchTerms: ['paladyum', 'palladium', 'xpd', 'ons', 'xpd_usd_oz'],
    },
};

export function getPreciousMetalDisplayMeta(symbol: string | undefined): PreciousMetalDisplayMeta | undefined {
    const k = normSym(symbol);
    return PRECIOUS_METAL_DISPLAY_META[k];
}

export function isPreciousMetalAllowlisted(symbol: string | undefined): boolean {
    const k = normSym(symbol);
    return (PRECIOUS_METAL_SYMBOLS as readonly string[]).includes(k);
}

export function isUsdPerOunceMetalSymbol(symbol: string | undefined): boolean {
    const s = String(symbol ?? '')
        .trim()
        .toUpperCase();
    return s.endsWith('_USD_OZ');
}

export function isGramMetalSymbol(symbol: string | undefined): boolean {
    const k = normSym(symbol);
    return k === 'XAU_TRY' || k === 'ALTIN_TRY';
}

export function metalsSubmarketForSymbol(symbol: string | undefined): MetalsSubmarket | null {
    if (isGramMetalSymbol(symbol)) return 'GRAM';
    if (isUsdPerOunceMetalSymbol(symbol)) return 'OUNCE';
    return null;
}

export function symbolMatchesMetalsSubmarket(symbol: string | undefined, sub: MetalsSubmarket): boolean {
    return metalsSubmarketForSymbol(symbol) === sub;
}

export function preciousMetalSymbolsForSubmarket(sub: MetalsSubmarket): readonly string[] {
    return sub === 'GRAM' ? PRECIOUS_METAL_GRAM_SYMBOLS : PRECIOUS_METAL_OUNCE_SYMBOLS;
}

export function defaultMetalSymbolForSubmarket(sub: MetalsSubmarket): string {
    return sub === 'GRAM' ? 'XAU_TRY' : 'XAU_USD_OZ';
}
