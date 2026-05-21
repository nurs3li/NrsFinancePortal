import type {
    AiAnalysisHistoryItem,
    AiAnalysisResult,
    AiUsageSummary,
    AssetAiAnalysisRequest,
    CreateTargetPortfolioPlanPayload,
    CurrentPortfolioAiAnalysisRequest,
    TargetPortfolioPlan,
} from '../types/portfolioAi';

const STORAGE_KEY = 'nrs-portfolio-ai-mock-v1';

type Store = {
    usage: AiUsageSummary;
    history: AiAnalysisHistoryItem[];
    results: Record<string, AiAnalysisResult>;
    plans: TargetPortfolioPlan[];
};

function todayYmd(): string {
    return new Date().toISOString().slice(0, 10);
}

function defaultStore(): Store {
    return {
        usage: {
            dailyLimit: 1,
            usedToday: 0,
            remainingToday: 1,
            lastAnalysisAt: null,
            targetPlanCount: 0,
            averageRiskScore: null,
        },
        history: [],
        results: {},
        plans: [],
    };
}

function load(): Store {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (!raw) return defaultStore();
        const parsed = JSON.parse(raw) as Store;
        const day = todayYmd();
        const lastDay = parsed.usage.lastAnalysisAt?.slice(0, 10);
        if (lastDay !== day) {
            parsed.usage.usedToday = 0;
            parsed.usage.remainingToday = parsed.usage.dailyLimit;
        }
        parsed.usage.targetPlanCount = parsed.plans.length;
        return parsed;
    } catch {
        return defaultStore();
    }
}

function save(store: Store): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(store));
}

function uid(prefix: string): string {
    return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function avgRisk(plans: TargetPortfolioPlan[], history: AiAnalysisHistoryItem[]): number | null {
    const scores = [
        ...plans.map((p) => p.riskScore).filter((n): n is number => n != null),
        ...history.map((h) => h.riskScore).filter((n) => Number.isFinite(n)),
    ];
    if (scores.length === 0) return null;
    return Math.round(scores.reduce((a, b) => a + b, 0) / scores.length);
}

type OpenPositionCtx = { symbol: string; weightPct: number };

function buildPortfolioResult(
    req: CurrentPortfolioAiAnalysisRequest,
    ctx: { healthScore: number | null; topSymbol: string | null; openPositions?: OpenPositionCtx[] },
): AiAnalysisResult {
    const base = ctx.healthScore ?? 62;
    const portfolioScore = Math.min(100, Math.max(35, base + (req.riskProfile === 'LOW' ? 5 : req.riskProfile === 'AGGRESSIVE' ? -8 : 0)));
    const riskScore = Math.min(100, Math.max(20, 100 - portfolioScore + 12));
    return {
        id: uid('pf-ai'),
        kind: 'PORTFOLIO',
        title: req.title?.trim() || 'Mevcut portföy AI değerlendirmesi',
        createdAt: new Date().toISOString(),
        portfolioScore,
        riskScore,
        confidenceLevel: req.detailLevel === 'DETAILED' ? 'HIGH' : 'MEDIUM',
        concentrationRisk:
            ctx.topSymbol != null
                ? `En büyük pozisyon (${ctx.topSymbol}) portföyde belirgin ağırlık oluşturuyor; dağılım etkisini izlemek faydalı olabilir.`
                : 'Yoğunlaşma düşük görünüyor; yine de tek varlık payını periyodik gözden geçirmeniz önerilir.',
        summary:
            'Portföyünüzün genel yapısı, nominal ve reel getiri bağlamı ile birlikte değerlendirildi. Sonuçlar karar destek amaçlıdır; işlem talimatı içermez.',
        keyFindings: [
            req.includeInflation
                ? 'Reel getiri / enflasyon etkisi senaryoda dikkate alındı.'
                : 'Enflasyon etkisi bu çalışmada kapalı bırakıldı.',
            req.includeAllocation ? 'Varlık dağılımı portföy puanını etkileyen ana faktörlerden biri.' : 'Dağılım analizi seçilmedi.',
            req.includeMacro ? 'Makro bağlam (faiz/enflasyon) yorumda referans alındı.' : 'Makro katmanı dahil edilmedi.',
            req.includeNews ? 'Haber akışı volatilite riski için izleme notu üretildi.' : 'Haber etkisi dahil edilmedi.',
        ],
        assetComments: (ctx.openPositions ?? []).slice(0, 12).map((p, i) => ({
            symbol: p.symbol.toUpperCase(),
            role: i === 0 ? 'Çekirdek' : p.weightPct >= 10 ? 'Tamamlayıcı' : 'Uydu',
            riskComment:
                p.weightPct >= 25
                    ? 'Yüksek ağırlık portföy volatilitesine duyarlılığı artırabilir.'
                    : 'Risk profili portföy ortalamasına yakın seyrediyor.',
            allocationEffect:
                p.weightPct >= 15
                    ? 'Dağılım üzerinde belirgin etki; yeniden dengeleme senaryosu izlenebilir.'
                    : 'Dağılım etkisi sınırlı; çeşitlendirme açısından destekleyici rol.',
            watchNote: 'Fiyat, makro ve haber akışı için periyodik izleme önerilir.',
        })),
        scenarioComment:
            req.analysisType === 'SELL_ALL'
                ? 'Tamamını satma senaryosu, mevcut dağılım ve risk profilinize göre likidite ve yoğunlaşma etkisini vurgular.'
                : 'Seçilen tutma senaryosu, kısa vadede dalgalanma ve dağılım uyumunu karar destek çerçevesinde özetler.',
    };
}

function buildAssetResult(req: AssetAiAnalysisRequest): AiAnalysisResult {
    const sym = req.symbol.toUpperCase();
    return {
        id: uid('as-ai'),
        kind: 'ASSET',
        title: `${sym} — varlık analizi`,
        createdAt: new Date().toISOString(),
        symbol: sym,
        portfolioScore: 58,
        riskScore: 64,
        confidenceLevel: 'MEDIUM',
        concentrationRisk: 'Tekil varlık portföy payına göre rol ve risk yorumu üretildi.',
        summary: `${sym} için seçilen amaç ve vade çerçevesinde karar destek özeti oluşturuldu.`,
        keyFindings: [
            `Amaç: ${req.intent}`,
            `Vade: ${req.horizon}`,
            'Pozisyon rolü ve dağılım etkisi tabloda ayrıca sunulur.',
        ],
        assetComments: [
            {
                symbol: sym,
                role: 'Portföydeki rol bağlamında değerlendirme',
                riskComment: 'Volatilite ve likidite profili risk skoruna yansıtıldı.',
                allocationEffect: 'Hedef ağırlık değişikliği portföy dengesini etkileyebilir.',
                watchNote: 'Fiyat ve makro gelişmeler için izleme notu önerilir.',
            },
        ],
        scenarioComment: 'Senaryo uyumu, seçilen vade ve risk profili ile karşılaştırıldı.',
    };
}

function buildTargetResult(plan: TargetPortfolioPlan): AiAnalysisResult {
    const total = plan.lines.reduce((s, l) => s + l.targetWeightPct, 0);
    const planScore = Math.min(100, Math.max(40, 100 - Math.abs(100 - total) * 2));
    const riskScore = plan.riskProfile === 'LOW' ? 38 : plan.riskProfile === 'AGGRESSIVE' ? 72 : 52;
    return {
        id: uid('tp-ai'),
        kind: 'TARGET_PLAN',
        title: `${plan.name} — hedef portföy analizi`,
        createdAt: new Date().toISOString(),
        planId: plan.id,
        portfolioScore: planScore,
        riskScore,
        confidenceLevel: 'HIGH',
        concentrationRisk:
            total > 55
                ? 'Hedef ağırlıklar yoğunlaşma riski taşıyabilir; üst sınır kontrolü önerilir.'
                : 'Hedef dağılım risk profili ile genel uyumlu görünüyor.',
        summary: 'Hedef portföy planı; hedefe mesafe ve uygunluk skoru ile birlikte yorumlandı.',
        keyFindings: [
            `Toplam hedef ağırlık: %${total.toFixed(1)}`,
            'Hedef fiyatlar senaryo uyum skoruna dahil edildi.',
            'Minimum tutma planı süreklilik açısından değerlendirildi.',
        ],
        assetComments: plan.lines.slice(0, 6).map((l) => ({
            symbol: l.symbol,
            role: `İşlem tipi: ${l.actionType}`,
            riskComment: 'Risk yorumu hedef ağırlık ve fiyat mesafesine göre üretildi.',
            allocationEffect: `Hedef ağırlık %${l.targetWeightPct.toFixed(1)}`,
            watchNote: l.userNote || 'İzleme notu eklenmedi.',
        })),
        scenarioComment: 'Senaryo uyum skoru, plan amacı ve risk profili ile karşılaştırıldı.',
    };
}

export const portfolioAiMockStore = {
    getUsage(): AiUsageSummary {
        const s = load();
        s.usage.targetPlanCount = s.plans.length;
        s.usage.averageRiskScore = avgRisk(s.plans, s.history);
        return { ...s.usage };
    },

    getHistory() {
        return [...load().history].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    },

    getPlans(): TargetPortfolioPlan[] {
        return [...load().plans].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    },

    getResult(id: string): AiAnalysisResult | null {
        return load().results[id] ?? null;
    },

    consumeDailyCredit(): boolean {
        const s = load();
        if (s.usage.remainingToday <= 0) return false;
        s.usage.usedToday += 1;
        s.usage.remainingToday = Math.max(0, s.usage.dailyLimit - s.usage.usedToday);
        s.usage.lastAnalysisAt = new Date().toISOString();
        save(s);
        return true;
    },

    addPortfolioAnalysis(
        req: CurrentPortfolioAiAnalysisRequest,
        ctx: { healthScore: number | null; topSymbol: string | null; openPositions?: OpenPositionCtx[] },
    ) {
        const s = load();
        const result = buildPortfolioResult(req, ctx);
        s.results[result.id] = result;
        s.history.unshift({
            id: result.id,
            kind: 'PORTFOLIO',
            createdAt: result.createdAt,
            title: result.title,
            portfolioScore: result.portfolioScore,
            riskScore: result.riskScore,
            summarySnippet: result.summary.slice(0, 120),
        });
        save(s);
        return result;
    },

    addAssetAnalysis(req: AssetAiAnalysisRequest) {
        const s = load();
        const result = buildAssetResult(req);
        s.results[result.id] = result;
        s.history.unshift({
            id: result.id,
            kind: 'ASSET',
            createdAt: result.createdAt,
            title: result.title,
            portfolioScore: result.portfolioScore,
            riskScore: result.riskScore,
            summarySnippet: result.summary.slice(0, 120),
            symbol: result.symbol ?? undefined,
        });
        save(s);
        return result;
    },

    addPlan(payload: CreateTargetPortfolioPlanPayload): TargetPortfolioPlan {
        const s = load();
        const plan: TargetPortfolioPlan = {
            id: uid('plan'),
            name: payload.name,
            createdAt: new Date().toISOString(),
            targetDate: payload.targetDate,
            minHoldingPeriod: payload.minHoldingPeriod,
            riskProfile: payload.riskProfile,
            purpose: payload.purpose,
            basis: payload.basis,
            status: 'DRAFT',
            lines: payload.lines.map((l) => ({ ...l, id: uid('line') })),
            planScore: null,
            riskScore: null,
            scenarioFitScore: null,
        };
        s.plans.unshift(plan);
        s.usage.targetPlanCount = s.plans.length;
        save(s);
        return plan;
    },

    analyzePlan(planId: string): AiAnalysisResult | null {
        const s = load();
        const plan = s.plans.find((p) => p.id === planId);
        if (!plan) return null;
        const result = buildTargetResult(plan);
        plan.status = 'ANALYZED';
        plan.planScore = result.portfolioScore;
        plan.riskScore = result.riskScore;
        plan.scenarioFitScore = Math.round((result.portfolioScore + (100 - result.riskScore)) / 2);
        s.results[result.id] = result;
        s.history.unshift({
            id: result.id,
            kind: 'TARGET_PLAN',
            createdAt: result.createdAt,
            title: result.title,
            portfolioScore: result.portfolioScore,
            riskScore: result.riskScore,
            summarySnippet: result.summary.slice(0, 120),
        });
        save(s);
        return result;
    },

    archivePlan(planId: string): void {
        const s = load();
        const p = s.plans.find((x) => x.id === planId);
        if (p) p.status = 'ARCHIVED';
        save(s);
    },
};
