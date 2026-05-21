import { Download, Mail, X } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { createPortal } from 'react-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthContext';
import {
    getPortfolioAiEmailDelivery,
    upsertPortfolioAiEmailDelivery,
} from '../../services/portfolioAiApi';
import type { AiAnalysisResult } from '../../types/portfolioAi';
import type { PortfolioAiEmailFrequency } from '../../types/portfolioAi';
import { exportPortfolioAiPdf } from '../../utils/portfolioAiExportPdf';
import type { TranslateFn } from './portfolioAiUiTypes';

type Props = {
    t: TranslateFn;
    locale: string;
    portfolioResult: AiAnalysisResult | null;
};

function frequencyLabel(freq: PortfolioAiEmailFrequency, t: TranslateFn): string {
    return freq === 'MONTHLY'
        ? t('portfolioAi.emailDeliveryMonthly', 'Aylık')
        : t('portfolioAi.emailDeliveryWeekly', 'Haftalık');
}

export function PortfolioAiEmailDeliveryPanel({ t, locale, portfolioResult }: Props) {
    const { user } = useAuth();
    const queryClient = useQueryClient();
    const [modalOpen, setModalOpen] = useState(false);
    const [draftEnabled, setDraftEnabled] = useState(false);
    const [draftFrequency, setDraftFrequency] = useState<PortfolioAiEmailFrequency>('WEEKLY');
    const accountEmail = user?.email?.trim() ?? '';
    const [localError, setLocalError] = useState<string | null>(null);

    const { data: saved } = useQuery({
        queryKey: ['portfolio-ai', 'email-delivery'],
        queryFn: getPortfolioAiEmailDelivery,
    });

    const openModal = () => {
        setDraftEnabled(saved?.enabled ?? false);
        setDraftFrequency(saved?.frequency ?? 'WEEKLY');
        setLocalError(null);
        setModalOpen(true);
    };

    const saveMutation = useMutation({
        mutationFn: upsertPortfolioAiEmailDelivery,
        onSuccess: () => {
            setLocalError(null);
            setModalOpen(false);
            void queryClient.invalidateQueries({ queryKey: ['portfolio-ai', 'email-delivery'] });
        },
        onError: () => {
            setLocalError(t('portfolioAi.emailDeliverySaveFailed', 'E-posta tercihi kaydedilemedi.'));
        },
    });

    const closeModal = () => {
        if (!saveMutation.isPending) {
            setModalOpen(false);
            setLocalError(null);
        }
    };

    useEffect(() => {
        if (!modalOpen) return;
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') closeModal();
        };
        document.addEventListener('keydown', onKey);
        document.body.style.overflow = 'hidden';
        return () => {
            document.removeEventListener('keydown', onKey);
            document.body.style.overflow = '';
        };
    }, [modalOpen, saveMutation.isPending]);

    const handleSave = (e: FormEvent) => {
        e.preventDefault();
        if (draftEnabled) {
            if (!accountEmail) {
                setLocalError(t('portfolioAi.emailDeliveryEmailRequired', 'E-posta adresi girin.'));
                return;
            }
            setLocalError(null);
            saveMutation.mutate({
                enabled: true,
                email: accountEmail,
                frequency: draftFrequency,
            });
            return;
        }
        setLocalError(null);
        saveMutation.mutate({
            enabled: false,
            email: accountEmail || null,
            frequency: draftFrequency,
        });
    };

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
        <>
            <div className="pf-ai-hero-actions">
                <button
                    type="button"
                    className="pf-ai-btn-outline pf-ai-btn-outline--email"
                    onClick={openModal}
                >
                    <Mail size={14} aria-hidden />
                    {t('portfolioAi.emailDeliveryTitle', 'E-posta ile analiz')}
                </button>
                {saved?.enabled ? (
                    <span className="pf-ai-hero-actions__freq" aria-label={t('portfolioAi.emailDeliveryFrequency', 'Gönderim sıklığı')}>
                        {frequencyLabel(saved.frequency ?? 'WEEKLY', t)}
                    </span>
                ) : null}
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

            {modalOpen
                ? createPortal(
                      <div
                          className="pf-ai-modal-backdrop"
                          onClick={(e) => {
                              if (e.target === e.currentTarget) closeModal();
                          }}
                          role="presentation"
                      >
                          <div
                              className="pf-ai-modal"
                              onClick={(ev) => ev.stopPropagation()}
                              role="dialog"
                              aria-labelledby="pf-ai-email-modal-title"
                              aria-modal="true"
                          >
                        <header className="pf-ai-modal__head">
                            <div className="pf-ai-modal__title-row">
                                <Mail size={18} aria-hidden className="pf-ai-modal__icon" />
                                <h2 id="pf-ai-email-modal-title" className="pf-ai-modal__title">
                                    {t('portfolioAi.emailDeliveryTitle', 'E-posta ile analiz')}
                                </h2>
                            </div>
                            <button
                                type="button"
                                className="pf-ai-modal__close"
                                onClick={closeModal}
                                aria-label={t('portfolioAi.close', 'Kapat')}
                            >
                                <X size={18} aria-hidden />
                            </button>
                        </header>
                        <form className="pf-ai-modal__body" onSubmit={handleSave}>
                            <label className="pf-ai-modal__toggle">
                                <input
                                    type="checkbox"
                                    checked={draftEnabled}
                                    onChange={(e) => setDraftEnabled(e.target.checked)}
                                />
                                <span>
                                    {t('portfolioAi.emailDeliveryAsk', 'Analizi e-posta ile almak istiyorum')}
                                </span>
                            </label>
                            {draftEnabled ? (
                                <>
                                    <fieldset className="pf-ai-modal__freq">
                                        <legend>{t('portfolioAi.emailDeliveryFrequency', 'Gönderim sıklığı')}</legend>
                                        <label>
                                            <input
                                                type="radio"
                                                name="pf-ai-email-freq"
                                                checked={draftFrequency === 'WEEKLY'}
                                                onChange={() => setDraftFrequency('WEEKLY')}
                                            />
                                            {t('portfolioAi.emailDeliveryWeekly', 'Haftalık')}
                                        </label>
                                        <label>
                                            <input
                                                type="radio"
                                                name="pf-ai-email-freq"
                                                checked={draftFrequency === 'MONTHLY'}
                                                onChange={() => setDraftFrequency('MONTHLY')}
                                            />
                                            {t('portfolioAi.emailDeliveryMonthly', 'Aylık')}
                                        </label>
                                    </fieldset>
                                    <label className="pf-ai-modal__email">
                                        {t('portfolioAi.emailDeliveryEmail', 'E-posta adresi')}
                                        <input
                                            type="email"
                                            className="pf-ai-modal__email-input--readonly"
                                            value={accountEmail}
                                            readOnly
                                            aria-readonly="true"
                                            title={t(
                                                'portfolioAi.emailDeliveryEmailReadonly',
                                                'Hesabınıza kayıtlı e-posta kullanılır.',
                                            )}
                                        />
                                    </label>
                                    <p className="pf-ai-modal__hint">
                                        {t(
                                            'portfolioAi.emailDeliveryHint',
                                            'Tercih kaydedildi. Periyodik gönderim planlandığında bu adrese özet iletilecektir.',
                                        )}
                                    </p>
                                </>
                            ) : null}
                            {localError ? <p className="pf-ai-field-error">{localError}</p> : null}
                            <footer className="pf-ai-modal__foot">
                                <button
                                    type="button"
                                    className="pf-ai-btn-outline"
                                    onClick={closeModal}
                                    disabled={saveMutation.isPending}
                                >
                                    {t('portfolioAi.cancel', 'İptal')}
                                </button>
                                <button
                                    type="submit"
                                    className="pf-ai-modal__save"
                                    disabled={saveMutation.isPending}
                                >
                                    {saveMutation.isPending
                                        ? t('portfolioAi.saving', 'Kaydediliyor…')
                                        : t('portfolioAi.save', 'Kaydet')}
                                </button>
                            </footer>
                          </form>
                      </div>
                  </div>,
                      document.body,
                  )
                : null}
        </>
    );
}
