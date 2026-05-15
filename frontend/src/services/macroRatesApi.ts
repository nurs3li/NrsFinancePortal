import { marketClient } from '../api/client';

/** React Query — kredi faizleri (borçlanma maliyeti, EVDS haftalık akım). */
export const macroLoanRatesQueryKeys = {
    latest: () => ['macro', 'loanRates', 'latest'] as const,
    history: (types: string, from: string, to: string) => ['macro', 'loanRates', 'history', types, from, to] as const,
    policyTr: () => ['macro', 'policyRateTr'] as const,
    depositLatest: () => ['macro', 'depositRates', 'latest'] as const,
};

export type LoanRateLatestItem = {
    type: string;
    label: string;
    seriesCode: string;
    value: number;
    description: string;
};

export type LoanRatesLatestResponse = {
    source: string;
    frequency: string;
    unit: string;
    asOf: string | null;
    items: LoanRateLatestItem[];
};

export type LoanRateHistoryPoint = { date: string; value: number };

export type LoanRateHistorySeries = {
    type: string;
    label: string;
    seriesCode: string;
    points: LoanRateHistoryPoint[];
};

export type LoanRatesHistoryResponse = {
    source: string;
    frequency: string;
    unit: string;
    series: LoanRateHistorySeries[];
};

export async function getLoanRatesLatest(signal?: AbortSignal): Promise<LoanRatesLatestResponse | null> {
    try {
        const { data } = await marketClient.get<LoanRatesLatestResponse>('/api/market/macro/loan-rates/latest', { signal });
        return data ?? null;
    } catch {
        return null;
    }
}

export async function getLoanRatesHistory(
    params: { types: string; from: string; to: string },
    signal?: AbortSignal,
): Promise<LoanRatesHistoryResponse | null> {
    try {
        const { data } = await marketClient.get<LoanRatesHistoryResponse>('/api/market/macro/loan-rates/history', {
            params: { types: params.types, from: params.from, to: params.to },
            signal,
        });
        return data ?? null;
    } catch {
        return null;
    }
}

/** Borçlanma maliyeti yüzdesi (EVDS düzey %); 61.55 → "61,55%". */
export function formatBorrowingCostPercent(value: number | null | undefined, locale = 'tr-TR'): string {
    if (value == null || !Number.isFinite(Number(value))) return '—';
    return `${Number(value).toLocaleString(locale, { maximumFractionDigits: 2 })}%`;
}
