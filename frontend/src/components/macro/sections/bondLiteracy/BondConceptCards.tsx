import type { MacroTermId } from '../../../../content/macroEducationTerms';
import type { MacroTheme } from '../../MacroTheme';
import { useLanguage } from '../../../../i18n/LanguageContext';
import { InfoButton } from '../../education/InfoButton';

type Concept = {
    titleKey: string;
    shortKey: string;
    termId: MacroTermId;
    titleFallback: string;
    shortFallback: string;
};

const CONCEPTS: Concept[] = [
    {
        titleKey: 'macro.bondLiteracy.concept.bond',
        shortKey: 'macro.bondLiteracy.concept.bondShort',
        termId: 'bond',
        titleFallback: 'Tahvil',
        shortFallback: 'Genellikle 1 yıldan uzun vadeli borçlanma aracı',
    },
    {
        titleKey: 'macro.bondLiteracy.concept.bono',
        shortKey: 'macro.bondLiteracy.concept.bonoShort',
        termId: 'bono',
        titleFallback: 'Bono',
        shortFallback: 'Genellikle 1 yıldan kısa vadeli borçlanma aracı',
    },
    {
        titleKey: 'macro.bondLiteracy.concept.coupon',
        shortKey: 'macro.bondLiteracy.concept.couponShort',
        termId: 'coupon',
        titleFallback: 'Kupon',
        shortFallback: 'Dönemsel faiz ödemesi',
    },
    {
        titleKey: 'macro.bondLiteracy.concept.maturity',
        shortKey: 'macro.bondLiteracy.concept.maturityShort',
        termId: 'bondMaturity',
        titleFallback: 'Vade',
        shortFallback: 'Anaparanın geri ödeneceği tarih',
    },
];

type Props = {
    tokens: MacroTheme;
};

export function BondConceptCards({ tokens }: Props) {
    const { t } = useLanguage();

    return (
        <div className="bond-literacy__concept-grid">
            {CONCEPTS.map((c) => {
                const title = t(c.titleKey, c.titleFallback);
                const ariaTemplate = t('macro.bondLiteracy.conceptInfoAria', `${title} hakkında bilgi`);
                const ariaLabel = ariaTemplate.includes('{title}')
                    ? ariaTemplate.replace('{title}', title)
                    : ariaTemplate;

                return (
                    <article
                        key={c.termId}
                        className="bond-literacy__concept-card"
                        style={{ borderColor: tokens.border, background: tokens.bgCard }}
                    >
                        <div className="bond-literacy__concept-card-head">
                            <h3 className="bond-literacy__concept-card-title" style={{ color: tokens.text }}>
                                {title}
                            </h3>
                            <InfoButton termId={c.termId} ariaLabel={ariaLabel} />
                        </div>
                        <p className="bond-literacy__concept-card-short" style={{ color: tokens.textMuted }}>
                            {t(c.shortKey, c.shortFallback)}
                        </p>
                    </article>
                );
            })}
        </div>
    );
}
