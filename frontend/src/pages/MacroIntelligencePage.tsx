import { useMemo } from 'react';
import { useLanguage } from '../i18n/LanguageContext';
import { useTheme } from '../theme/ThemeContext';
import { InfoTermProvider } from '../components/macro/education/InfoTermProvider';
import { useMacroIntelligenceData } from '../components/macro/hooks/useMacroIntelligenceData';
import { MacroPageHeader } from '../components/macro/layout/MacroPageHeader';
import { MacroSummaryKpiStrip } from '../components/macro/layout/MacroSummaryKpiStrip';
import { StickySectionNav, useMacroSectionSpy } from '../components/macro/layout/StickySectionNav';
import { MACRO_SECTION_NAV } from '../components/macro/macroSections';
import { MacroOverviewSection } from '../components/macro/sections/MacroOverviewSection';
import { MacroInflationSection } from '../components/macro/sections/MacroInflationSection';
import { MacroPolicySection } from '../components/macro/sections/MacroPolicySection';
import { MacroDepositSection } from '../components/macro/sections/MacroDepositSection';
import { MacroLoansSection } from '../components/macro/sections/MacroLoansSection';
import { MacroBondsSection } from '../components/macro/sections/MacroBondsSection';
import { MacroEurobondSection } from '../components/macro/sections/MacroEurobondSection';
import { MacroGlossarySection } from '../components/macro/sections/MacroGlossarySection';
import { EmptyStateCard } from '../components/macro/primitives/EmptyStateCard';
import { formatLocaleDate } from '../utils/macroPanelSeries';
import './MacroIntelligence.css';

export function MacroIntelligencePage() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-GB' : 'tr-TR';

    const chartTokens = useMemo(
        () => ({
            bg: tokens.bg,
            bgCard: tokens.bgCard,
            border: tokens.border,
            text: tokens.text,
            textMuted: tokens.textMuted,
        }),
        [tokens],
    );

    const data = useMacroIntelligenceData();
    const sectionIds = useMemo(() => MACRO_SECTION_NAV.map((s) => s.id), []);
    const { activeId, scrollTo } = useMacroSectionSpy(sectionIds);

    const lastUpdatedLabel = data.lastUpdated ? formatLocaleDate(data.lastUpdated, locale) : undefined;

    return (
        <InfoTermProvider tokens={chartTokens}>
            <div className="macro-intelligence-page" style={{ background: tokens.bg, color: tokens.text }}>
                <MacroPageHeader
                    title={t('nav.marketMacro', 'Faiz & Enflasyon Paneli')}
                    subtitle={t(
                        'market.bondMacroPageLead',
                        'Enflasyon, TCMB politika faizi, TL mevduat, kredi faizleri, tahvil/bono ve eurobond (EVDS) verilerini finansal okuryazarlık odağıyla inceleyin.',
                    )}
                    lastUpdated={lastUpdatedLabel}
                    tokens={chartTokens}
                />

                {data.panelError ? (
                    <EmptyStateCard
                        title={t('macro.panel.loadErrorTitle', 'Makro paneli şu an yüklenemedi')}
                        hint={t(
                            'macro.panel.loadErrorHint',
                            'Bölümler mümkün olan yedek uçlarla gösterilmeye devam eder.',
                        )}
                        tokens={chartTokens}
                        onRetry={() => void data.refetch()}
                    />
                ) : null}

                <MacroSummaryKpiStrip
                    panel={data.panel}
                    derived={data.derived}
                    panelLoading={data.panelLoading}
                    tokens={chartTokens}
                />

                <StickySectionNav activeId={activeId} onNavigate={scrollTo} />

                <MacroOverviewSection
                    panel={data.panel}
                    derived={data.derived}
                    panelLoading={data.panelLoading}
                    locale={locale}
                    tokens={chartTokens}
                />
                <MacroInflationSection
                    panel={data.panel}
                    derived={data.derived}
                    indexChartData={data.indexChartData}
                    panelInflationFromPanel={data.panelInflationFromPanel}
                    panelLoading={data.panelLoading}
                    locale={locale}
                    tokens={chartTokens}
                />
                <MacroPolicySection
                    panel={data.panel}
                    derived={data.derived}
                    panelLoading={data.panelLoading}
                    locale={locale}
                    tokens={chartTokens}
                />
                <MacroDepositSection
                    panel={data.panel}
                    derived={data.derived}
                    depositTryChart={data.depositTryChart}
                    usePanelDepositsTry={data.usePanelDepositsTry}
                    panelLoading={data.panelLoading}
                    locale={locale}
                    tokens={chartTokens}
                />
                <MacroLoansSection
                    panel={data.panel}
                    loanChart={data.loanChart}
                    usePanelLoans={data.usePanelLoans}
                    panelLoading={data.panelLoading}
                    locale={locale}
                    tokens={chartTokens}
                />
                <MacroBondsSection tokens={chartTokens} />
                <MacroEurobondSection tokens={chartTokens} />
                <MacroGlossarySection tokens={chartTokens} />
            </div>
        </InfoTermProvider>
    );
}
