import { financeClient, marketClient } from '../api/client';
import type { AssetClass, AssetType } from '../constants/OrderConstants';
import { classifyDebtInstrument, classifyViopContract } from '../constants/OrderConstants';

type MarketOverview = {
    doviz?: Record<string, { buyPrice?: number; sellPrice?: number; source?: string }>;
    metals?: Record<string, { buyPrice?: number; source?: string }>;
    crypto?: Record<string, { buyPrice?: number; source?: string }>;
    funds?: Record<string, { buyPrice?: number; source?: string }>;
    stocks?: Record<string, { buyPrice?: number; source?: string }>;
    timestamp?: string;
};

type ViopContract = { contractCode: string; underlying: string; expiry: string; type: string };
type DebtInstrument = { isin: string; name: string; issuer: string; maturityDate: string };

export async function fetchSpotSymbolsByAssetClass(assetClass: AssetClass): Promise<string[]> {
    const overviewRes = await financeClient.get<MarketOverview>('/api/market/overview');
    const overview = ((overviewRes.data as any)?.data ?? overviewRes.data) as MarketOverview;
    const map = assetClass === 'SPOT_EQUITY'
        ? overview?.stocks
        : assetClass === 'SPOT_CRYPTO'
            ? overview?.crypto
            : assetClass === 'SPOT_FX'
                ? overview?.doviz
                : overview?.metals;
    return Object.keys(map ?? {}).filter((k) => (map as any)?.[k] != null);
}

export async function fetchViopSymbolsByClass(assetClass: AssetClass): Promise<string[]> {
    const res = await marketClient.get<ViopContract[]>('/api/market/viop/contracts');
    const rows = (res.data ?? []).map((x) => String(x.contractCode ?? '').toUpperCase()).filter(Boolean);
    return rows.filter((symbol) => classifyViopContract(symbol) === assetClass);
}

export async function fetchDebtSymbolsByClass(assetClass: AssetClass): Promise<string[]> {
    const res = await marketClient.get<DebtInstrument[]>('/api/market/debt/catalog');
    const rows = res.data ?? [];
    return rows
        .filter((x) => classifyDebtInstrument(x.name, x.issuer, x.isin) === assetClass)
        .map((x) => String(x.isin ?? '').toUpperCase())
        .filter(Boolean);
}

export async function fetchSimulationSymbolsByType(type: AssetType): Promise<string[]> {
    const endpoint =
        type === 'FX'
            ? '/api/market/doviz/latest'
            : type === 'CRYPTO'
                ? '/api/market/crypto/latest'
                : type === 'METAL'
                    ? '/api/market/metals/latest'
                    : type === 'FUND'
                        ? '/api/market/funds/latest'
                        : '/api/market/equity/latest';
    const res = await marketClient.get<Record<string, unknown>>(endpoint);
    const rows = Object.entries(res.data ?? {});
    return rows
        .filter(([, value]) => {
            if (!value || typeof value !== 'object') return false;
            const row = value as Record<string, unknown>;
            if (String(row.status ?? '').toUpperCase() === 'NO_DATA') return false;
            const buy = Number(row.buyPrice ?? 0);
            const sell = Number(row.sellPrice ?? 0);
            return buy > 0 || sell > 0;
        })
        .map(([key]) => String(key ?? '').toUpperCase())
        .filter(Boolean)
        .sort((a, b) => a.localeCompare(b, 'tr-TR'));
}
