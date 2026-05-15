import { marketClient } from '../api/client';

export type BistSymbolDto = {
    symbol: string;
    displayName?: string | null;
    sector?: string | null;
};

export type BistEquityLatestDto = {
    symbol: string;
    displayName?: string | null;
    sector?: string | null;
    currency?: string | null;
    adjustedClose?: number | string | null;
    rawClose?: number | string | null;
    change?: number | string | null;
    changePercent?: number | string | null;
    volume?: number | string | null;
    marketCapTry?: number | string | null;
    marketCapUsd?: number | string | null;
    source?: string | null;
    dataQuality?: string | null;
    lastUpdated?: string | null;
};

export type BistEquityHistoryDto = {
    symbol: string;
    date: string;
    open?: number | string | null;
    high?: number | string | null;
    low?: number | string | null;
    close?: number | string | null;
    volume?: number | string | null;
    adjustedClose?: number | string | null;
    rawClose?: number | string | null;
    usdTry?: number | string | null;
    bist100Value?: number | string | null;
    marketCapTry?: number | string | null;
    source?: string | null;
    dataQuality?: string | null;
};

export type BistEquityCandleDto = BistEquityHistoryDto;

export type BistBatchHistoryDto = {
    historiesBySymbol?: Record<string, BistEquityHistoryDto[]>;
};

function toNum(v: unknown): number {
    if (v == null) return Number.NaN;
    if (typeof v === 'number') return Number.isFinite(v) ? v : Number.NaN;
    if (typeof v === 'string') {
        const n = parseFloat(v.replace(',', '.'));
        return Number.isFinite(n) ? n : Number.NaN;
    }
    const n = Number(v);
    return Number.isFinite(n) ? n : Number.NaN;
}

export async function getBistSymbols(signal?: AbortSignal): Promise<BistSymbolDto[]> {
    const { data } = await marketClient.get<BistSymbolDto[]>('/api/market/equities/bist/symbols', { signal });
    return Array.isArray(data) ? data : [];
}

export async function getBistLatest(signal?: AbortSignal): Promise<BistEquityLatestDto[]> {
    const { data } = await marketClient.get<BistEquityLatestDto[]>('/api/market/equities/bist/latest', { signal });
    return Array.isArray(data) ? data : [];
}

export async function getBistLatestBySymbol(symbol: string, signal?: AbortSignal): Promise<BistEquityLatestDto | null> {
    const { data } = await marketClient.get<BistEquityLatestDto | ''>(
        `/api/market/equities/bist/${encodeURIComponent(symbol)}/latest`,
        { signal }
    );
    if (data == null || data === '') return null;
    return data as BistEquityLatestDto;
}

export async function getBistHistory(symbol: string, from: string, to: string, signal?: AbortSignal): Promise<BistEquityHistoryDto[]> {
    const { data } = await marketClient.get<BistEquityHistoryDto[]>(
        `/api/market/equities/bist/${encodeURIComponent(symbol)}/history`,
        { params: { from, to }, signal }
    );
    return Array.isArray(data) ? data : [];
}

export async function getBistCandles(symbol: string, from: string, to: string, signal?: AbortSignal): Promise<BistEquityCandleDto[]> {
    const { data } = await marketClient.get<BistEquityCandleDto[]>(
        `/api/market/equities/bist/${encodeURIComponent(symbol)}/candles`,
        { params: { from, to }, signal }
    );
    return Array.isArray(data) ? data : [];
}

export async function getBistBatchHistory(
    symbols: string[],
    from: string,
    to: string,
    signal?: AbortSignal
): Promise<BistBatchHistoryDto> {
    const sym = symbols.filter(Boolean).join(',');
    const { data } = await marketClient.get<BistBatchHistoryDto>('/api/market/equities/bist/batch-history', {
        params: { symbols: sym, from, to },
        signal,
    });
    return data && typeof data === 'object' ? data : {};
}

export function bistPickClose(row: Pick<BistEquityHistoryDto, 'adjustedClose' | 'close' | 'rawClose'>): number {
    const a = toNum(row.adjustedClose);
    if (a > 0) return a;
    const c = toNum(row.close);
    if (c > 0) return c;
    const r = toNum(row.rawClose);
    return r > 0 ? r : Number.NaN;
}

export function bistLatestPrice(row: BistEquityLatestDto): number {
    const a = toNum(row.adjustedClose);
    if (a > 0) return a;
    const r = toNum(row.rawClose);
    return r > 0 ? r : 0;
}
