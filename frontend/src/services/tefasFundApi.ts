import type { AxiosResponse } from 'axios';
import { financeClient, marketClient } from '../api/client';
import { unwrapFinanceSuccess } from './manualPortfolioApi';

export type TefasFundRow = {
    code: string;
    title?: string | null;
    fundType?: string | null;
    riskLevel?: string | null;
    tefasListed?: boolean;
    price?: number | null;
    return1d?: number | null;
    return1w?: number | null;
    return1m?: number | null;
    return3m?: number | null;
    return6m?: number | null;
    return1y?: number | null;
    returnYtd?: number | null;
    return3y?: number | null;
    return5y?: number | null;
    source?: string | null;
};

export type TefasFundPage = {
    items: TefasFundRow[];
    page: number;
    size: number;
    total: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
};

export type TefasFundHistoryPoint = {
    date: string;
    price: number;
};

export async function fetchTefasFundPage(
    page = 0,
    size = 50,
    sort = 'return1y',
    dir = 'desc',
    search?: string,
    signal?: AbortSignal,
): Promise<TefasFundPage> {
    const { data } = await marketClient.get<TefasFundPage>('/api/market/tefas/funds', {
        params: { page, size, sort, dir, search },
        signal,
    });
    return {
        items: Array.isArray(data?.items) ? data.items : [],
        page: Number.isFinite(data?.page) ? Number(data.page) : 0,
        size: Number.isFinite(data?.size) ? Number(data.size) : size,
        total: Number.isFinite(data?.total) ? Number(data.total) : 0,
        totalPages: Number.isFinite(data?.totalPages) ? Number(data.totalPages) : 0,
        hasNext: Boolean(data?.hasNext),
        hasPrevious: Boolean(data?.hasPrevious),
    };
}

export async function fetchTefasFundHistory(
    code: string,
    months: number,
    signal?: AbortSignal,
): Promise<TefasFundHistoryPoint[]> {
    const res = await financeClient.get<TefasFundHistoryPoint[]>(
        `/api/market/tefas/funds/${encodeURIComponent(code.trim().toUpperCase())}/history`,
        { params: { months }, signal },
    );
    try {
        const payload = unwrapFinanceSuccess<TefasFundHistoryPoint[]>(res as AxiosResponse<unknown>);
        return Array.isArray(payload) ? payload : [];
    } catch {
        const data = res.data;
        return Array.isArray(data) ? data : [];
    }
}

/** Grafik aralığı → TEFAS fiyat geçmişi periyodu (ay). */
export function tefasMonthsForChartRange(range: string): number {
    switch (range) {
        case '1D':
        case '1W':
            return 1;
        case '1M':
            return 1;
        case '3M':
            return 3;
        case '6M':
            return 6;
        case '1Y':
            return 12;
        case '2Y':
            return 24;
        default:
            return 12;
    }
}

/** Piyasa listesi sıralama anahtarı → TEFAS API sort parametresi. */
export function mapTerminalSortToTefas(sort: string | null | undefined): string | undefined {
    if (!sort) return undefined;
    const m: Record<string, string> = {
        price: 'return1y',
        pctDay: 'return1d',
        pctWeek: 'return1w',
        pctMonth: 'return1m',
        pctYear: 'return1y',
        fundReturn3m: 'return3m',
        fundReturn6m: 'return6m',
        return1m: 'return1m',
        return3m: 'return3m',
        return6m: 'return6m',
        returnytd: 'returnYtd',
        return3y: 'return3y',
        return5y: 'return5y',
    };
    return m[sort] ?? sort;
}
