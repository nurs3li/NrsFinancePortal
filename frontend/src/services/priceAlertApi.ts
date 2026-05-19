import { financeClient } from '../api/client';
import type { PriceAlert, PriceAlertCreatePayload } from '../types/priceAlert';
import { unwrapFinanceSuccess, readFinanceApiError } from './manualPortfolioApi';

export { readFinanceApiError };

export async function listPriceAlerts(): Promise<PriceAlert[]> {
    const res = await financeClient.get('/api/me/price-alerts');
    return unwrapFinanceSuccess<PriceAlert[]>(res);
}

export async function createPriceAlert(payload: PriceAlertCreatePayload): Promise<PriceAlert> {
    const res = await financeClient.post('/api/me/price-alerts', payload);
    return unwrapFinanceSuccess<PriceAlert>(res);
}

export async function deletePriceAlert(id: number): Promise<void> {
    await financeClient.delete(`/api/me/price-alerts/${id}`);
}
