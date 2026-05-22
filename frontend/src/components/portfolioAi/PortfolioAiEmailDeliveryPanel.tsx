import { Download, Mail } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import type { AiAnalysisResult } from '../../types/portfolioAi';
import { exportPortfolioAiPdf } from '../../utils/portfolioAiExportPdf';
import type { TranslateFn } from './portfolioAiUiTypes';

type Props = {
    t: TranslateFn;
    locale: string;
    portfolioResult: AiAnalysisResult | null;
    emailFlowPending: boolean;
    onEmailAnalyze: () => void;
};

export function PortfolioAiEmailDeliveryPanel({
    t,
    locale,
    portfolioResult,
    emailFlowPending,
    onEmailAnalyze,
}: Props) {
    const { user } = useAuth();
    const accountEmail = user?.email?.trim() ?? '';
    const canEmail = accountEmail.length > 0;

    const handlePdfExport = () => {
        if (!portfolioResult) return;
        void exportPortfolioAiPdf(portfolioResult, {
            brandTitle: t('portfolioAi.exportPdfBrand', 'NRS Finance Portal'),
            title: t('portfolioAi.exportPdfTitle', 'NRS Finance Portal — Portföy AI analizi'),
            generated: t('portfolioAi.exportPdfGenerated', 'Oluşturulma'),
            portfolioCommentary: t('portfolioAi.exportPdfPortfolioCommentary', 'Portföy AI Yorumu'),
            assetPortfolioCommentary: t(
                'portfolioAi.exportPdfAssetPortfolioCommentary',
                'Varlık Portföy Yorumu',
            ),
            assetBasedAnalysis: t(
                'portfolioAi.exportPdfAssetBasedAnalysis',
                'Varlık Bazlı AI Analiz Sonucu',
            ),
            general: t('portfolioAi.generalSituation', 'Genel Durum'),
            mainPositive: t('portfolioAi.mainPositive', 'Ana Olumlu Taraf'),
            mainRisk: t('portfolioAi.mainRisk', 'Ana Risk'),
            scenario: t('portfolioAi.scenarioCommentTitle', 'Tutma / Satış Senaryosu Yorumu'),
            watchPoints: t('portfolioAi.watchPoints', 'İzlenecek Noktalar'),
            macroImpact: t('portfolioAi.macroNewsImpact', 'Makro ve haber etkisi'),
            finalNote: t('portfolioAi.finalNote', 'Son not'),
            assets: t('portfolioAi.exportPdfAssets', 'Ek yorum'),
            symbol: t('portfolioAi.fieldSymbol', 'Sembol'),
            weight: t('portfolioAi.colWeight', 'Ağırlık'),
            role: t('portfolioAi.assetRoleLabel', 'Portföydeki rolü'),
            impact: t('portfolioAi.impactOnPortfolio', 'Portföye etkisi'),
            positive: t('portfolioAi.positiveView', 'Olumlu taraf'),
            risk: t('portfolioAi.riskView', 'Risk tarafı'),
            whatToWatch: t('portfolioAi.watchPoints', 'İzlenecek noktalar'),
            footer: t(
                'portfolioAi.disclaimer',
                'Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir.',
            ),
            fontError: t('portfolioAi.exportPdfFontError', 'PDF için Türkçe font yüklenemedi.'),
        }, locale);
    };

    return (
        <div className="pf-ai-hero-actions">
            <button
                type="button"
                className="pf-ai-btn-outline pf-ai-btn-outline--email"
                onClick={onEmailAnalyze}
                disabled={emailFlowPending || !canEmail}
                title={
                    !canEmail
                        ? t(
                              'portfolioAi.emailAnalyzeNoAccountEmail',
                              'E-posta göndermek için hesabınızda kayıtlı bir e-posta olmalı.',
                          )
                        : undefined
                }
            >
                <Mail size={14} aria-hidden />
                {emailFlowPending
                    ? t('portfolioAi.emailAnalyzeRunning', 'Analiz oluşturuluyor, e-posta gönderiliyor…')
                    : t('portfolioAi.emailDeliveryTitle', 'E-posta ile analiz')}
            </button>
            {portfolioResult ? (
                <button
                    type="button"
                    className="pf-ai-btn-outline pf-ai-btn-outline--pdf"
                    onClick={handlePdfExport}
                >
                    <Download size={14} aria-hidden />
                    {t('portfolioAi.downloadPdf', 'PDF olarak indir')}
                </button>
            ) : null}
        </div>
    );
}
