export type TemplateType = 'SPOT' | 'FUTURES' | 'FIXED_INCOME';
export type TradeType = 'BUY' | 'SELL';
export type AssetType = 'CRYPTO' | 'FX' | 'FUND' | 'METAL' | 'STOCK';

export type AssetClass =
    | 'SPOT_EQUITY'
    | 'SPOT_CRYPTO'
    | 'SPOT_FX'
    | 'SPOT_COMMODITY'
    | 'FUTURES_INDEX'
    | 'FUTURES_FX'
    | 'FUTURES_COMMODITY'
    | 'FUTURES_EQUITY'
    | 'BOND_GOV'
    | 'BOND_CORP'
    | 'BOND_EUROBOND';

export type AssetClassOption = {
    value: AssetClass;
    label: string;
    template: TemplateType;
    backendAssetType: AssetType;
};

export const TEMPLATE_LABELS: Record<TemplateType, string> = {
    SPOT: 'Spot',
    FUTURES: 'VIOP',
    FIXED_INCOME: 'Tahvil/Bono',
};

export const ASSET_CLASS_OPTIONS: AssetClassOption[] = [
    { value: 'SPOT_EQUITY', label: 'Hisse', template: 'SPOT', backendAssetType: 'STOCK' },
    { value: 'SPOT_CRYPTO', label: 'Kripto', template: 'SPOT', backendAssetType: 'CRYPTO' },
    { value: 'SPOT_FX', label: 'Doviz (FX)', template: 'SPOT', backendAssetType: 'FX' },
    { value: 'SPOT_COMMODITY', label: 'Emtia (Altin)', template: 'SPOT', backendAssetType: 'METAL' },
    { value: 'FUTURES_INDEX', label: 'Endeks Vadeli', template: 'FUTURES', backendAssetType: 'STOCK' },
    { value: 'FUTURES_FX', label: 'Doviz Vadeli', template: 'FUTURES', backendAssetType: 'FX' },
    { value: 'FUTURES_COMMODITY', label: 'Emtia Vadeli', template: 'FUTURES', backendAssetType: 'METAL' },
    { value: 'FUTURES_EQUITY', label: 'Pay Vadeli', template: 'FUTURES', backendAssetType: 'STOCK' },
    { value: 'BOND_GOV', label: 'Devlet Tahvili', template: 'FIXED_INCOME', backendAssetType: 'FUND' },
    { value: 'BOND_CORP', label: 'Ozel Sektor Tahvili', template: 'FIXED_INCOME', backendAssetType: 'FUND' },
    { value: 'BOND_EUROBOND', label: 'Eurobond', template: 'FIXED_INCOME', backendAssetType: 'FUND' },
];

export const TEMPLATE_THEME: Record<TemplateType, { border: string; bg: string; chip: string }> = {
    SPOT: { border: '#16a34a', bg: 'rgba(34,197,94,0.08)', chip: 'SPOT' },
    FUTURES: { border: '#f97316', bg: 'rgba(249,115,22,0.1)', chip: 'VIOP' },
    FIXED_INCOME: { border: '#2563eb', bg: 'rgba(37,99,235,0.1)', chip: 'TAHVIL/BONO' },
};

export function assetClassesByTemplate(template: TemplateType): AssetClassOption[] {
    return ASSET_CLASS_OPTIONS.filter((x) => x.template === template);
}

export function getAssetClassOption(value: AssetClass): AssetClassOption | undefined {
    return ASSET_CLASS_OPTIONS.find((x) => x.value === value);
}

export function inferTemplateBySymbol(symbol: string): TemplateType {
    const normalized = String(symbol ?? '').trim().toUpperCase();
    if (/^TR[A-Z0-9]{10}$/.test(normalized)) return 'FIXED_INCOME';
    if (/^[A-Z0-9_]+\d{4}$/.test(normalized)) return 'FUTURES';
    return 'SPOT';
}

export function classifyViopContract(contractCode: string): AssetClass {
    const normalized = String(contractCode ?? '').trim().toUpperCase();
    if (normalized.startsWith('USDTRY') || normalized.startsWith('EURTRY') || normalized.startsWith('GBPTRY')) {
        return 'FUTURES_FX';
    }
    if (normalized.startsWith('ALTIN') || normalized.startsWith('XAU') || normalized.startsWith('GOLD')) {
        return 'FUTURES_COMMODITY';
    }
    if (normalized.startsWith('XU') || normalized.startsWith('BIST')) {
        return 'FUTURES_INDEX';
    }
    return 'FUTURES_EQUITY';
}

export function classifyDebtInstrument(name: string | undefined, issuer: string | undefined, isin: string): AssetClass {
    const n = String(name ?? '').toUpperCase();
    const i = String(issuer ?? '').toUpperCase();
    const s = String(isin ?? '').toUpperCase();
    if (n.includes('EUROBOND') || i.includes('EUROBOND') || s.includes('XS')) return 'BOND_EUROBOND';
    if (i.includes('HAZINE') || i.includes('TREASURY') || n.includes('HAZINE')) return 'BOND_GOV';
    return 'BOND_CORP';
}

/** Tablo alt satırı: SPOT-KRIPTO vb. teknik etiket */
export function tradeTechnicalDetail(symbol: string, assetType: AssetType): string {
    const template = inferTemplateBySymbol(symbol);
    if (template === 'FUTURES') {
        const cls = classifyViopContract(symbol);
        const map: Record<AssetClass, string> = {
            FUTURES_INDEX: 'VIOP-ENDEKS',
            FUTURES_FX: 'VIOP-DOVIZ',
            FUTURES_COMMODITY: 'VIOP-EMTIA',
            FUTURES_EQUITY: 'VIOP-PAY',
            SPOT_EQUITY: 'SPOT-HISSE',
            SPOT_CRYPTO: 'SPOT-KRIPTO',
            SPOT_FX: 'SPOT-DOVIZ',
            SPOT_COMMODITY: 'SPOT-EMTIA',
            BOND_GOV: 'TAHVIL-DEVLET',
            BOND_CORP: 'TAHVIL-OZEL',
            BOND_EUROBOND: 'TAHVIL-EUROBOND',
        };
        return map[cls];
    }
    if (template === 'FIXED_INCOME') {
        return 'TAHVIL/BONO';
    }
    const assetMap: Record<AssetType, string> = {
        CRYPTO: 'SPOT-KRIPTO',
        FX: 'SPOT-DOVIZ',
        FUND: 'SPOT-FON',
        METAL: 'SPOT-EMTIA',
        STOCK: 'SPOT-HISSE',
    };
    return assetMap[assetType] ?? 'SPOT';
}

export function getTradeSideAndDetail(
    tradeType: TradeType,
    symbol: string,
    assetType: AssetType,
): { sideLabel: 'AL' | 'SAT'; detail: string } {
    return {
        sideLabel: tradeType === 'BUY' ? 'AL' : 'SAT',
        detail: tradeTechnicalDetail(symbol, assetType),
    };
}

export function professionalTradeLabel(tradeType: TradeType, symbol: string, assetType: AssetType): string {
    const { sideLabel, detail } = getTradeSideAndDetail(tradeType, symbol, assetType);
    return `${sideLabel} (${detail})`;
}
