import type { MarketDashboard, TabId } from './marketTypes';

export function tabToAssetClass(tab: TabId): string {
    switch (tab) {
        case 'doviz':
            return 'FX';
        case 'crypto':
            return 'CRYPTO';
        case 'metals':
            return 'METAL';
        case 'funds':
            return 'FUND';
        case 'equity':
            return 'STOCK';
        default:
            return 'FX';
    }
}

export function sparklineClosesFor(
    dashboard: MarketDashboard | undefined,
    tab: TabId,
    symbol: string,
): number[] | undefined {
    if (!dashboard) return undefined;
    const ac = tabToAssetClass(tab);
    const row = dashboard.sparklines.find((s) => s.assetClass === ac && s.symbol === symbol);
    return row?.closes?.map((c) => Number(c)) ?? undefined;
}

export function volatilityMap(dashboard: MarketDashboard | undefined): Map<string, number> {
    const m = new Map<string, number>();
    if (!dashboard) return m;
    for (const v of dashboard.volatility) {
        m.set(`${v.assetClass}:${v.symbol}`, v.dailyVolatility);
    }
    return m;
}

export function volatilityForSymbol(
    dashboard: MarketDashboard | undefined,
    tab: TabId,
    symbol: string,
): number {
    const ac = tabToAssetClass(tab);
    return volatilityMap(dashboard).get(`${ac}:${symbol}`) ?? 0;
}
