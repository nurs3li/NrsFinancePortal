export type ViopCategory = 'FX' | 'INDEX' | 'COMMODITY' | 'EQUITY';
export type ViopDirection = 'LONG' | 'SHORT';
export type ViopPositionStatus = 'OPEN' | 'CLOSED' | 'DELETED';

export type ManualViopPosition = {
    id: number;
    symbol: string;
    displayName?: string | null;
    viopCategory: ViopCategory;
    underlyingSymbol?: string | null;
    direction: ViopDirection;
    contractCount: number;
    entryPrice: number;
    entryDate: string;
    currentPrice?: number | null;
    contractMultiplier: number;
    initialMargin?: number | null;
    expiryDate?: string | null;
    status: ViopPositionStatus;
    closePrice?: number | null;
    closeDate?: string | null;
    unrealizedPnl?: number | null;
    riskExposure?: number | null;
    netFinancialEffect?: number | null;
    daysToExpiry?: number | null;
    note?: string | null;
};

export type ViopPositionSummary = {
    openPositionCount: number;
    totalInitialMargin: number;
    totalUnrealizedPnl: number;
    totalRiskExposure: number;
    longCount: number;
    shortCount: number;
    expiringSoonCount: number;
    netFinancialEffect: number;
    incompleteDataCount: number;
};

export type ManualViopPositionCreatePayload = {
    symbol: string;
    displayName?: string;
    viopCategory: ViopCategory;
    underlyingSymbol?: string;
    direction: ViopDirection;
    contractCount: number;
    entryPrice: number;
    entryDate: string;
    currentPrice?: number;
    contractMultiplier: number;
    initialMargin?: number;
    expiryDate?: string;
    note?: string;
};

export type ManualViopPositionClosePayload = {
    closePrice: number;
    closeDate: string;
};
