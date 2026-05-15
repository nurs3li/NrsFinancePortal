import axios, { type AxiosResponse } from 'axios';
import { financeClient } from '../api/client';
import type {
    ManualPortfolioAnalysis,
    ManualPortfolioClosePayload,
    ManualPortfolioCreatePayload,
    ManualPortfolioView,
    ManualResolvedPrice,
    ManualSummary,
} from '../types/manualPortfolio';

type ApiEnvelope<T> = {
    success?: boolean;
    data?: T;
    errors?: { code?: string; message?: string; error?: string };
};

export function unwrapFinanceSuccess<T>(res: AxiosResponse<unknown>): T {
    const body = res.data as ApiEnvelope<T>;
    if (body && typeof body === 'object' && body.success === true && body.data !== undefined) {
        return body.data as T;
    }
    throw new Error('Unexpected finance API response shape');
}

export function readFinanceApiError(err: unknown): { code?: string; message: string } {
    if (axios.isAxiosError(err)) {
        const data = err.response?.data as ApiEnvelope<unknown> | undefined;
        const e = data?.errors as { code?: string; message?: string; error?: string } | undefined;
        const msg = e?.message ?? e?.error ?? err.message ?? 'Request failed';
        return { code: typeof e?.code === 'string' ? e.code : undefined, message: msg };
    }
    return { message: err instanceof Error ? err.message : 'Request failed' };
}

export async function getManualPositions(): Promise<ManualPortfolioView[]> {
    const res = await financeClient.get('/api/portfolio/manual/me');
    return unwrapFinanceSuccess<ManualPortfolioView[]>(res);
}

export async function getManualSummary(): Promise<ManualSummary> {
    const res = await financeClient.get('/api/portfolio/manual/summary/me');
    return unwrapFinanceSuccess<ManualSummary>(res);
}

export async function resolveManualPrice(type: string, symbol: string, date: string): Promise<ManualResolvedPrice> {
    const res = await financeClient.get('/api/portfolio/manual/price-resolve', {
        params: { type, symbol, date },
    });
    return unwrapFinanceSuccess<ManualResolvedPrice>(res);
}

export async function createManualPosition(payload: ManualPortfolioCreatePayload): Promise<ManualPortfolioView> {
    const res = await financeClient.post('/api/portfolio/manual', payload);
    return unwrapFinanceSuccess<ManualPortfolioView>(res);
}

export async function updateManualPosition(id: number, payload: ManualPortfolioCreatePayload): Promise<ManualPortfolioView> {
    const res = await financeClient.put(`/api/portfolio/manual/${id}`, payload);
    return unwrapFinanceSuccess<ManualPortfolioView>(res);
}

export async function closeManualPosition(id: number, payload: ManualPortfolioClosePayload): Promise<ManualPortfolioView> {
    const res = await financeClient.post(`/api/portfolio/manual/${id}/close`, payload);
    return unwrapFinanceSuccess<ManualPortfolioView>(res);
}

export async function deleteManualPosition(id: number): Promise<void> {
    await financeClient.delete(`/api/portfolio/manual/${id}`);
}

export async function getManualPositionAnalysis(id: number): Promise<ManualPortfolioAnalysis> {
    const res = await financeClient.get(`/api/portfolio/manual/${id}/analysis`);
    return unwrapFinanceSuccess<ManualPortfolioAnalysis>(res);
}
