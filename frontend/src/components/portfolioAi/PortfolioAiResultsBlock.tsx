import type { AiAnalysisResult } from '../../types/portfolioAi';
import { PortfolioAiGenerationBadge } from './PortfolioAiGenerationBadge';
import type { TranslateFn } from './portfolioAiUiTypes';

type Props = {
    result: AiAnalysisResult;
    t: TranslateFn;
    showDisclaimer?: boolean;
    /** Son analiz kartı: yalnızca yorum bölümleri, puan kutuları yok */
    variant?: 'default' | 'summaryCard';
};

function scoreBand(score: number, t: TranslateFn): { label: string; tone: 'good' | 'mid' | 'high' } {
    if (score >= 75) return { label: t('portfolioAi.bandGood', 'İyi'), tone: 'good' };
    if (score >= 50) return { label: t('portfolioAi.bandMidHigh', 'Orta-Yüksek'), tone: 'mid' };
    return { label: t('portfolioAi.bandHigh', 'Yüksek'), tone: 'high' };
}

function narrativeSections(result: AiAnalysisResult) {
    const overview = result.portfolioOverview;
    const decision = result.decisionPerspective;

    const generalSituation =
        overview?.summary?.trim() ||
        overview?.currentSituation?.trim() ||
        result.summary?.trim() ||
        '';

    const mainPositive =
        overview?.mainPositive?.trim() ||
        result.keyFindings.find((f) => /olumlu|pozitif|reel/i.test(f)) ||
        '';

    const mainRisk =
        overview?.mainRisk?.trim() ||
        result.keyFindings.find((f) => /risk|yoğun|konsantr/i.test(f)) ||
        (result.concentrationRisk === 'HIGH'
            ? 'Portföyde yoğunlaşma riski yüksek görünüyor.'
            : '');

    const scenarioComment =
        decision?.comment?.trim() ||
        result.scenarioComment?.trim() ||
        '';

    const watchPoints =
        decision?.watchPoints && decision.watchPoints.length > 0
            ? decision.watchPoints.filter((w) => w.trim().length > 0)
            : result.keyFindings.filter((f) => f.trim().length > 0);

    return { generalSituation, mainPositive, mainRisk, scenarioComment, watchPoints };
}

function SummaryCardBody({ result, t }: { result: AiAnalysisResult; t: TranslateFn }) {
    const { generalSituation, mainPositive, mainRisk, scenarioComment, watchPoints } =
        narrativeSections(result);

    return (
        <div className="pf-ai-last-result-body">
            {generalSituation ? (
                <section className="pf-ai-narrative-section">
                    <h3 className="pf-ai-narrative-section__title">
                        {t('portfolioAi.generalSituation', 'Genel Durum')}
                    </h3>
                    <p className="pf-ai-narrative-section__text">{generalSituation}</p>
                </section>
            ) : null}

            {mainPositive ? (
                <section className="pf-ai-narrative-section">
                    <h3 className="pf-ai-narrative-section__title">
                        {t('portfolioAi.mainPositive', 'Ana Olumlu Taraf')}
                    </h3>
                    <p className="pf-ai-narrative-section__text">{mainPositive}</p>
                </section>
            ) : null}

            {mainRisk ? (
                <section className="pf-ai-narrative-section">
                    <h3 className="pf-ai-narrative-section__title">
                        {t('portfolioAi.mainRisk', 'Ana Risk')}
                    </h3>
                    <p className="pf-ai-narrative-section__text">{mainRisk}</p>
                </section>
            ) : null}

            {scenarioComment ? (
                <section className="pf-ai-narrative-section">
                    <h3 className="pf-ai-narrative-section__title">
                        {t('portfolioAi.scenarioCommentTitle', 'Tutma / Satış Senaryosu Yorumu')}
                    </h3>
                    <p className="pf-ai-narrative-section__text">{scenarioComment}</p>
                </section>
            ) : null}

            {watchPoints.length > 0 ? (
                <section className="pf-ai-narrative-section">
                    <h3 className="pf-ai-narrative-section__title">
                        {t('portfolioAi.watchPoints', 'İzlenecek Noktalar')}
                    </h3>
                    <ul className="pf-ai-narrative-section__list">
                        {watchPoints.map((w, i) => (
                            <li key={i}>{w}</li>
                        ))}
                    </ul>
                </section>
            ) : null}
        </div>
    );
}

export function PortfolioAiResultsBlock({
    result,
    t,
    showDisclaimer = true,
    variant = 'default',
}: Props) {
    if (variant === 'summaryCard') {
        return <SummaryCardBody result={result} t={t} />;
    }

    const pfBand = scoreBand(result.portfolioScore, t);
    const riskBand = scoreBand(100 - result.riskScore, t);
    const macro = result.macroAndNewsImpact;
    const { generalSituation, mainPositive, mainRisk, scenarioComment, watchPoints } =
        narrativeSections(result);

    return (
        <div className="pf-ai-results-block">
            <div className="pf-ai-results-meta">
                <PortfolioAiGenerationBadge t={t} source={result.source} model={result.model} />
            </div>

            {generalSituation ? (
                <div className="pf-ai-results-copy pf-ai-results-copy--primary">
                    <h4>{t('portfolioAi.generalSituation', 'Genel Durum')}</h4>
                    <p>{generalSituation}</p>
                </div>
            ) : null}

            {mainPositive ? (
                <div className="pf-ai-results-copy">
                    <h4>{t('portfolioAi.mainPositive', 'Ana Olumlu Taraf')}</h4>
                    <p>{mainPositive}</p>
                </div>
            ) : null}

            {mainRisk ? (
                <div className="pf-ai-results-copy">
                    <h4>{t('portfolioAi.mainRisk', 'Ana Risk')}</h4>
                    <p>{mainRisk}</p>
                </div>
            ) : null}

            {scenarioComment ? (
                <div className="pf-ai-results-copy">
                    <h4>{t('portfolioAi.scenarioCommentTitle', 'Tutma / Satış Senaryosu Yorumu')}</h4>
                    <p>{scenarioComment}</p>
                </div>
            ) : null}

            {watchPoints.length > 0 ? (
                <div className="pf-ai-results-copy">
                    <h4>{t('portfolioAi.watchPoints', 'İzlenecek Noktalar')}</h4>
                    <ul className="pf-ai-detail-list">
                        {watchPoints.map((w, i) => (
                            <li key={i}>{w}</li>
                        ))}
                    </ul>
                </div>
            ) : null}

            {macro?.summary ? (
                <div className="pf-ai-results-copy">
                    <h4>{t('portfolioAi.macroNewsImpact', 'Makro ve haber etkisi')}</h4>
                    <p>{macro.summary}</p>
                </div>
            ) : null}

            <div className="pf-ai-metric-tiles pf-ai-metric-tiles--compact">
                <div
                    className={`pf-ai-metric-tile pf-ai-metric-tile--compact${pfBand.tone === 'good' ? ' pf-ai-metric-tile--good' : pfBand.tone === 'mid' ? ' pf-ai-metric-tile--mid' : ''}`}
                >
                    <span>{t('portfolioAi.scorePortfolio', 'Portföy Puanı')}</span>
                    <strong>
                        {result.portfolioScore}
                        <small>/100</small>
                    </strong>
                </div>
                <div
                    className={`pf-ai-metric-tile pf-ai-metric-tile--compact${riskBand.tone === 'mid' ? ' pf-ai-metric-tile--mid' : riskBand.tone === 'high' ? ' pf-ai-metric-tile--high' : ''}`}
                >
                    <span>{t('portfolioAi.scoreRisk', 'Risk Puanı')}</span>
                    <strong>
                        {result.riskScore}
                        <small>/100</small>
                    </strong>
                </div>
                <div className="pf-ai-metric-tile pf-ai-metric-tile--compact">
                    <span>{t('portfolioAi.confidence', 'Güven')}</span>
                    <em>{result.confidenceLevel}</em>
                </div>
                <div className="pf-ai-metric-tile pf-ai-metric-tile--compact">
                    <span>{t('portfolioAi.concentration', 'Yoğunlaşma')}</span>
                    <em>{result.concentrationRisk}</em>
                </div>
            </div>

            {showDisclaimer ? (
                <p className="pf-ai-disclaimer-inline">
                    {result.finalNote ||
                        t('portfolioAi.disclaimer', 'Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir.')}
                </p>
            ) : null}
        </div>
    );
}
