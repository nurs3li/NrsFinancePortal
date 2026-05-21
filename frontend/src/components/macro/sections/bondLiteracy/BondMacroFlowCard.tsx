import { ArrowDown } from 'lucide-react';
import type { MacroTheme } from '../../MacroTheme';
import { useLanguage } from '../../../../i18n/LanguageContext';
import { InfoButton } from '../../education/InfoButton';
import { useInfoTerm } from '../../education/InfoTermProvider';

type Props = {
    tokens: MacroTheme;
};

function FlowStep({ label, accent }: { label: string; accent: string }) {
    return (
        <div className="bond-literacy__flow-step" style={{ borderColor: accent, background: `${accent}18` }}>
            <span>{label}</span>
        </div>
    );
}

export function BondMacroFlowCard({ tokens }: Props) {
    const { t } = useLanguage();
    const { openTerm } = useInfoTerm();

    return (
        <article className="bond-literacy__panel-card" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <div className="bond-literacy__panel-card-head">
                <h3 className="bond-literacy__panel-card-title" style={{ color: tokens.text }}>
                    {t('macro.bondLiteracy.flow.title', 'Tahvil/Bono faizleri nelerden etkilenir?')}
                </h3>
                <InfoButton
                    termId="bondMacroDrivers"
                    ariaLabel={t('macro.bondLiteracy.flow.infoAria', 'Faiz etkileri hakkında bilgi')}
                />
            </div>

            <div className="bond-literacy__flow-columns">
                <div className="bond-literacy__flow-col">
                    <div className="bond-literacy__flow-col-label" style={{ color: tokens.textMuted }}>
                        {t('macro.bondLiteracy.flow.shortTerm', 'Kısa vade')}
                    </div>
                    <FlowStep label={t('macro.bondLiteracy.flow.policyRate', 'TCMB Politika Faizi')} accent="#38bdf8" />
                    <ArrowDown size={16} className="bond-literacy__flow-arrow" aria-hidden />
                    <FlowStep
                        label={t('macro.bondLiteracy.flow.shortTlRates', 'Kısa Vadeli TL Faizleri')}
                        accent="#38bdf8"
                    />
                    <ArrowDown size={16} className="bond-literacy__flow-arrow" aria-hidden />
                    <FlowStep label={t('macro.bondLiteracy.flow.bonoLogic', 'Bono Mantığı')} accent="#22d3ee" />
                </div>

                <div className="bond-literacy__flow-col">
                    <div className="bond-literacy__flow-col-label" style={{ color: tokens.textMuted }}>
                        {t('macro.bondLiteracy.flow.longTerm', 'Uzun vade')}
                    </div>
                    <FlowStep
                        label={t('macro.bondLiteracy.flow.inflationRisk', 'Enflasyon Beklentisi + Risk Primi')}
                        accent="#f59e0b"
                    />
                    <ArrowDown size={16} className="bond-literacy__flow-arrow" aria-hidden />
                    <FlowStep
                        label={t('macro.bondLiteracy.flow.longBondLogic', 'Uzun Vadeli Tahvil Mantığı')}
                        accent="#a78bfa"
                    />
                </div>
            </div>

            <p className="bond-literacy__flow-note" style={{ color: tokens.textMuted }}>
                {t(
                    'macro.bondLiteracy.flow.note',
                    'Kısa vadeli araçlar politika faizine, uzun vadeli araçlar ise enflasyon beklentisi ve risk primine daha duyarlıdır.',
                )}
            </p>
            <button type="button" className="macro-link-btn" onClick={() => openTerm('bondMacroDrivers')}>
                {t('macro.bondLiteracy.flow.detailLink', 'Detaylı açıklama')}
            </button>
        </article>
    );
}
