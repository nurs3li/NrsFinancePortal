import type { LucideIcon } from 'lucide-react';
import { Bitcoin, Building2, Coins, Gem, Landmark, TrendingUp } from 'lucide-react';
import type { MarketKind } from '../../lib/assetBranding';
import { formatAssetLabel, getDynamicLogoUrl } from '../../lib/assetBranding';
import { SIMULATION_ASSET_TYPES, type SimulationAssetType } from '../../types/simulationAssetType';

export function assetTypeToMarketKind(type: SimulationAssetType): MarketKind {
    switch (type) {
        case 'CRYPTO':
            return 'CRYPTO';
        case 'FX':
            return 'FX';
        case 'METAL':
            return 'METALS';
        case 'TR_FUND':
        case 'FUND':
            return 'FUNDS';
        case 'STOCK':
        case 'BIST':
            return 'EQUITY';
        default:
            return 'FX';
    }
}

export function categoryLabel(type: SimulationAssetType, t: (k: string, d: string) => string): string {
    switch (type) {
        case 'CRYPTO':
            return t('category.crypto', 'Kripto');
        case 'FX':
            return t('category.fx', 'Döviz');
        case 'METAL':
            return t('category.metals', 'Kıymetli madenler');
        case 'TR_FUND':
            return t('funds.turkishFunds', 'Türk Fonları');
        case 'FUND':
            return t('funds.usFunds', 'Amerika Fonları');
        case 'STOCK':
            return t('stocks.usStocks', 'ABD Hisseleri');
        case 'BIST':
            return t('stocks.turkishStocks', 'Türk Hisseleri');
        default:
            return type;
    }
}

export function categoryFallbackIcon(type: SimulationAssetType): LucideIcon {
    switch (type) {
        case 'CRYPTO':
            return Bitcoin;
        case 'FX':
            return Coins;
        case 'METAL':
            return Gem;
        case 'TR_FUND':
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

export function symbolLogoUrl(symbol: string, assetType: SimulationAssetType): string | null {
    return getDynamicLogoUrl(symbol, assetTypeToMarketKind(assetType), { assetType });
}

export function symbolDisplayLabel(symbol: string, assetType: SimulationAssetType): string {
    return formatAssetLabel(symbol, assetTypeToMarketKind(assetType));
}

export const SIM_ASSET_TYPES = [...SIMULATION_ASSET_TYPES];
