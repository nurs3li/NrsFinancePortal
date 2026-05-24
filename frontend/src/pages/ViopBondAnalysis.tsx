import { useMemo, useState, type CSSProperties } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { bondPositionKeys } from '../queries/bondPositionKeys';
import { viopPositionKeys } from '../queries/viopPositionKeys';
import { getViopBondCombinedSummary, getBondSummary } from '../services/bondPositionApi';
import { getViopSummary } from '../services/viopPositionApi';
import { CombinedFinancialSummaryCards } from '../components/viopBond/CombinedFinancialSummaryCards';
import { ViopBondTabs } from '../components/viopBond/ViopBondTabs';
import { ViopAnalysisTab } from '../components/viopBond/ViopAnalysisTab';
import { BondAnalysisTab } from '../components/viopBond/BondAnalysisTab';
import { buildCombinedRiskReasons } from '../components/viopBond/bondAnalysisHelpers';
import './Portfolio.css';
import './ViopBondAnalysis.css';

export function ViopBondAnalysis() {
    const { tokens } = useTheme();
    const { t } = useLanguage();
    const [tab, setTab] = useState<'viop' | 'bond'>('viop');

    const { data: combined, isLoading: combinedLoading } = useQuery({
        queryKey: bondPositionKeys.combined(),
        queryFn: getViopBondCombinedSummary,
    });

    const { data: viopSummary } = useQuery({
        queryKey: viopPositionKeys.summary(),
        queryFn: getViopSummary,
    });

    const { data: bondSummary } = useQuery({
        queryKey: bondPositionKeys.summary(),
        queryFn: getBondSummary,
    });

    const openBondCount = bondSummary?.openPositionCount ?? 0;
    const openViopCount = viopSummary?.openPositionCount ?? 0;

    const totalPnl = (bondSummary?.totalPnl ?? 0) + (viopSummary?.totalUnrealizedPnl ?? 0);

    const riskStatus = useMemo(() => {
        const exposure = combined?.totalRiskExposure ?? 0;
        const effect = combined?.totalFinancialEffect ?? 0;
        if (effect <= 0) return t('viopBond.riskBalanced', 'Dengeli');
        const ratio = exposure / effect;
        if (ratio > 1.5) return t('viopBond.riskHigh', 'Yüksek Risk');
        if (ratio > 0.6) return t('viopBond.riskMedium', 'Orta Risk');
        return t('viopBond.riskBalanced', 'Dengeli');
    }, [combined, t]);

    const riskReasons = useMemo(
        () => buildCombinedRiskReasons(combined, riskStatus, openBondCount, openViopCount, t),
        [combined, riskStatus, openBondCount, openViopCount, t],
    );

    const pageStyle: CSSProperties = {
        background: tokens.bg,
        color: tokens.text,
    };

    const cardTokens = {
        border: tokens.border,
        bgCard: tokens.bgCard,
        textMuted: tokens.textMuted,
        text: tokens.text,
    };

    const pageVars = {
        ...pageStyle,
        '--tp-bg': tokens.bg,
        '--tp-card': tokens.bgCard,
        '--tp-border': tokens.border,
        '--tp-text': tokens.text,
        '--tp-muted': tokens.textMuted,
        '--vb-border': tokens.border,
        '--pf-text': tokens.text,
    } as CSSProperties;

    return (
        <div className="portfolio-page vb-page" style={pageVars}>
            <header className="vb-page-header">
                <h1 className="vb-hero-title">{t('viopBond.title', 'Vadeli Portföyüm')}</h1>
                <p className="vb-hero-sub" style={{ color: tokens.textMuted }}>
                    {t(
                        'viopBond.subtitle',
                        'Vadeli işlem ve sabit getirili ürünlerinizi vade, teminat, kupon ve risk maruziyetiyle takip edin.',
                    )}
                </p>
            </header>

            <CombinedFinancialSummaryCards
                summary={combined}
                loading={combinedLoading}
                tokens={cardTokens}
                totalPnl={totalPnl}
                riskStatus={riskStatus}
                riskReasons={riskReasons}
            />

            <section className="vb-tab-shell pf-card-premium" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                <div className="vb-tab-bar-wrap">
                    <div className="vb-tab-bar">
                        <ViopBondTabs
                            active={tab}
                            onChange={setTab}
                            viopLabel={t('viopBond.tabViop', 'VİOP')}
                            bondLabel={t('viopBond.tabBond', 'Tahvil & Bono')}
                            tokens={cardTokens}
                        />
                    </div>
                </div>
                <div className="vb-tab-body">
                    {tab === 'viop' ? <ViopAnalysisTab tokens={cardTokens} /> : <BondAnalysisTab tokens={cardTokens} />}
                </div>
            </section>
        </div>
    );
}
