import { financeClient } from '../api/client';
import {
    getManualPortfolioInsights,
    getManualPositions,
    getManualSummary,
    readFinanceApiError,
    unwrapFinanceSuccess,
} from './manualPortfolioApi';
import type {
    AiAnalysisHistoryFilters,
    AiAnalysisHistoryItem,
    AiAnalysisResult,
    AiUsageSummary,
    AssetAiCommentRow,
    AssetInsightNarrative,
    CurrentPortfolioAiAnalysisRequest,
    DecisionPerspectiveNarrative,
    MacroAndNewsNarrative,
    PortfolioAnalysisType,
    PortfolioContextSnapshot,
    PortfolioOverviewNarrative,
    PortfolioAiEmailDeliverySettings,
    PortfolioAiEmailDeliveryUpsert,
} from '../types/portfolioAi';

type ApiEnvelope<T> = { success?: boolean; data?: T };

type BackendUsage = {
    dailyLimit: number;
    usedToday: number;
    remainingToday: number;
    lastAnalysisAt: string | null;
    lastPortfolioScore?: number | null;
    lastRiskScore?: number | null;
};

type BackendAnalysisRequest = {
    title: string;
    analysisType: string;
    riskProfile: string;
    detailLevel: string;
    includeNews: boolean;
    includeMacro: boolean;
    includeRealReturn: boolean;
};

type BackendAssetComment = {
    symbol: string;
    assetName?: string | null;
    assetClass?: string | null;
    weightPct: number;
    returnPct?: number | null;
    assetScore: number;
    riskScore: number;
    riskLevel: string;
    role: string;
    positiveFactors: string[];
    riskFactors: string[];
    shortComment: string;
    detailComment: string;
};

type BackendOverview = {
    summary?: string;
    currentSituation?: string;
    mainPositive?: string;
    mainRisk?: string;
};

type BackendDecision = {
    scenario?: string;
    comment?: string;
    shortTermView?: string;
    mediumTermView?: string;
    watchPoints?: string[];
};

type BackendMacroNews = {
    summary?: string;
    dataAvailability?: string;
    relevantItems?: string[];
};

type BackendAssetInsight = {
    symbol: string;
    assetName?: string | null;
    assetClass?: string | null;
    weightPct: number;
    returnPct?: number | null;
    role?: string;
    impactOnPortfolio?: string;
    positiveView?: string;
    riskView?: string;
    whatToWatch?: string[];
    shortComment?: string;
    detailComment?: string;
};

type BackendAnalysisResponse = {
    id: string;
    createdAt: string;
    title?: string | null;
    analysisType: string;
    portfolioScore: number;
    riskScore: number;
    confidence: 'LOW' | 'MEDIUM' | 'HIGH';
    concentrationRisk: 'LOW' | 'MEDIUM' | 'HIGH';
    summary: string;
    findings: string[];
    scenarioComment: string;
    assetComments: BackendAssetComment[];
    disclaimer: string;
    source?: string;
    model?: string | null;
    portfolioOverview?: BackendOverview | null;
    decisionPerspective?: BackendDecision | null;
    macroAndNewsImpact?: BackendMacroNews | null;
    assetInsights?: BackendAssetInsight[] | null;
    finalNote?: string | null;
};

type BackendHistoryList = {
    items: Array<{
        id: string;
        createdAt: string;
        analysisType: string;
        title: string;
        portfolioScore: number;
        riskScore: number;
        summary: string;
    }>;
};

function toBackendRequest(payload: CurrentPortfolioAiAnalysisRequest): BackendAnalysisRequest {
    const typeMap: Record<PortfolioAnalysisType, string> = {
        GENERAL: 'GENERAL_REVIEW',
        HOLD_1W: 'ONE_WEEK_HOLD',
        HOLD_1M: 'ONE_MONTH_HOLD',
        HOLD_3M: 'ONE_MONTH_HOLD',
        SELL_ALL: 'SELL_SCENARIO',
    };
    return {
        title: payload.title.trim(),
        analysisType: typeMap[payload.analysisType] ?? 'GENERAL_REVIEW',
        riskProfile: payload.riskProfile,
        detailLevel: payload.detailLevel === 'SHORT' ? 'BRIEF' : 'DETAILED',
        includeNews: payload.includeNews,
        includeMacro: payload.includeMacro,
        includeRealReturn: payload.includeInflation,
    };
}

function mapAssetComment(a: BackendAssetComment, insight?: BackendAssetInsight): AssetAiCommentRow {
    return {
        symbol: a.symbol,
        assetName: a.assetName ?? insight?.assetName ?? null,
        assetClass: a.assetClass ?? insight?.assetClass ?? null,
        weightPct: a.weightPct ?? insight?.weightPct,
        returnPct: a.returnPct ?? insight?.returnPct ?? null,
        assetScore: a.assetScore,
        riskScore: a.riskScore,
        riskLevel: a.riskLevel,
        role: insight?.role ?? a.role,
        positiveFactors: a.positiveFactors ?? (insight?.positiveView ? [insight.positiveView] : []),
        riskFactors: a.riskFactors ?? (insight?.riskView ? [insight.riskView] : []),
        shortComment: insight?.shortComment ?? a.shortComment,
        detailComment: insight?.detailComment ?? a.detailComment,
        impactOnPortfolio: insight?.impactOnPortfolio,
        positiveView: insight?.positiveView,
        riskView: insight?.riskView,
        whatToWatch: insight?.whatToWatch,
        riskComment: a.riskFactors?.[0] ?? insight?.riskView ?? '',
        allocationEffect: a.positiveFactors?.[0] ?? insight?.positiveView ?? '',
        watchNote: insight?.whatToWatch?.[0] ?? a.shortComment ?? '',
    };
}

function mapOverview(o?: BackendOverview | null): PortfolioOverviewNarrative | null {
    if (!o) return null;
    return {
        summary: o.summary,
        currentSituation: o.currentSituation,
        mainPositive: o.mainPositive,
        mainRisk: o.mainRisk,
    };
}

function mapDecision(d?: BackendDecision | null): DecisionPerspectiveNarrative | null {
    if (!d) return null;
    return {
        scenario: d.scenario,
        comment: d.comment,
        shortTermView: d.shortTermView,
        mediumTermView: d.mediumTermView,
        watchPoints: d.watchPoints ?? [],
    };
}

function mapMacro(m?: BackendMacroNews | null): MacroAndNewsNarrative | null {
    if (!m) return null;
    return {
        summary: m.summary,
        dataAvailability: m.dataAvailability as MacroAndNewsNarrative['dataAvailability'],
        relevantItems: m.relevantItems ?? [],
    };
}

function mapInsight(i: BackendAssetInsight): AssetInsightNarrative {
    return {
        symbol: i.symbol,
        assetName: i.assetName ?? null,
        assetClass: i.assetClass ?? null,
        weightPct: i.weightPct,
        returnPct: i.returnPct ?? null,
        role: i.role,
        impactOnPortfolio: i.impactOnPortfolio,
        positiveView: i.positiveView,
        riskView: i.riskView,
        whatToWatch: i.whatToWatch ?? [],
        shortComment: i.shortComment,
        detailComment: i.detailComment,
    };
}

function resolveAnalysisSource(r: BackendAnalysisResponse): 'OPENAI' | 'FALLBACK_RULE_BASED' {
    if (r.source === 'OPENAI' || r.source === 'FALLBACK_RULE_BASED') {
        return r.source;
    }
    const m = r.model ?? '';
    if (m && m !== 'FALLBACK_RULE_BASED') {
        return 'OPENAI';
    }
    return 'FALLBACK_RULE_BASED';
}

function mapAnalysisResponse(r: BackendAnalysisResponse): AiAnalysisResult {
    const insightBySym = new Map(
        (r.assetInsights ?? []).map((i) => [i.symbol.toUpperCase(), i]),
    );
    const overview = mapOverview(r.portfolioOverview);
    const summary =
        overview?.summary?.trim() ||
        r.summary ||
        '';
    return {
        id: r.id,
        kind: 'PORTFOLIO',
        title: r.title?.trim() || historyTitle(r.analysisType),
        createdAt: r.createdAt,
        portfolioScore: r.portfolioScore,
        riskScore: r.riskScore,
        confidenceLevel: r.confidence,
        concentrationRisk: r.concentrationRisk,
        summary,
        keyFindings: r.findings ?? [],
        assetComments: (r.assetComments ?? []).map((a) =>
            mapAssetComment(a, insightBySym.get(a.symbol.toUpperCase())),
        ),
        scenarioComment:
            r.decisionPerspective?.comment?.trim() ||
            r.scenarioComment ||
            '',
        symbol: null,
        planId: null,
        source: resolveAnalysisSource(r),
        model: r.model ?? null,
        portfolioOverview: overview,
        decisionPerspective: mapDecision(r.decisionPerspective),
        macroAndNewsImpact: mapMacro(r.macroAndNewsImpact),
        assetInsights: (r.assetInsights ?? []).map(mapInsight),
        finalNote: r.finalNote ?? r.disclaimer ?? null,
    };
}

function historyTitle(analysisType: string): string {
    switch (analysisType) {
        case 'ONE_WEEK_HOLD':
            return '1 hafta tutma senaryosu';
        case 'ONE_MONTH_HOLD':
            return '1 ay tutma senaryosu';
        case 'SELL_SCENARIO':
            return 'Tamamını satma senaryosu';
        default:
            return 'Mevcut portföy AI değerlendirmesi';
    }
}

function mapUsage(u: BackendUsage): AiUsageSummary {
    return {
        dailyLimit: u.dailyLimit,
        usedToday: u.usedToday,
        remainingToday: u.remainingToday,
        lastAnalysisAt: u.lastAnalysisAt,
        targetPlanCount: 0,
        averageRiskScore: u.lastRiskScore ?? null,
    };
}

function mapHistoryItem(h: BackendHistoryList['items'][0]): AiAnalysisHistoryItem {
    return {
        id: h.id,
        kind: 'PORTFOLIO',
        createdAt: h.createdAt,
        title: h.title,
        portfolioScore: h.portfolioScore,
        riskScore: h.riskScore,
        summarySnippet: h.summary,
        symbol: null,
    };
}

function throwApiError(err: unknown): never {
    const { message } = readFinanceApiError(err);
    throw new Error(message || 'AI_ANALYSIS_FAILED');
}

export async function getAiUsageSummary(): Promise<AiUsageSummary> {
    try {
        const res = await financeClient.get<ApiEnvelope<BackendUsage>>('/api/portfolio/ai/usage/me');
        const data = unwrapFinanceSuccess<BackendUsage>(res);
        return mapUsage(data);
    } catch (err) {
        throwApiError(err);
    }
}

export async function getPortfolioContextSnapshot(): Promise<PortfolioContextSnapshot> {
    const [summary, positions, insights] = await Promise.all([
        getManualSummary(),
        getManualPositions(),
        getManualPortfolioInsights().catch(() => null),
    ]);
    const open = positions.filter((p) => String(p.status).toUpperCase() === 'OPEN');
    const openTotal = open.reduce((s, p) => s + Math.max(0, Number(p.currentValue ?? 0)), 0);
    let largest: { sym: string; w: number } | null = null;
    for (const p of open) {
        const v = Math.max(0, Number(p.currentValue ?? 0));
        if (openTotal <= 0 || v <= 0) continue;
        const w = (100 * v) / openTotal;
        if (!largest || w > largest.w) largest = { sym: p.symbol, w };
    }
    const ins = insights?.summary;
    return {
        totalValueTry: Number(summary.currentOpenValue ?? openTotal),
        nominalReturnPct: ins?.nominalReturnPct ?? summary.totalNominalReturnPct ?? null,
        realReturnPct: ins?.realReturnPct ?? null,
        realReturnAvailable: ins?.realReturnAvailable ?? false,
        largestPositionSymbol: largest?.sym ?? insights?.concentrationRisk?.topAssetSymbol ?? null,
        largestPositionWeightPct: largest?.w ?? insights?.concentrationRisk?.topAssetWeightPct ?? null,
        healthScore: insights?.healthScore?.score ?? null,
        healthLevel: insights?.healthScore?.level ?? null,
        openPositionCount: open.length,
    };
}

/** OpenAI + portföy bağlamı; varsayılan HTTP 45s yetmez */
const PORTFOLIO_AI_HTTP_TIMEOUT_MS =
    Number(import.meta.env.VITE_PORTFOLIO_AI_TIMEOUT_MS) || 120_000;

export async function requestCurrentPortfolioAiAnalysis(
    payload: CurrentPortfolioAiAnalysisRequest,
): Promise<AiAnalysisResult> {
    try {
        const res = await financeClient.post<ApiEnvelope<BackendAnalysisResponse>>(
            '/api/portfolio/ai/analyses/current/me',
            toBackendRequest(payload),
            { timeout: PORTFOLIO_AI_HTTP_TIMEOUT_MS },
        );
        const data = unwrapFinanceSuccess<BackendAnalysisResponse>(res);
        return mapAnalysisResponse(data);
    } catch (err) {
        throwApiError(err);
    }
}

export async function getLatestPortfolioAiAnalysis(): Promise<AiAnalysisResult | null> {
    try {
        const res = await financeClient.get<ApiEnvelope<BackendAnalysisResponse>>(
            '/api/portfolio/ai/analyses/latest/me',
        );
        const data = unwrapFinanceSuccess<BackendAnalysisResponse>(res);
        return mapAnalysisResponse(data);
    } catch (err) {
        const { code } = readFinanceApiError(err);
        if (code === 'AI_ANALYSIS_NOT_FOUND' || code === 'RESOURCE_NOT_FOUND') {
            return null;
        }
        throwApiError(err);
    }
}

export async function getAiAnalysisHistory(
    filters: AiAnalysisHistoryFilters = {},
): Promise<AiAnalysisHistoryItem[]> {
    const range =
        filters.dateRange === '7D' ? '7d' : filters.dateRange === 'ALL' ? 'all' : '30d';
    const q = filters.symbolSearch?.trim() ?? '';
    try {
        const res = await financeClient.get<ApiEnvelope<BackendHistoryList>>(
            '/api/portfolio/ai/analyses/history/me',
            { params: { range, q: q || undefined } },
        );
        const data = unwrapFinanceSuccess<BackendHistoryList>(res);
        return (data.items ?? []).map(mapHistoryItem);
    } catch (err) {
        throwApiError(err);
    }
}

export async function deletePortfolioAiAnalysis(id: string): Promise<void> {
    try {
        const res = await financeClient.delete<ApiEnvelope<null>>(`/api/portfolio/ai/analyses/${id}/me`);
        unwrapFinanceSuccess(res);
    } catch (err) {
        throwApiError(err);
    }
}

export async function getAiAnalysisById(id: string): Promise<AiAnalysisResult | null> {
    try {
        const res = await financeClient.get<ApiEnvelope<BackendAnalysisResponse>>(
            `/api/portfolio/ai/analyses/${id}/me`,
        );
        const data = unwrapFinanceSuccess<BackendAnalysisResponse>(res);
        return mapAnalysisResponse(data);
    } catch (err) {
        const { code } = readFinanceApiError(err);
        if (code === 'AI_ANALYSIS_NOT_FOUND' || code === 'RESOURCE_NOT_FOUND') {
            return null;
        }
        throwApiError(err);
    }
}

type BackendEmailDelivery = {
    enabled: boolean;
    email: string | null;
    frequency: 'WEEKLY' | 'MONTHLY';
    updatedAt: string | null;
};

export async function getPortfolioAiEmailDelivery(): Promise<PortfolioAiEmailDeliverySettings> {
    try {
        const res = await financeClient.get<ApiEnvelope<BackendEmailDelivery>>(
            '/api/portfolio/ai/email-delivery/me',
        );
        const data = unwrapFinanceSuccess<BackendEmailDelivery>(res);
        return {
            enabled: data.enabled,
            email: data.email,
            frequency: data.frequency ?? 'WEEKLY',
            updatedAt: data.updatedAt,
        };
    } catch (err) {
        throwApiError(err);
    }
}

export async function sendPortfolioAiAnalysisReportEmail(analysisId: string): Promise<void> {
    try {
        const res = await financeClient.post<ApiEnvelope<null>>(
            `/api/portfolio/ai/analyses/${encodeURIComponent(analysisId)}/email/me`,
        );
        unwrapFinanceSuccess(res);
    } catch (err) {
        throwApiError(err);
    }
}

export async function upsertPortfolioAiEmailDelivery(
    payload: PortfolioAiEmailDeliveryUpsert,
): Promise<PortfolioAiEmailDeliverySettings> {
    try {
        const res = await financeClient.put<ApiEnvelope<BackendEmailDelivery>>(
            '/api/portfolio/ai/email-delivery/me',
            payload,
        );
        const data = unwrapFinanceSuccess<BackendEmailDelivery>(res);
        return {
            enabled: data.enabled,
            email: data.email,
            frequency: data.frequency ?? 'WEEKLY',
            updatedAt: data.updatedAt,
        };
    } catch (err) {
        throwApiError(err);
    }
}
