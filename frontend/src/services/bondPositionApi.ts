import { financeClient } from '../api/client';
import { unwrapFinanceSuccess } from './manualPortfolioApi';
import type { PositionHistoricalPriceResolve } from '../types/historicalPriceResolve';
import type {
    BondPositionSummary,
    ManualBondPosition,
    ManualBondPositionCreatePayload,
    ManualBondPositionSellPayload,
    ViopBondCombinedSummary,
} from '../types/bondPosition';

export async function listBondPositions(): Promise<ManualBondPosition[]> {
    const res = await financeClient.get('/api/me/bond-positions');
    return unwrapFinanceSuccess<ManualBondPosition[]>(res);
}

export async function getBondSummary(): Promise<BondPositionSummary> {
    const res = await financeClient.get('/api/me/bond-positions/summary');
    return unwrapFinanceSuccess<BondPositionSummary>(res);
}

export async function getViopBondCombinedSummary(): Promise<ViopBondCombinedSummary> {
    const res = await financeClient.get('/api/me/viop-bond-analysis/summary');
    return unwrapFinanceSuccess<ViopBondCombinedSummary>(res);
}

export async function createBondPosition(payload: ManualBondPositionCreatePayload): Promise<ManualBondPosition> {
    const res = await financeClient.post('/api/me/bond-positions', payload);
    return unwrapFinanceSuccess<ManualBondPosition>(res);
}

export async function updateBondPosition(
    id: number,
    payload: ManualBondPositionCreatePayload,
): Promise<ManualBondPosition> {
    const res = await financeClient.put(`/api/me/bond-positions/${id}`, payload);
    return unwrapFinanceSuccess<ManualBondPosition>(res);
}

export async function sellBondPosition(id: number, payload: ManualBondPositionSellPayload): Promise<ManualBondPosition> {
    const res = await financeClient.post(`/api/me/bond-positions/${id}/sell`, payload);
    return unwrapFinanceSuccess<ManualBondPosition>(res);
}

export async function deleteBondPosition(id: number): Promise<void> {
    await financeClient.delete(`/api/me/bond-positions/${id}`);
}

export async function resolveBondHistoricalPrice(
    symbol: string,
    date: string,
): Promise<PositionHistoricalPriceResolve> {
    const res = await financeClient.get('/api/me/bond-positions/resolve-price', {
        params: { symbol, date },
    });
    return unwrapFinanceSuccess<PositionHistoricalPriceResolve>(res);
}
