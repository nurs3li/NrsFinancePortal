import type { AxiosResponse } from 'axios';
import { financeClient } from '../api/client';
import { unwrapFinanceSuccess } from './manualPortfolioApi';

export type TefasFundHistoryPoint = {
    date: string;
    price: number;
};

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
