import type {
    ManualPortfolioInsights,
    ManualPortfolioView,
    ManualSummary,
    PortfolioInsightItem,
} from '../types/manualPortfolio';

export type InsightSeverity = 'info' | 'attention' | 'risk' | 'positive';

export type SmartInsightCard = {
    id: string;
    message: string;
    severity: InsightSeverity;
};

export type HealthScoreView = {
    score: number;
    labelDefault: string;
    fromBackend: boolean;
};

export type SoldScenarioSummary = {
    totalSellPnl: number;
    totalMissed: number;
    best: { symbol: string; pnl: number } | null;
    worst: { symbol: string; pnl: number } | null;
};

export type RadarRiskFacts = {
    topWeightSymbol: string | null;
    topWeightPct: number | null;
    biggestLoss: { label: string; pnlTry: number } | null;
    strongestContribution: { label: string; pnlTry: number } | null;
    riskiestCategory: { label: string; weightPct: number } | null;
};

type DistRow = { name: string; typeKey: string; value: number };

type PnlRankRow = { key: string; label: string; pnlTry: number };

type Translate = (key: string, defaultText: string) => string;

export function isFiniteNum(v: unknown): v is number {
    return typeof v === 'number' && Number.isFinite(v);
}

export function clampScore(n: number): number {
    return Math.max(0, Math.min(100, Math.round(n)));
}

export function healthScoreBand(score: number): 'strong' | 'balanced' | 'caution' | 'risky' {
    if (score >= 80) return 'strong';
    if (score >= 60) return 'balanced';
    if (score >= 40) return 'caution';
    return 'risky';
}

export function healthBandLabel(band: ReturnType<typeof healthScoreBand>, t: Translate): string {
    switch (band) {
        case 'strong':
            return t('portfolio.healthBandStrong', 'Güçlü');
        case 'balanced':
            return t('portfolio.healthBandBalanced', 'Dengeli');
        case 'caution':
            return t('portfolio.healthBandCaution', 'Dikkat');
        default:
            return t('portfolio.healthBandRisky', 'Riskli');
    }
}

export function computeFallbackHealthScore(
    openPositions: ManualPortfolioView[],
    summary: ManualSummary | undefined,
    distributionByAsset: DistRow[],
    distributionByCategory: DistRow[],
): number {
    let score = 100;
    const openTotal = openPositions.reduce((s, p) => s + Math.max(0, Number(p.currentValue ?? 0)), 0);
    if (openTotal > 0 && distributionByAsset.length > 0) {
        const maxAsset = Math.max(...distributionByAsset.map((d) => (d.value / openTotal) * 100));
        if (maxAsset > 60) score -= 20;
    }
    const catTotal = distributionByCategory.reduce((s, d) => s + d.value, 0);
    if (catTotal > 0 && distributionByCategory.length > 0) {
        const maxCat = Math.max(...distributionByCategory.map((d) => (d.value / catTotal) * 100));
        if (maxCat > 70) score -= 15;
    }
    if (openPositions.length > 0) {
        const losers = openPositions.filter((p) => Number(p.unrealizedProfit ?? 0) < 0).length;
        if (losers / openPositions.length > 0.5) score -= 15;
    }
    const totalReturn = Number(summary?.totalNominalReturnPct ?? 0);
    if (Number.isFinite(totalReturn) && totalReturn < 0) score -= 20;
    return clampScore(score);
}

export function resolveHealthScore(
    insights: ManualPortfolioInsights | undefined,
    fallbackScore: number,
    t: Translate,
): HealthScoreView {
    const backendScore = insights?.healthScore?.score;
    const score = isFiniteNum(backendScore) ? clampScore(backendScore) : fallbackScore;
    const band = healthScoreBand(score);
    return {
        score,
        labelDefault: healthBandLabel(band, t),
        fromBackend: isFiniteNum(backendScore),
    };
}

export function buildConcentrationRiskLine(
    topSymbol: string | null,
    topWeightPct: number | null,
    t: Translate,
): string {
    if (!topSymbol || topWeightPct == null || !Number.isFinite(topWeightPct)) {
        return t('portfolio.concentrationBalanced', 'Varlık dağılımı dengeli görünüyor.');
    }
    const pct = topWeightPct.toLocaleString('tr-TR', { maximumFractionDigits: 1 });
    if (topWeightPct > 70) {
        return t(
            'portfolio.concentrationCritical',
            'Kritik yoğunlaşma: {symbol} portföyün %{weight}’ini oluşturuyor.',
        )
            .replace('{symbol}', topSymbol)
            .replace('{weight}', pct);
    }
    if (topWeightPct > 50) {
        return t(
            'portfolio.concentrationWarning',
            'Yoğunlaşma riski: {symbol} portföyün %{weight}’ini oluşturuyor.',
        )
            .replace('{symbol}', topSymbol)
            .replace('{weight}', pct);
    }
    return t('portfolio.concentrationBalanced', 'Varlık dağılımı dengeli görünüyor.');
}

function mapBackendSeverity(uiSeverity: string | undefined): InsightSeverity {
    const s = (uiSeverity ?? '').toUpperCase();
    if (s === 'CRITICAL' || s === 'RISK') return 'risk';
    if (s === 'WARNING' || s === 'ATTENTION') return 'attention';
    if (s === 'POSITIVE' || s === 'SUCCESS') return 'positive';
    return 'info';
}

function mapBackendInsight(item: PortfolioInsightItem, index: number): SmartInsightCard {
    return {
        id: `backend-${item.type}-${index}`,
        message: item.message,
        severity: mapBackendSeverity(item.uiSeverity),
    };
}

export function buildRuleBasedInsightCards(
    openPositions: ManualPortfolioView[],
    soldPositions: ManualPortfolioView[],
    summary: ManualSummary | undefined,
    insights: ManualPortfolioInsights | undefined,
    topWeightPct: number | null,
    topSymbol: string | null,
    t: Translate,
): SmartInsightCard[] {
    const cards: SmartInsightCard[] = [];

    if (topWeightPct != null && topWeightPct > 50 && topSymbol) {
        cards.push({
            id: 'concentration',
            message: buildConcentrationRiskLine(topSymbol, topWeightPct, t),
            severity: topWeightPct > 70 ? 'risk' : 'attention',
        });
    }

    const openByPnl = [...openPositions]
        .map((p) => ({ sym: p.symbol, pnl: Number(p.unrealizedProfit ?? NaN) }))
        .filter((x) => Number.isFinite(x.pnl));
    const worstOpen = openByPnl.filter((x) => x.pnl < 0).sort((a, b) => a.pnl - b.pnl)[0];
    if (worstOpen) {
        cards.push({
            id: 'worst-loss',
            message: t('portfolio.insightWorstLoss', 'En büyük zarar eden açık pozisyon: {symbol}.').replace(
                '{symbol}',
                worstOpen.sym,
            ),
            severity: 'risk',
        });
    }
    const bestOpen = openByPnl.filter((x) => x.pnl > 0).sort((a, b) => b.pnl - a.pnl)[0];
    if (bestOpen) {
        cards.push({
            id: 'best-gain',
            message: t('portfolio.insightBestGain', 'En büyük kâr eden açık pozisyon: {symbol}.').replace(
                '{symbol}',
                bestOpen.sym,
            ),
            severity: 'positive',
        });
    }

    const nominal = Number(summary?.totalNominalProfit ?? 0);
    const realAvailable = insights?.summary?.realReturnAvailable === true;
    if (Number.isFinite(nominal) && nominal > 0 && !realAvailable) {
        cards.push({
            id: 'inflation-check',
            message: t(
                'portfolio.insightInflationCheck',
                'Nominal getiri pozitif; reel getiri için TÜFE verisi kontrol edilmeli.',
            ),
            severity: 'attention',
        });
    }

    const missedSold = soldPositions
        .filter((p) => Number(p.missedProfit ?? 0) > 0)
        .sort((a, b) => Number(b.missedProfit ?? 0) - Number(a.missedProfit ?? 0));
    if (missedSold.length > 0) {
        const top = missedSold[0]!;
        cards.push({
            id: 'post-sell-miss',
            message: t(
                'portfolio.insightPostSellMiss',
                'Satış sonrası fırsat: {symbol} için kaçırılan fırsat öne çıkıyor.',
            ).replace('{symbol}', top.symbol),
            severity: 'attention',
        });
    }

    return cards;
}

export function pickSmartInsightCards(
    insights: ManualPortfolioInsights | undefined,
    ruleBased: SmartInsightCard[],
    max = 4,
): SmartInsightCard[] {
    const backend = (insights?.insights ?? []).map(mapBackendInsight);
    const seen = new Set<string>();
    const out: SmartInsightCard[] = [];
    for (const c of [...backend, ...ruleBased]) {
        const key = c.message.trim();
        if (!key || seen.has(key)) continue;
        seen.add(key);
        out.push(c);
        if (out.length >= max) break;
    }
    return out;
}

export function buildSoldScenarioSummary(soldPositions: ManualPortfolioView[]): SoldScenarioSummary | null {
    if (soldPositions.length === 0) return null;
    let totalSellPnl = 0;
    let totalMissed = 0;
    let best: { symbol: string; pnl: number } | null = null;
    let worst: { symbol: string; pnl: number } | null = null;

    for (const p of soldPositions) {
        const pnl = Number(p.realizedProfit ?? NaN);
        if (Number.isFinite(pnl)) {
            totalSellPnl += pnl;
            if (!best || pnl > best.pnl) best = { symbol: p.symbol, pnl };
            if (!worst || pnl < worst.pnl) worst = { symbol: p.symbol, pnl };
        }
        const miss = Number(p.missedProfit ?? NaN);
        if (Number.isFinite(miss)) totalMissed += miss;
    }

    return { totalSellPnl, totalMissed, best, worst };
}

export function buildRadarRiskFacts(
    pnlRankRows: { pos: PnlRankRow[]; neg: PnlRankRow[] },
    distributionByCategory: DistRow[],
    topSymbol: string | null,
    topWeightPct: number | null,
): RadarRiskFacts {
    const catTotal = distributionByCategory.reduce((s, d) => s + d.value, 0);
    let riskiestCategory: { label: string; weightPct: number } | null = null;
    if (catTotal > 0) {
        const top = [...distributionByCategory].sort((a, b) => b.value - a.value)[0];
        if (top) {
            riskiestCategory = { label: top.name, weightPct: (top.value / catTotal) * 100 };
        }
    }
    const biggestLoss = pnlRankRows.neg[0] ? { label: pnlRankRows.neg[0].label, pnlTry: pnlRankRows.neg[0].pnlTry } : null;
    const strongestContribution = pnlRankRows.pos[0]
        ? { label: pnlRankRows.pos[0].label, pnlTry: pnlRankRows.pos[0].pnlTry }
        : null;
    return {
        topWeightSymbol: topSymbol,
        topWeightPct,
        biggestLoss,
        strongestContribution,
        riskiestCategory,
    };
}

export function severityLabel(severity: InsightSeverity, t: Translate): string {
    switch (severity) {
        case 'risk':
            return t('portfolio.severityRisk', 'Risk');
        case 'attention':
            return t('portfolio.severityAttention', 'Dikkat');
        case 'positive':
            return t('portfolio.severityPositive', 'Olumlu');
        default:
            return t('portfolio.severityInfo', 'Bilgi');
    }
}
