import { ArrowDown } from 'lucide-react';
import type { MacroTheme } from '../../MacroTheme';
import { useLanguage } from '../../../../i18n/LanguageContext';
import { useTheme } from '../../../../theme/ThemeContext';
import { InfoButton } from '../../education/InfoButton';
import { useInfoTerm } from '../../education/InfoTermProvider';

type Props = {
    tokens: MacroTheme;
};

function FlowStep({ label, accent, border }: { label: string; accent: string; border: string }) {
    return (
        <div className="bond-literacy__flow-step" style={{ borderColor: border, background: `${accent}18` }}>
            <span>{label}</span>
        </div>
    );
}

export function BondMacroFlowCard({ tokens }: Props) {
    const { t } = useLanguage();
    const { theme } = useTheme();
    const { openTerm } = useInfoTerm();

    const accents =
        theme === 'light'
            ? {
                  short: '#1d4ed8',
                  shortAlt: '#0e7490',
                  shortBorder: '#1d4ed8',
                  shortAltBorder: '#0e7490',
                  long: '#b45309',
                  longAlt: '#6d28d9',
                  longBorder: '#b45309',
                  longAltBorder: '#6d28d9',
              }
            : {
                  short: '#38bdf8',
                  shortAlt: '#22d3ee',
                  shortBorder: '#38bdf8',
                  shortAltBorder: '#22d3ee',
                  long: '#f59e0b',
                  longAlt: '#a78bfa',
                  longBorder: '#f59e0b',
                  longAltBorder: '#a78bfa',
              };

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
                    <FlowStep label={t('macro.bondLiteracy.flow.policyRate', 'TCMB Politika Faizi')} accent={accents.short} border={accents.shortBorder} />
                    <ArrowDown size={16} className="bond-literacy__flow-arrow" aria-hidden />
                    <FlowStep
                        label={t('macro.bondLiteracy.flow.shortTlRates', 'Kısa Vadeli TL Faizleri')}
                        accent={accents.short}
                        border={accents.shortBorder}
                    />
                    <ArrowDown size={16} className="bond-literacy__flow-arrow" aria-hidden />
                    <FlowStep label={t('macro.bondLiteracy.flow.bonoLogic', 'Bono Mantığı')} accent={accents.shortAlt} border={accents.shortAltBorder} />
                </div>

                <div className="bond-literacy__flow-col">
                    <div className="bond-literacy__flow-col-label" style={{ color: tokens.textMuted }}>
                        {t('macro.bondLiteracy.flow.longTerm', 'Uzun vade')}
                    </div>
                    <FlowStep
                        label={t('macro.bondLiteracy.flow.inflationRisk', 'Enflasyon Beklentisi + Risk Primi')}
                        accent={accents.long}
                        border={accents.longBorder}
                    />
                    <ArrowDown size={16} className="bond-literacy__flow-arrow" aria-hidden />
                    <FlowStep
                        label={t('macro.bondLiteracy.flow.longBondLogic', 'Uzun Vadeli Tahvil Mantığı')}
                        accent={accents.longAlt}
                        border={accents.longAltBorder}
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
