/** GET /api/portfolio/manual/timeseries/me */
export type ManualPortfolioTimeseriesPoint = {
    date: string;
    openCostBasisTry: number;
    marketValueTry: number | null;
};

export type ManualPositionStatus = 'OPEN' | 'SOLD';

export type ManualPriceSource =
    | 'USER_INPUT'
    | 'MARKET_HISTORY_EXACT'
    | 'MARKET_HISTORY_SAME_DAY_HOURLY'
    | 'MARKET_HISTORY_PREVIOUS_CLOSE'
    | 'MARKET_HISTORY_NEXT_CLOSE'
    | 'NOT_RESOLVED';

export type ManualAssetType = 'BIST' | 'STOCK' | 'CRYPTO' | 'FX' | 'METAL' | 'TR_FUND' | 'FUND';

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
    nominalCost?: number | null;
    exitValue?: number | null;
    nominalProfit?: number | null;
    nominalReturnPct?: number | null;
    inflationFactor?: number | null;
    inflationReturnPct?: number | null;
    inflationAdjustedCost?: number | null;
    realProfit?: number | null;
    realReturnPct?: number | null;
    realReturnAvailable?: boolean;
    realReturnStatus?: ManualPositionRealReturnStatus | string | null;
    cpiStartDate?: string | null;
    cpiEndDate?: string | null;
    cpiStartValue?: number | null;
    cpiEndValue?: number | null;
    calculationEndDate?: string | null;
    calculationMode?: ManualPositionRealReturnCalculationMode | string | null;
};

export type ManualPositionRealReturnStatus =
    | 'BEAT_INFLATION'
    | 'LOST_TO_INFLATION'
    | 'NO_CPI_DATA';

export type ManualPositionRealReturnCalculationMode =
    | 'OPEN_POSITION_MARK_TO_MARKET'
    | 'SOLD_POSITION';

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

export type PortfolioInsightsSummary = {
    openCurrentValue: number;
    closedRealizedValue: number;
    totalEvaluatedValue: number;
    totalInvestedAmount: number;
    nominalReturn: number;
    nominalReturnPct: number | null;
    inflationAdjustedCost: number | null;
    realReturn: number | null;
    realReturnPct: number | null;
    realReturnAvailable: boolean;
    realReturnUnavailableReason: string | null;
};

export type PortfolioConcentrationRisk = {
    topAssetSymbol: string | null;
    topAssetWeightPct: number | null;
    top3WeightPct: number | null;
    riskLevel: string;
    message: string;
};

export type PortfolioHealthScore = {
    score: number;
    level: string;
    summary: string;
    factors: string[];
};

export type PortfolioInsightItem = {
    type: string;
    uiSeverity: string;
    message: string;
};

export type ManualPortfolioInsights = {
    summary: PortfolioInsightsSummary;
    healthScore: PortfolioHealthScore;
    concentrationRisk: PortfolioConcentrationRisk;
    insights: PortfolioInsightItem[];
};

export type PortfolioInsightNotificationEvaluateResult = {
    generatedCount: number;
    generatedTypes: string[];
};

export type ManualPortfolioPageTimeseries = {
    range: string;
    from: string;
    to: string;
    points: ManualPortfolioTimeseriesPoint[];
};

export type ManualPortfolioPageMeta = {
    warmedAt?: string | null;
    pricingAt?: string | null;
    gapFillMs?: number;
    warmStatus?: string;
};

export type ManualPortfolioPage = {
    positions: ManualPortfolioView[];
    summary: ManualSummary;
    insights: ManualPortfolioInsights | null;
    timeseries: ManualPortfolioPageTimeseries;
    meta: ManualPortfolioPageMeta;
};
