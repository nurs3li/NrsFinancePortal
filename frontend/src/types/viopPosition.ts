export type ViopCategory = 'FX' | 'INDEX' | 'COMMODITY' | 'EQUITY';
export type ViopDirection = 'LONG' | 'SHORT';
export type ViopPositionStatus = 'OPEN' | 'CLOSED' | 'DELETED';
export type ViopCloseReason = 'MANUAL_CLOSE' | 'TAKE_PROFIT' | 'STOP_LOSS' | 'EXPIRY';

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
    closeFee?: number | null;
    closeReason?: ViopCloseReason | null;
    realizedPnl?: number | null;
    realizedReturnPercent?: number | null;
    unrealizedPnl?: number | null;
    riskExposure?: number | null;
    netFinancialEffect?: number | null;
    daysToExpiry?: number | null;
    note?: string | null;
    quoteCurrency?: string | null;
    riskExposureNative?: number | null;
    unrealizedPnlNative?: number | null;
    leverage?: number | null;
    marginRatio?: number | null;
    pnlToMarginRatio?: number | null;
    missingFxRate?: boolean;
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
    portfolioLeverage?: number | null;
    marginRatio?: number | null;
    pnlToMarginRatio?: number | null;
    hasMissingFxRate?: boolean;
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
    fee?: number;
    closeReason?: ViopCloseReason;
    note?: string;
};
