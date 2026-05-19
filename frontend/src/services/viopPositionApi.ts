import { financeClient } from '../api/client';
import { unwrapFinanceSuccess } from './manualPortfolioApi';
import type { PositionHistoricalPriceResolve } from '../types/historicalPriceResolve';
import type {
    ManualViopPosition,
    ManualViopPositionClosePayload,
    ManualViopPositionCreatePayload,
    ViopPositionSummary,
} from '../types/viopPosition';

export async function listViopPositions(): Promise<ManualViopPosition[]> {
    const res = await financeClient.get('/api/me/viop-positions');
    return unwrapFinanceSuccess<ManualViopPosition[]>(res);
}

export async function getViopSummary(): Promise<ViopPositionSummary> {
    const res = await financeClient.get('/api/me/viop-positions/summary');
    return unwrapFinanceSuccess<ViopPositionSummary>(res);
}

export async function createViopPosition(payload: ManualViopPositionCreatePayload): Promise<ManualViopPosition> {
    const res = await financeClient.post('/api/me/viop-positions', payload);
    return unwrapFinanceSuccess<ManualViopPosition>(res);
}

export async function updateViopPosition(
    id: number,
    payload: ManualViopPositionCreatePayload,
): Promise<ManualViopPosition> {
    const res = await financeClient.put(`/api/me/viop-positions/${id}`, payload);
    return unwrapFinanceSuccess<ManualViopPosition>(res);
}

export async function closeViopPosition(id: number, payload: ManualViopPositionClosePayload): Promise<ManualViopPosition> {
    const res = await financeClient.post(`/api/me/viop-positions/${id}/close`, payload);
    return unwrapFinanceSuccess<ManualViopPosition>(res);
}

export async function deleteViopPosition(id: number): Promise<void> {
    await financeClient.delete(`/api/me/viop-positions/${id}`);
}

export async function resolveViopHistoricalPrice(
    symbol: string,
    date: string,
): Promise<PositionHistoricalPriceResolve> {
    const res = await financeClient.get('/api/me/viop-positions/resolve-price', {
        params: { symbol, date },
    });
    return unwrapFinanceSuccess<PositionHistoricalPriceResolve>(res);
}
