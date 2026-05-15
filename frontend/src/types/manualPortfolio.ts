export type ManualPositionStatus = 'OPEN' | 'SOLD';

export type ManualPriceSource =
    | 'USER_INPUT'
    | 'MARKET_HISTORY_EXACT'
    | 'MARKET_HISTORY_PREVIOUS_CLOSE'
    | 'NOT_RESOLVED';

export type ManualAssetType = 'STOCK' | 'CRYPTO' | 'FX' | 'METAL' | 'FUND';

export type ManualResolvedPrice = {
    found: boolean;
    type: string;
    symbol: string;
    requestedDate: string;
    resolvedDate?: string | null;
    price?: number | null;
    source: ManualPriceSource | string;
    currency?: string | null;
    message?: string | null;
};

export type ManualPortfolioView = {
    id: number;
    type: string;
    symbol: string;
    displayName?: string | null;
    quantity: number;
    buyDate: string;
    buyPrice: number | null;
    note?: string | null;
    status: ManualPositionStatus | string;
    buyFee?: number | null;
    sellDate?: string | null;
    sellPrice?: number | null;
    sellFee?: number | null;
    buyPriceSource?: string | null;
    buyPriceResolvedDate?: string | null;
    buyPriceOverride?: boolean;
    sellPriceSource?: string | null;
    sellPriceResolvedDate?: string | null;
    sellPriceOverride?: boolean;
    buyCost?: number | null;
    currentPrice?: number | null;
    currentValue?: number | null;
    unrealizedProfit?: number | null;
    unrealizedReturnPct?: number | null;
    sellProceeds?: number | null;
    realizedProfit?: number | null;
    realizedReturnPct?: number | null;
    holdValueToday?: number | null;
    holdProfitToday?: number | null;
    holdReturnPctToday?: number | null;
    missedProfit?: number | null;
    missedReturnPct?: number | null;
    totalProfit?: number | null;
    totalReturnPct?: number | null;
};

export type ManualSummary = {
    totalPositions: number;
    openPositions: number;
    soldPositions: number;
    totalInvested: number;
    currentOpenValue: number;
    realizedProfit: number;
    unrealizedProfit: number;
    holdValueTodayForSold: number;
    missedProfit: number;
    totalNominalProfit: number;
    totalNominalReturnPct: number;
    bestPositionSymbol: string | null;
    bestPositionReturnPct: number | null;
    biggestMissedOpportunitySymbol: string | null;
    biggestMissedProfit: number | null;
};

export type ManualAnalysisMarkerType = 'BUY' | 'SELL' | 'TODAY';

export type ManualAnalysisMarker = {
    type: ManualAnalysisMarkerType | string;
    date: string;
    price: number | null;
    value: number | null;
};

export type ManualAnalysisChartPoint = {
    date: string;
    price: number | null;
    value: number | null;
};

export type ManualPortfolioAnalysis = {
    position: ManualPortfolioView;
    markers: ManualAnalysisMarker[];
    chartSeries: ManualAnalysisChartPoint[];
};

/** POST /api/portfolio/manual — omit optional fields for backend auto-resolve */
export type ManualPortfolioCreatePayload = {
    type: ManualAssetType | string;
    symbol: string;
    quantity: number;
    buyDate: string;
    buyPrice?: number | null;
    buyPriceOverride?: boolean;
    buyFee?: number | null;
    status?: ManualPositionStatus | string | null;
    sellDate?: string | null;
    sellPrice?: number | null;
    sellPriceOverride?: boolean;
    sellFee?: number | null;
    note?: string | null;
};

export type ManualPortfolioClosePayload = {
    sellDate: string;
    sellPrice?: number | null;
    sellPriceOverride?: boolean;
    sellFee?: number | null;
};
