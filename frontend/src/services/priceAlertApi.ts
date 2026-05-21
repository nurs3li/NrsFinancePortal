import { financeClient } from '../api/client';
import type { PriceAlert, PriceAlertCreatePayload } from '../types/priceAlert';
import { unwrapFinanceSuccess, readFinanceApiError } from './manualPortfolioApi';

export { readFinanceApiError };

export type PriceAlertPageResult = {
    content: PriceAlert[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
};

export type PriceAlertListFilter = 'all' | 'active' | 'past';

function filterAlertsByTab(alerts: PriceAlert[], filter: PriceAlertListFilter): PriceAlert[] {
    if (filter === 'active') {
        return alerts.filter((a) => a.status === 'ACTIVE');
    }
    if (filter === 'past') {
        return alerts.filter((a) => a.status === 'TRIGGERED' || a.status === 'DISABLED');
    }
    return alerts;
}

function sortAlertsNewestFirst(alerts: PriceAlert[]): PriceAlert[] {
    return [...alerts].sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
}

function isPagedResponse(value: unknown): value is PriceAlertPageResult {
    return (
        value !== null &&
        typeof value === 'object' &&
        !Array.isArray(value) &&
        Array.isArray((value as PriceAlertPageResult).content)
    );
}

/** Eski finance-service jar'ı dizi döndürür; yeni sürüm PriceAlertPageResponse döner. */
function normalizePriceAlertPage(
    raw: PriceAlertPageResult | PriceAlert[],
    page: number,
    size: number,
    filter: PriceAlertListFilter,
): PriceAlertPageResult {
    if (isPagedResponse(raw)) {
        return raw;
    }
    const filtered = sortAlertsNewestFirst(filterAlertsByTab(raw, filter));
    const totalElements = filtered.length;
    const totalPages = Math.max(1, Math.ceil(totalElements / size) || 1);
    const safePage = Math.min(Math.max(0, page), totalPages - 1);
    const start = safePage * size;
    const content = filtered.slice(start, start + size);
    return {
        content,
        page: safePage,
        size,
        totalElements,
        totalPages,
        hasNext: safePage < totalPages - 1,
        hasPrevious: safePage > 0,
    };
}

export async function listPriceAlerts(): Promise<PriceAlert[]> {
    const res = await financeClient.get('/api/me/price-alerts');
    const raw = unwrapFinanceSuccess<PriceAlert[] | PriceAlertPageResult>(res);
    if (isPagedResponse(raw)) {
        return raw.content;
    }
    return Array.isArray(raw) ? raw : [];
}

export async function listPriceAlertsPage(
    page: number,
    size: number,
    filter: PriceAlertListFilter
): Promise<PriceAlertPageResult> {
    const res = await financeClient.get('/api/me/price-alerts', {
        params: { page, size, filter },
    });
    const raw = unwrapFinanceSuccess<PriceAlertPageResult | PriceAlert[]>(res);
    return normalizePriceAlertPage(raw, page, size, filter);
}

export async function createPriceAlert(payload: PriceAlertCreatePayload): Promise<PriceAlert> {
    const res = await financeClient.post('/api/me/price-alerts', payload);
    return unwrapFinanceSuccess<PriceAlert>(res);
}

export async function deletePriceAlert(id: number): Promise<void> {
    await financeClient.delete(`/api/me/price-alerts/${id}`);
}
