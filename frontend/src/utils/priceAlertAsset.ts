import type { PriceAlertAssetType } from '../types/priceAlert';

export function marketCategoryToPriceAlertAsset(
    category: string,
    symbol: string,
    opts?: { marketRegion?: string | null; exchange?: string | null },
): { assetType: PriceAlertAssetType; symbol: string } | null {
    const sym = symbol.trim().toUpperCase();
    if (!sym) return null;

    const cat = category.toUpperCase();
    if (cat === 'CRYPTO') {
        return { assetType: 'CRYPTO', symbol: sym.endsWith('USDT') ? sym : `${sym}USDT` };
    }
    if (cat === 'FX') {
        const fxSym = sym.endsWith('TRY') ? sym : sym.length === 3 ? `${sym}TRY` : sym;
        return { assetType: 'FX', symbol: fxSym };
    }
    if (cat === 'METALS') {
        const metal = sym === 'ALTIN_TRY' ? 'XAU_TRY' : sym;
        return { assetType: 'METAL', symbol: metal };
    }
    if (cat === 'FUNDS') {
        return { assetType: 'FUND', symbol: sym };
    }
    if (cat === 'EQUITY') {
        const isBist =
            (opts?.marketRegion ?? '').toUpperCase() === 'TR' ||
            (opts?.exchange ?? '').toUpperCase() === 'BIST';
        return { assetType: isBist ? 'BIST' : 'STOCK', symbol: sym };
    }
    return null;
}

export function portfolioTypeToPriceAlertAsset(type: string, symbol: string): { assetType: PriceAlertAssetType; symbol: string } | null {
    const t = type.trim().toUpperCase();
    const sym = symbol.trim().toUpperCase();
    if (!sym) return null;
    const allowed: PriceAlertAssetType[] = ['FX', 'CRYPTO', 'STOCK', 'BIST', 'METAL', 'FUND'];
    if (allowed.includes(t as PriceAlertAssetType)) {
        if (t === 'FX' && !sym.endsWith('TRY') && sym.length === 3) {
            return { assetType: 'FX', symbol: `${sym}TRY` };
        }
        if (t === 'CRYPTO' && !sym.endsWith('USDT')) {
            return { assetType: 'CRYPTO', symbol: `${sym}USDT` };
        }
        if (t === 'METAL' && sym === 'ALTIN_TRY') {
            return { assetType: 'METAL', symbol: 'XAU_TRY' };
        }
        return { assetType: t as PriceAlertAssetType, symbol: sym };
    }
    return null;
}
