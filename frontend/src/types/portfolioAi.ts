export type AiAnalysisKind = 'PORTFOLIO' | 'ASSET' | 'TARGET_PLAN';

export type AiRiskProfile = 'LOW' | 'BALANCED' | 'AGGRESSIVE';

export type AiDetailLevel = 'SHORT' | 'DETAILED';

export type PortfolioAnalysisType =
    | 'GENERAL'
    | 'HOLD_1W'
    | 'HOLD_1M'
    | 'HOLD_3M'
    | 'SELL_ALL';

export type AssetAnalysisIntent = 'HOLDING' | 'CONSIDER_ADD' | 'CONSIDER_REDUCE' | 'INFO_ONLY';

export type AssetHorizon = '1W' | '1M' | '3M' | '6M';

export type TargetPlanStatus = 'DRAFT' | 'ANALYZED' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';

export type TargetPlanPurpose =
    | 'CAPITAL_PRESERVATION'
    | 'BALANCED_GROWTH'
    | 'HIGH_RETURN'
    | 'INFLATION_BEAT';

export type TargetAssetAction = 'KEEP' | 'ADD' | 'REDUCE' | 'REMOVE' | 'WATCH';

export type TargetPlanBasis = 'CURRENT_PORTFOLIO' | 'FROM_SCRATCH';

export type AiUsageSummary = {
    dailyLimit: number;
    usedToday: number;
    remainingToday: number;
    lastAnalysisAt: string | null;
    targetPlanCount: number;
    averageRiskScore: number | null;
};

export type PortfolioContextSnapshot = {
    totalValueTry: number;
    nominalReturnPct: number | null;
    realReturnPct: number | null;
    realReturnAvailable: boolean;
    largestPositionSymbol: string | null;
    largestPositionWeightPct: number | null;
    healthScore: number | null;
    healthLevel: string | null;
    openPositionCount: number;
};

export type PortfolioAiEmailFrequency = 'WEEKLY' | 'MONTHLY';

export type PortfolioAiEmailDeliverySettings = {
    enabled: boolean;
    email: string | null;
    frequency: PortfolioAiEmailFrequency;
    updatedAt: string | null;
};

export type PortfolioAiEmailDeliveryUpsert = {
    enabled: boolean;
    email: string | null;
    frequency: PortfolioAiEmailFrequency;
};

export type CurrentPortfolioAiAnalysisRequest = {
    title: string;
    analysisType: PortfolioAnalysisType;
    riskProfile: AiRiskProfile;
    detailLevel: AiDetailLevel;
    includeNews: boolean;
    includeMacro: boolean;
    includeInflation: boolean;
    includeAllocation: boolean;
};

export type AssetAiAnalysisRequest = {
    symbol: string;
    intent: AssetAnalysisIntent;
    horizon: AssetHorizon;
    riskProfile: AiRiskProfile;
    detailLevel: AiDetailLevel;
};

export type TargetPlanLine = {
    id: string;
    symbol: string;
    assetClass: string;
    currentWeightPct: number;
    targetWeightPct: number;
    minHoldingPeriod: string;
    currentPrice: number | null;
    targetPrice: number | null;
    userNote: string;
    actionType: TargetAssetAction;
};

export type TargetPortfolioPlan = {
    id: string;
    name: string;
    createdAt: string;
    targetDate: string;
    minHoldingPeriod: string;
    riskProfile: AiRiskProfile;
    purpose: TargetPlanPurpose;
    basis: TargetPlanBasis;
    status: TargetPlanStatus;
    lines: TargetPlanLine[];
    planScore: number | null;
    riskScore: number | null;
    scenarioFitScore: number | null;
};

export type CreateTargetPortfolioPlanPayload = {
    name: string;
    targetDate: string;
    minHoldingPeriod: string;
    riskProfile: AiRiskProfile;
    purpose: TargetPlanPurpose;
    basis: TargetPlanBasis;
    lines: Omit<TargetPlanLine, 'id'>[];
};

export type AssetAiCommentRow = {
    symbol: string;
    assetName?: string | null;
    assetClass?: string | null;
    weightPct?: number;
    returnPct?: number | null;
    assetScore?: number;
    riskScore?: number;
    riskLevel?: string;
    role: string;
    positiveFactors?: string[];
    riskFactors?: string[];
    shortComment?: string;
    detailComment?: string;
    impactOnPortfolio?: string;
    positiveView?: string;
    riskView?: string;
    whatToWatch?: string[];
    /** @deprecated legacy mock fields */
    riskComment?: string;
    allocationEffect?: string;
    watchNote?: string;
};

export type PortfolioOverviewNarrative = {
    summary?: string;
    currentSituation?: string;
    mainPositive?: string;
    mainRisk?: string;
};

export type DecisionPerspectiveNarrative = {
    scenario?: string;
    comment?: string;
    shortTermView?: string;
    mediumTermView?: string;
    watchPoints?: string[];
};

export type MacroAndNewsNarrative = {
    summary?: string;
    dataAvailability?: 'AVAILABLE' | 'PARTIAL' | 'NOT_AVAILABLE' | string;
    relevantItems?: string[];
};

export type AssetInsightNarrative = {
    symbol: string;
    assetName?: string | null;
    assetClass?: string | null;
    weightPct?: number;
    returnPct?: number | null;
    role?: string;
    impactOnPortfolio?: string;
    positiveView?: string;
    riskView?: string;
    whatToWatch?: string[];
    shortComment?: string;
    detailComment?: string;
};

export type AiAnalysisResult = {
    id: string;
    kind: AiAnalysisKind;
    title: string;
    createdAt: string;
    portfolioScore: number;
    riskScore: number;
    confidenceLevel: 'LOW' | 'MEDIUM' | 'HIGH';
    concentrationRisk: string;
    summary: string;
    keyFindings: string[];
    assetComments: AssetAiCommentRow[];
    scenarioComment: string;
    symbol?: string | null;
    planId?: string | null;
    /** OPENAI | FALLBACK_RULE_BASED */
    source?: 'OPENAI' | 'FALLBACK_RULE_BASED' | string | null;
    /** OpenAI model adı (ör. gpt-4o-mini); fallback'te genelde FALLBACK_RULE_BASED */
    model?: string | null;
    portfolioOverview?: PortfolioOverviewNarrative | null;
    decisionPerspective?: DecisionPerspectiveNarrative | null;
    macroAndNewsImpact?: MacroAndNewsNarrative | null;
    assetInsights?: AssetInsightNarrative[] | null;
    finalNote?: string | null;
};

export type AiAnalysisHistoryItem = {
    id: string;
    kind: AiAnalysisKind;
    createdAt: string;
    title: string;
    portfolioScore: number;
    riskScore: number;
    summarySnippet: string;
    symbol?: string | null;
};

export type AiAnalysisHistoryFilters = {
    kind?: AiAnalysisKind | 'ALL';
    dateRange?: '7D' | '30D' | 'ALL';
    symbolSearch?: string;
};
