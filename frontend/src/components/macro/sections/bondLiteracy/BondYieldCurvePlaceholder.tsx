import type { MacroTheme } from '../../MacroTheme';
import { useLanguage } from '../../../../i18n/LanguageContext';
import { InfoButton } from '../../education/InfoButton';

type Props = {
    tokens: MacroTheme;
};

const TENOR_KEYS = [
    {
        id: 'short',
        labelKey: 'macro.bondLiteracy.yield.short',
        hintKey: 'macro.bondLiteracy.yield.shortHint',
        labelFb: 'Kısa Vade',
        hintFb: 'Politika faizi etkisi',
    },
    {
        id: 'mid',
        labelKey: 'macro.bondLiteracy.yield.mid',
        hintKey: 'macro.bondLiteracy.yield.midHint',
        labelFb: 'Orta Vade',
        hintFb: 'Geçiş bölgesi',
    },
    {
        id: 'long',
        labelKey: 'macro.bondLiteracy.yield.long',
        hintKey: 'macro.bondLiteracy.yield.longHint',
        labelFb: 'Uzun Vade',
        hintFb: 'Enflasyon + risk primi',
    },
] as const;

export function BondYieldCurvePlaceholder({ tokens }: Props) {
    const { t } = useLanguage();

    return (
        <article className="bond-literacy__yield-card" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <div className="bond-literacy__yield-head">
                <div>
                    <h3 className="bond-literacy__panel-card-title" style={{ color: tokens.text }}>
                        {t('macro.bondLiteracy.yield.title', 'Vade-Getiri Eğrisi')}
                    </h3>
                    <div className="bond-literacy__badges">
                        <span className="bond-literacy__badge bond-literacy__badge--edu">
                            {t('macro.bondLiteracy.yield.educationBadge', 'Eğitim amaçlı gösterim')}
                        </span>
                        <span className="bond-literacy__badge bond-literacy__badge--muted">
                            {t('macro.bondLiteracy.yield.notLive', 'Canlı getiri eğrisi bağlı değil')}
                        </span>
                    </div>
                </div>
                <InfoButton
                    termId="yieldCurve"
                    ariaLabel={t('macro.bondLiteracy.yield.infoAria', 'Vade-getiri eğrisi hakkında bilgi')}
                />
            </div>

            <div className="bond-literacy__yield-body">
                <div className="bond-literacy__tenor-stack">
                    {TENOR_KEYS.map((tenor) => (
                        <div
                            key={tenor.id}
                            className={`bond-literacy__tenor bond-literacy__tenor--${tenor.id}`}
                            style={{ borderColor: tokens.border }}
                        >
                            <span className="bond-literacy__tenor-label" style={{ color: tokens.text }}>
                                {t(tenor.labelKey, tenor.labelFb)}
                            </span>
                            <span className="bond-literacy__tenor-hint" style={{ color: tokens.textMuted }}>
                                {t(tenor.hintKey, tenor.hintFb)}
                            </span>
                        </div>
                    ))}
                </div>

                <div className="bond-literacy__curve-visual" aria-hidden>
                    <svg viewBox="0 0 200 100" className="bond-literacy__curve-svg">
                        <defs>
                            <linearGradient id="bondCurveGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                                <stop offset="0%" stopColor="#38bdf8" stopOpacity="0.35" />
                                <stop offset="100%" stopColor="#a78bfa" stopOpacity="0.55" />
                            </linearGradient>
                        </defs>
                        <path
                            d="M 12 78 Q 60 72, 95 58 T 188 28"
                            fill="none"
                            stroke="url(#bondCurveGrad)"
                            strokeWidth="3"
                            strokeLinecap="round"
                        />
                        <circle cx="12" cy="78" r="4" fill="#38bdf8" />
                        <circle cx="95" cy="58" r="4" fill="#22d3ee" />
                        <circle cx="188" cy="28" r="4" fill="#a78bfa" />
                    </svg>
                </div>
            </div>

            <p className="bond-literacy__yield-foot" style={{ color: tokens.textMuted }}>
                {t(
                    'macro.bondLiteracy.yield.foot',
                    'Gerçek DİBS getiri eğrisi verisi bağlı olmadığında bu alan kavramsal öğrenme amacıyla gösterilir.',
                )}
            </p>
        </article>
    );
}
