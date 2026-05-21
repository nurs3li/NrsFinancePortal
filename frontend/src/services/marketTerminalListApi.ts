import type { AxiosResponse } from 'axios';
import { financeClient } from '../api/client';
import type { MarketCategory } from '../components/market/marketTypes';
import { unwrapFinanceSuccess } from './manualPortfolioApi';

export const MARKET_LIST_PAGE_SIZE = 5;

export type MarketTerminalListItem = {
    symbol: string;
    category: MarketCategory;
    equitySubmarket?: string | null;
    fundSubmarket?: string | null;
    displayName?: string | null;
    name?: string | null;
    price: number;
    changePercent: number;
    dailyChangePercent?: number | null;
    trend: 'UP' | 'DOWN';
    volume?: number | null;
    currency?: string | null;
    marketRegion?: string | null;
    exchange?: string | null;
    sector?: string | null;
    source?: string | null;
    delayMinutes?: number | null;
    sparklineCloses: number[];
    pctDay?: number | null;
    pctWeek?: number | null;
    pctMonth?: number | null;
    pctYear?: number | null;
    maturityDate?: string | null;
    daysToMaturity?: number | null;
    couponRate?: number | null;
    yieldToMaturity?: number | null;
    couponFrequencyPerYear?: number | null;
    couponFrequencyLabel?: string | null;
    contractMonth?: string | null;
    basis?: number | null;
    marginRequirement?: number | null;
    fundRiskLevel?: number | null;
    fundReturn6m?: number | null;
    fundReturn3y?: number | null;
    fundReturn5y?: number | null;
};

export type MarketTerminalListPage = {
    items: MarketTerminalListItem[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
};

export type FundSubmarket = 'US' | 'TR';

export type MarketTerminalListParams = {
    category: MarketCategory;
    equitySubmarket?: string;
    fundSubmarket?: FundSubmarket;
    page?: number;
    size?: number;
    filter?: string;
    sort?: string | null;
    dir?: 'asc' | 'desc';
    search?: string;
};

export async function fetchMarketTerminalList(
    params: MarketTerminalListParams,
    signal?: AbortSignal,
): Promise<MarketTerminalListPage> {
    const res = await financeClient.get<MarketTerminalListPage>('/api/market/terminal/list', {
        params: {
            category: params.category,
            equitySubmarket: params.equitySubmarket,
            fundSubmarket: params.fundSubmarket,
            page: params.page ?? 0,
            size: params.size ?? MARKET_LIST_PAGE_SIZE,
            filter: params.filter ?? 'ALL',
            sort: params.sort ?? undefined,
            dir: params.dir ?? 'desc',
            search: params.search?.trim() || undefined,
        },
        signal,
    });
    let payload: MarketTerminalListPage;
    try {
        payload = unwrapFinanceSuccess<MarketTerminalListPage>(res as AxiosResponse<unknown>);
    } catch {
        payload = res.data;
    }
    if (!payload || !Array.isArray(payload.items)) {
        return {
            items: [],
            page: 0,
            size: params.size ?? MARKET_LIST_PAGE_SIZE,
            totalElements: 0,
            totalPages: 0,
            hasNext: false,
            hasPrevious: false,
        };
    }
    return payload;
}
