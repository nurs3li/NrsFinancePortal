export const manualPortfolioKeys = {
    all: ['manualPortfolio'] as const,
    positions: () => [...manualPortfolioKeys.all, 'positions'] as const,
    summary: () => [...manualPortfolioKeys.all, 'summary'] as const,
    analysis: (id: number) => [...manualPortfolioKeys.all, 'analysis', id] as const,
};
