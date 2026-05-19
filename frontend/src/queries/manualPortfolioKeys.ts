export const manualPortfolioKeys = {
    all: ['manualPortfolio'] as const,
    positions: () => [...manualPortfolioKeys.all, 'positions'] as const,
    summary: () => [...manualPortfolioKeys.all, 'summary'] as const,
    analysis: (id: number) => [...manualPortfolioKeys.all, 'analysis', id] as const,
    timeseries: (rangeKey: string) => [...manualPortfolioKeys.all, 'timeseries', rangeKey] as const,
    insights: () => [...manualPortfolioKeys.all, 'insights'] as const,
};
