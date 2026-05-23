import { useQuery } from '@tanstack/react-query';
import { financeClient } from '../api/client';
import type { ViopFxRates } from '../utils/viopPositionMetrics';
import { unwrapFinanceSuccess } from '../services/manualPortfolioApi';

type LatestPricing = {
    latest?: {
        doviz?: Record<string, { buy?: number; sell?: number; buyPrice?: number; sellPrice?: number }>;
    };
};

function midFx(row: { buy?: number; sell?: number; buyPrice?: number; sellPrice?: number } | undefined): number | null {
    if (!row) return null;
    const buy = row.buy ?? row.buyPrice;
    const sell = row.sell ?? row.sellPrice;
    if (buy != null && sell != null && buy > 0 && sell > 0) return (buy + sell) / 2;
    if (buy != null && buy > 0) return buy;
    if (sell != null && sell > 0) return sell;
    return null;
}

export function useViopFxRates(enabled = true) {
    return useQuery({
        queryKey: ['viop', 'fx-rates'],
        queryFn: async (): Promise<ViopFxRates> => {
            const res = await financeClient.get<LatestPricing>('/api/market/dashboard');
            const data = unwrapFinanceSuccess<LatestPricing>(res);
            const fx = data?.latest?.doviz ?? {};
            return {
                usdTry: midFx(fx.USDTRY),
                eurTry: midFx(fx.EURTRY),
            };
        },
        enabled,
        staleTime: 30_000,
    });
}
