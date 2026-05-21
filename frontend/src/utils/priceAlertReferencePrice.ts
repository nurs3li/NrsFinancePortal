import { marketClient } from '../api/client';
import { getBistLatestBySymbol } from '../services/bistEquityApi';
import type { LatestPriceRow } from '../components/market/marketTypes';
import type { PriceAlertAssetType } from '../types/priceAlert';

function pickLatestRowPrice(row: LatestPriceRow | undefined): number | null {
    if (!row || row.status === 'NO_DATA') return null;
    const n = Number(row.buyPrice ?? row.buy ?? row.price ?? row.sellPrice ?? row.sell ?? 0);
    return Number.isFinite(n) && n > 0 ? n : null;
}

function toNum(v: unknown): number | null {
    if (v == null) return null;
    const n = typeof v === 'number' ? v : Number(String(v).replace(',', '.'));
    return Number.isFinite(n) && n > 0 ? n : null;
}

async function loadUsdTryRate(signal?: AbortSignal): Promise<number | null> {
    const { data } = await marketClient.get<Record<string, LatestPriceRow>>('/api/market/doviz/latest', { signal });
    return pickLatestRowPrice(data?.USDTRY);
}

async function priceFromLatestMap(
    path: string,
    symbol: string,
    convertUsdToTry: boolean,
    signal?: AbortSignal,
): Promise<number | null> {
    const { data } = await marketClient.get<Record<string, LatestPriceRow>>(path, { signal });
    const sym = symbol.trim().toUpperCase();
    const row = data?.[sym];
    const px = pickLatestRowPrice(row);
    if (px == null) return null;
    if (!convertUsdToTry) return px;
    const usdTry = await loadUsdTryRate(signal);
    return usdTry != null ? px * usdTry : px;
}

export async function fetchPriceAlertReferencePrice(
    assetType: PriceAlertAssetType,
    symbol: string,
    signal?: AbortSignal,
): Promise<number | null> {
    const sym = symbol.trim().toUpperCase();
    if (!sym) return null;

    try {
        switch (assetType) {
            case 'FX':
                return priceFromLatestMap('/api/market/doviz/latest', sym, false, signal);
            case 'CRYPTO':
                return priceFromLatestMap('/api/market/crypto/latest', sym, true, signal);
            case 'METAL':
                return priceFromLatestMap('/api/market/metals/latest', sym, false, signal);
            case 'FUND':
                return priceFromLatestMap('/api/market/funds/latest', sym, true, signal);
            case 'STOCK':
                return priceFromLatestMap('/api/market/equity/latest', sym, true, signal);
            case 'BIST': {
                const row = await getBistLatestBySymbol(sym, signal);
                return toNum(row?.adjustedClose ?? row?.rawClose);
            }
            case 'VIOP': {
                const { data } = await marketClient.get<{
                    last?: number | null;
                    bid?: number | null;
                    ask?: number | null;
                }>(`/api/market/viop/contracts/${encodeURIComponent(sym)}/snapshot`, { signal });
                return toNum(data?.last ?? data?.bid ?? data?.ask);
            }
            case 'BOND': {
                const { data } = await marketClient.get<
                    { isin: string; dirtyPrice?: number | null }[]
                >('/api/market/debt/latest', { signal });
                const row = (data ?? []).find((r) => r.isin?.trim().toUpperCase() === sym);
                return toNum(row?.dirtyPrice);
            }
            default:
                return null;
        }
    } catch {
        return null;
    }
}
