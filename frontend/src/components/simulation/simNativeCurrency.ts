import type { AssetType } from '../../constants/OrderConstants';
import type { SimDisplayCurrency, SimulationResultItem } from './types';

export type NativeQuoteCurrency = 'TRY' | 'USD' | 'GBP' | 'EUR';

/** Varlığın kotasyon / pozisyon birimi (grafikte ikincil gösterim için). */
export function resolveNativeQuoteCurrency(assetType: AssetType, symbol: string): NativeQuoteCurrency {
    const sym = symbol.trim().toUpperCase();
    if (assetType === 'BIST' || assetType === 'METAL') return 'TRY';
    if (assetType === 'FX') {
        if (sym.startsWith('GBP')) return 'GBP';
        if (sym.startsWith('EUR')) return 'EUR';
        if (sym.startsWith('USD')) return 'USD';
        return 'TRY';
    }
    if (assetType === 'CRYPTO' || assetType === 'STOCK' || assetType === 'FUND') return 'USD';
    return 'TRY';
}

export function nativeCurrencySymbol(currency: NativeQuoteCurrency): string {
    switch (currency) {
        case 'USD':
            return '$';
        case 'GBP':
            return '£';
        case 'EUR':
            return '€';
        default:
            return '₺';
    }
}

export function formatNativeMoney(locale: string, value: number, currency: NativeQuoteCurrency): string {
    if (currency === 'TRY' || currency === 'USD') {
        return new Intl.NumberFormat(locale, {
            style: 'currency',
            currency,
            maximumFractionDigits: 2,
        }).format(value);
    }
    const sym = nativeCurrencySymbol(currency);
    const n = new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
    return `${sym}${n}`;
}

export function simulationUnits(res: SimulationResultItem): number {
    if (res.unitsBought > 0) return res.unitsBought;
    if (res.buyPrice > 0) return res.initialAmount / res.buyPrice;
    return 0;
}

export type DualMoneyParts = {
    primary: number;
    secondary: number | null;
    nativeCurrency: NativeQuoteCurrency;
};

/**
 * Toplam pozisyon değeri: birincil = oturum para birimi (TRY/USD), ikincil = varlığın kendi kuru (varsa).
 */
export function computeDualTotalValue(
    res: SimulationResultItem,
    valueInDisplay: number,
    usdTryRate?: number | null,
): DualMoneyParts {
    const native = resolveNativeQuoteCurrency(res.assetType, res.assetName);
    const dc = res.displayCurrency;
    const primary = valueInDisplay;

    if (native === dc) {
        return { primary, secondary: null, nativeCurrency: native };
    }

    const units = simulationUnits(res);

    // XXXTRY: birim fiyat TRY/XXX → pozisyon büyüklüğü XXX cinsinden = units
    if (res.assetType === 'FX' && res.assetName.toUpperCase().endsWith('TRY') && units > 0) {
        return { primary, secondary: units, nativeCurrency: native };
    }

    if (native === 'USD' && dc === 'TRY') {
        const rate = usdTryRate && usdTryRate > 0 ? usdTryRate : null;
        return { primary, secondary: rate ? primary / rate : null, nativeCurrency: 'USD' };
    }

    if (native === 'TRY' && dc === 'USD') {
        const rate = usdTryRate && usdTryRate > 0 ? usdTryRate : null;
        return { primary, secondary: rate ? primary * rate : null, nativeCurrency: 'TRY' };
    }

    return { primary, secondary: null, nativeCurrency: native };
}
