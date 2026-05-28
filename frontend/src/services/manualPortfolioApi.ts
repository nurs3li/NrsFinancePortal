import { type AxiosResponse } from 'axios';
import { readApiError, unwrapApiSuccess } from '../api/envelope';
import { financeClient } from '../api/client';
import type {
    ManualPortfolioAnalysis,
    ManualPortfolioClosePayload,
    ManualPortfolioCreatePayload,
    ManualPortfolioInsights,
    ManualPortfolioTimeseriesPoint,
    ManualPortfolioView,
    ManualResolvedPrice,
    ManualSummary,
    PortfolioInsightNotificationEvaluateResult,
} from '../types/manualPortfolio';

export function unwrapFinanceSuccess<T>(res: AxiosResponse<unknown>): T {
    return unwrapApiSuccess<T>(res.data);
}

export { readApiError as readFinanceApiError };

export async function getManualPositions(): Promise<ManualPortfolioView[]> {
    const res = await financeClient.get('/api/portfolio/manual/me');
    return unwrapFinanceSuccess<ManualPortfolioView[]>(res);
}

export async function getManualSummary(): Promise<ManualSummary> {
    const res = await financeClient.get('/api/portfolio/manual/summary/me');
    return unwrapFinanceSuccess<ManualSummary>(res);
}

export async function getManualTimeseries(fromIsoDate: string, toIsoDate: string): Promise<ManualPortfolioTimeseriesPoint[]> {
    const res = await financeClient.get('/api/portfolio/manual/timeseries/me', {
        params: { from: fromIsoDate, to: toIsoDate },
    });
    return unwrapFinanceSuccess<ManualPortfolioTimeseriesPoint[]>(res);
}

export async function getManualTimeseriesSegment(
    fromIsoDate: string,
    toIsoDate: string,
    mode: 'TYPE' | 'SYMBOL',
    key: string,
): Promise<ManualPortfolioTimeseriesPoint[]> {
    const res = await financeClient.get('/api/portfolio/manual/timeseries/me/segment', {
        params: { from: fromIsoDate, to: toIsoDate, mode, key },
    });
    return unwrapFinanceSuccess<ManualPortfolioTimeseriesPoint[]>(res);
}

export async function getManualSoldHoldHypotheticalTimeseries(
    fromIsoDate: string,
    toIsoDate: string,
): Promise<ManualPortfolioTimeseriesPoint[]> {
    const res = await financeClient.get('/api/portfolio/manual/timeseries/me/sold-hold-hypothetical', {
        params: { from: fromIsoDate, to: toIsoDate },
    });
    return unwrapFinanceSuccess<ManualPortfolioTimeseriesPoint[]>(res);
}

export async function getManualSoldHoldHypotheticalSegment(
    fromIsoDate: string,
    toIsoDate: string,
    mode: 'TYPE' | 'SYMBOL',
    key: string,
): Promise<ManualPortfolioTimeseriesPoint[]> {
    const res = await financeClient.get('/api/portfolio/manual/timeseries/me/sold-hold-hypothetical/segment', {
        params: { from: fromIsoDate, to: toIsoDate, mode, key },
    });
    return unwrapFinanceSuccess<ManualPortfolioTimeseriesPoint[]>(res);
}

export async function getManualSoldLifecyclePnlTimeseries(
    fromIsoDate: string,
    toIsoDate: string,
): Promise<ManualPortfolioTimeseriesPoint[]> {
    const res = await financeClient.get('/api/portfolio/manual/timeseries/me/sold-lifecycle-pnl', {
        params: { from: fromIsoDate, to: toIsoDate },
    });
    return unwrapFinanceSuccess<ManualPortfolioTimeseriesPoint[]>(res);
}

export async function getManualSoldLifecyclePnlSegment(
    fromIsoDate: string,
    toIsoDate: string,
    mode: 'TYPE' | 'SYMBOL',
    key: string,
): Promise<ManualPortfolioTimeseriesPoint[]> {
    const res = await financeClient.get('/api/portfolio/manual/timeseries/me/sold-lifecycle-pnl/segment', {
        params: { from: fromIsoDate, to: toIsoDate, mode, key },
    });
    return unwrapFinanceSuccess<ManualPortfolioTimeseriesPoint[]>(res);
}

export async function getManualOpenUnrealizedPnlTimeseries(
    fromIsoDate: string,
    toIsoDate: string,
): Promise<ManualPortfolioTimeseriesPoint[]> {
    const res = await financeClient.get('/api/portfolio/manual/timeseries/me/open-unrealized-pnl', {
        params: { from: fromIsoDate, to: toIsoDate },
    });
    return unwrapFinanceSuccess<ManualPortfolioTimeseriesPoint[]>(res);
}

export async function getManualRealReturnPnlTimeseries(
    fromIsoDate: string,
    toIsoDate: string,
): Promise<ManualPortfolioTimeseriesPoint[]> {
    const res = await financeClient.get('/api/portfolio/manual/timeseries/me/real-return-pnl', {
        params: { from: fromIsoDate, to: toIsoDate },
    });
    return unwrapFinanceSuccess<ManualPortfolioTimeseriesPoint[]>(res);
}

export async function getManualRealReturnPnlSegment(
    fromIsoDate: string,
    toIsoDate: string,
    mode: 'TYPE' | 'SYMBOL',
    key: string,
): Promise<ManualPortfolioTimeseriesPoint[]> {
    const res = await financeClient.get('/api/portfolio/manual/timeseries/me/real-return-pnl/segment', {
        params: { from: fromIsoDate, to: toIsoDate, mode, key },
    });
    return unwrapFinanceSuccess<ManualPortfolioTimeseriesPoint[]>(res);
}

export async function getManualOpenUnrealizedPnlSegment(
    fromIsoDate: string,
    toIsoDate: string,
    mode: 'TYPE' | 'SYMBOL',
    key: string,
): Promise<ManualPortfolioTimeseriesPoint[]> {
    const res = await financeClient.get('/api/portfolio/manual/timeseries/me/open-unrealized-pnl/segment', {
        params: { from: fromIsoDate, to: toIsoDate, mode, key },
    });
    return unwrapFinanceSuccess<ManualPortfolioTimeseriesPoint[]>(res);
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

export async function getManualPortfolioInsights(): Promise<ManualPortfolioInsights> {
    const res = await financeClient.get('/api/portfolio/manual/insights/me');
    return unwrapFinanceSuccess<ManualPortfolioInsights>(res);
}

export async function evaluatePortfolioInsightNotifications(): Promise<PortfolioInsightNotificationEvaluateResult> {
    const res = await financeClient.post('/api/portfolio/manual/insights/evaluate-notifications/me');
    return unwrapFinanceSuccess<PortfolioInsightNotificationEvaluateResult>(res);
}
