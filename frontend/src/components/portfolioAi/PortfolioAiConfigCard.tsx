import { Sparkles } from 'lucide-react';
import type { AiDetailLevel, AiRiskProfile, PortfolioAnalysisType } from '../../types/portfolioAi';
import { PortfolioAiAsciiRunner } from './PortfolioAiAsciiRunner';
import type { TranslateFn } from './portfolioAiUiTypes';

type Props = {
    t: TranslateFn;
    title: string;
    setTitle: (v: string) => void;
    titleError: string | null;
    pfType: PortfolioAnalysisType;
    setPfType: (v: PortfolioAnalysisType) => void;
    risk: AiRiskProfile;
    setRisk: (v: AiRiskProfile) => void;
    detail: AiDetailLevel;
    setDetail: (v: AiDetailLevel) => void;
    incNews: boolean;
    setIncNews: (v: boolean) => void;
    incMacro: boolean;
    setIncMacro: (v: boolean) => void;
    incInflation: boolean;
    setIncInflation: (v: boolean) => void;
    pending: boolean;
    errorMsg: string | null;
    onAnalyze: () => void;
};

export function PortfolioAiConfigCard({
    t,
    title,
    setTitle,
    titleError,
    pfType,
    setPfType,
    risk,
    setRisk,
    detail,
    setDetail,
    incNews,
    setIncNews,
    incMacro,
    setIncMacro,
    incInflation,
    setIncInflation,
    pending,
    errorMsg,
    onAnalyze,
}: Props) {
    const titleInvalid = title.trim().length === 0;

    return (
        <section className="pf-ai-dash-card pf-ai-dash-card--config">
            <h2 className="pf-ai-dash-card__title">{t('portfolioAi.configCardTitle', 'Mevcut Portföy Analizi')}</h2>
            <div className="pf-ai-form-grid pf-ai-form-grid--config">
                <label className="pf-ai-form-field">
                    {t('portfolioAi.analysisTitle', 'Başlık')}
                    <input
                        type="text"
                        value={title}
                        onChange={(e) => setTitle(e.target.value)}
                        placeholder={t('portfolioAi.analysisTitlePlaceholder', 'Örn. Mart 2026 portföy değerlendirmesi')}
                        maxLength={120}
                        required
                        aria-required
                        aria-invalid={titleInvalid || !!titleError}
                    />
                </label>
                <label className="pf-ai-form-field">
                    {t('portfolioAi.analysisType', 'Analiz tipi')}
                    <select value={pfType} onChange={(e) => setPfType(e.target.value as PortfolioAnalysisType)}>
                        <option value="GENERAL">{t('portfolioAi.typeGeneral', 'Genel portföy değerlendirmesi')}</option>
                        <option value="HOLD_1W">{t('portfolioAi.type1w', '1 hafta tutma senaryosu')}</option>
                        <option value="HOLD_1M">{t('portfolioAi.type1m', '1 ay tutma senaryosu')}</option>
                        <option value="HOLD_3M">{t('portfolioAi.type3m', '3 ay tutma senaryosu')}</option>
                        <option value="SELL_ALL">{t('portfolioAi.typeSellAll', 'Tamamını satma senaryosu')}</option>
                    </select>
                </label>
                <label className="pf-ai-form-field">
                    {t('portfolioAi.riskProfile', 'Risk profili')}
                    <select value={risk} onChange={(e) => setRisk(e.target.value as AiRiskProfile)}>
                        <option value="LOW">{t('portfolioAi.riskLow', 'Düşük risk')}</option>
                        <option value="BALANCED">{t('portfolioAi.riskBalanced', 'Dengeli')}</option>
                        <option value="AGGRESSIVE">{t('portfolioAi.riskAggressive', 'Agresif')}</option>
                    </select>
                </label>
                <label className="pf-ai-form-field">
                    {t('portfolioAi.detailLevel', 'Detay seviyesi')}
                    <select value={detail} onChange={(e) => setDetail(e.target.value as AiDetailLevel)}>
                        <option value="SHORT">{t('portfolioAi.detailShort', 'Kısa özet')}</option>
                        <option value="DETAILED">{t('portfolioAi.detailFull', 'Detaylı analiz')}</option>
                    </select>
                </label>
            </div>
            {titleError ? <p className="pf-ai-field-error">{titleError}</p> : null}
            <div className="pf-ai-checks pf-ai-checks--stack">
                <label>
                    <input type="checkbox" checked={incNews} onChange={(e) => setIncNews(e.target.checked)} />
                    {t('portfolioAi.chkNewsEffect', 'Haber etkisi dahil')}
                </label>
                <label>
                    <input type="checkbox" checked={incMacro} onChange={(e) => setIncMacro(e.target.checked)} />
                    {t('portfolioAi.chkMacroEffect', 'Makro bağlam dahil')}
                </label>
                <label>
                    <input type="checkbox" checked={incInflation} onChange={(e) => setIncInflation(e.target.checked)} />
                    {t('portfolioAi.chkRealReturn', 'Reel getiri / enflasyon etkisi')}
                </label>
            </div>
            <button
                type="button"
                className="pf-ai-btn-gradient"
                disabled={pending || titleInvalid}
                onClick={onAnalyze}
            >
                <Sparkles size={16} aria-hidden />
                {pending
                    ? t('portfolioAi.generating', 'Analiz oluşturuluyor…')
                    : t('portfolioAi.btnAnalyzePortfolio', 'Portföyümü AI ile analiz et')}
            </button>
            <PortfolioAiAsciiRunner active={pending} t={t} />
            {errorMsg ? <p className="pf-ai-error">{errorMsg}</p> : null}
        </section>
    );
}
