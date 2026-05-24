import type { LucideIcon } from 'lucide-react';
import { Bitcoin, Building2, Coins, Gem, Landmark, TrendingUp } from 'lucide-react';
import type { AssetType } from '../../constants/OrderConstants';
import type { MarketKind } from '../../lib/assetBranding';
import { formatAssetLabel, getDynamicLogoUrl } from '../../lib/assetBranding';

export function assetTypeToMarketKind(type: AssetType): MarketKind {
    switch (type) {
        case 'CRYPTO':
            return 'CRYPTO';
        case 'FX':
            return 'FX';
        case 'METAL':
            return 'METALS';
        case 'FUND':
            return 'FUNDS';
        case 'STOCK':
        case 'BIST':
            return 'EQUITY';
        default:
            return 'FX';
    }
}

export function categoryLabel(type: AssetType, t: (k: string, d: string) => string): string {
    switch (type) {
        case 'CRYPTO':
            return t('category.crypto', 'Kripto');
        case 'FX':
            return t('category.fx', 'Döviz');
        case 'METAL':
            return t('category.metals', 'Kıymetli madenler');
        case 'FUND':
            return t('category.funds', 'Fonlar');
        case 'STOCK':
            return t('category.equity', 'Hisse (ABD)');
        case 'BIST':
            return t('category.bist', 'BIST Hisse');
        default:
            return type;
    }
}

export function categoryFallbackIcon(type: AssetType): LucideIcon {
    switch (type) {
        case 'CRYPTO':
            return Bitcoin;
        case 'FX':
            return Coins;
        case 'METAL':
            return Gem;
        case 'FUND':
            return Landmark;
        case 'STOCK':
            return TrendingUp;
        case 'BIST':
            return Building2;
        default:
            return Coins;
    }
}

export function symbolLogoUrl(symbol: string, assetType: AssetType): string | null {
    return getDynamicLogoUrl(symbol, assetTypeToMarketKind(assetType), { assetType });
}

export function symbolDisplayLabel(symbol: string, assetType: AssetType): string {
    return formatAssetLabel(symbol, assetTypeToMarketKind(assetType));
}

export const SIM_ASSET_TYPES: AssetType[] = ['CRYPTO', 'FX', 'METAL', 'FUND', 'STOCK', 'BIST'];
