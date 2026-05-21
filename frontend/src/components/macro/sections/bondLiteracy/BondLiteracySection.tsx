import type { MacroTheme } from '../../MacroTheme';
import { useLanguage } from '../../../../i18n/LanguageContext';
import { MacroSection } from '../../primitives/MacroSection';
import { BondConceptCards } from './BondConceptCards';
import { BondMacroFlowCard } from './BondMacroFlowCard';
import { BondPriceRateRelationCard } from './BondPriceRateRelationCard';
import { BondYieldCurvePlaceholder } from './BondYieldCurvePlaceholder';
import { BondMarketRedirectCard } from './BondMarketRedirectCard';

type Props = {
    tokens: MacroTheme;
};

export function BondLiteracySection({ tokens }: Props) {
    const { t } = useLanguage();

    return (
        <MacroSection
            id="macro-bonds"
            title={t('macro.bondLiteracy.title', 'Tahvil & Bono')}
            summary={t(
                'macro.bondLiteracy.summary',
                'Sabit getirili borçlanma araçlarını TCMB faizi, enflasyon ve vade ilişkisiyle yorumlayın.',
            )}
            termId="bondSectionIntro"
            infoAriaLabel={t('macro.bondLiteracy.infoAria', 'Tahvil ve bono bölümü hakkında bilgi')}
            tokens={tokens}
        >
            <BondConceptCards tokens={tokens} />

            <div className="macro-grid macro-grid--2 bond-literacy__main-grid">
                <BondMacroFlowCard tokens={tokens} />
                <BondPriceRateRelationCard tokens={tokens} />
            </div>

            <BondYieldCurvePlaceholder tokens={tokens} />

            <BondMarketRedirectCard tokens={tokens} />
        </MacroSection>
    );
}
