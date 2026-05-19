import { useMemo } from 'react';
import { useLanguage } from '../../../i18n/LanguageContext';
import type { MacroPanelDerivedMetrics } from '../../../services/marketDataService';
import { formatLocaleDate, formatPercent2, lastObservation, selectMacroSeries } from '../../../utils/macroPanelSeries';
import type { MacroIntelligencePanel } from '../hooks/useMacroIntelligenceData';
import { KpiCard, type KpiStatus } from '../primitives/KpiCard';
import type { MacroTheme } from '../MacroTheme';

type Props = {
    panel: MacroIntelligencePanel;
    derived: MacroPanelDerivedMetrics | undefined;
    panelLoading: boolean;
    tokens: MacroTheme;
};

function fmtDerived(v: number | null | undefined, locale: string, awaiting: string) {
    if (v == null || !Number.isFinite(Number(v))) return awaiting;
    return formatPercent2(v, locale);
}

function statusFromSigned(v: number | null | undefined): KpiStatus | undefined {
    if (v == null || !Number.isFinite(Number(v))) return undefined;
    if (v > 0.25) return 'positive';
    if (v < -0.25) return 'negative';
    return 'neutral';
}

export function MacroSummaryKpiStrip({ panel, derived, panelLoading, tokens }: Props) {
    const { lang } = useLanguage();
    const locale = lang === 'en' ? 'en-GB' : 'tr-TR';
    const awaiting = '—';

    const pol = useMemo(() => lastObservation(selectMacroSeries(panel?.series, 'POLICY_RATE_TR')), [panel?.series]);
    const funding = useMemo(
        () => lastObservation(selectMacroSeries(panel?.series, 'TCMB_WEIGHTED_AVG_FUNDING_COST_TR')),
        [panel?.series],
    );
    const cpiD = useMemo(() => lastObservation(selectMacroSeries(panel?.series, 'CPI_TR_INDEX'))?.date, [panel?.series]);
    const depD = useMemo(
        () => lastObservation(selectMacroSeries(panel?.series, 'DEPOSIT_RATE_TRY_1M_WEEKLY'))?.date,
        [panel?.series],
    );

    const kpis = [
        {
            title: 'TÜFE Yıllık',
            value: fmtDerived(derived?.cpiYoY, locale, awaiting),
            meta: cpiD ? `${formatLocaleDate(cpiD, locale)} · Aylık` : undefined,
            termId: 'yoy' as const,
            infoAriaLabel: 'TÜFE yıllık değişim hakkında bilgi',
        },
        {
            title: 'Politika Faizi',
            value:
                pol != null && Number.isFinite(Number(pol.value))
                    ? formatPercent2(Number(pol.value), locale)
                    : awaiting,
            meta: pol?.date ? `${formatLocaleDate(pol.date, locale)} · Aylık` : undefined,
            termId: 'policyRate' as const,
            infoAriaLabel: 'Politika faizi hakkında bilgi',
        },
        {
            title: 'Reel Politika Faizi',
            value: fmtDerived(derived?.realPolicyRate, locale, awaiting),
            meta: pol?.date ? formatLocaleDate(pol.date, locale) : undefined,
            status: statusFromSigned(derived?.realPolicyRate ?? null),
            termId: 'realPolicyRate' as const,
            infoAriaLabel: 'Reel politika faizi hakkında bilgi',
        },
        {
            title: '1 Ay TL Mevduat Reel Farkı',
            value: fmtDerived(derived?.realDepositRate, locale, awaiting),
            meta: depD ? `${formatLocaleDate(depD, locale)} · Haftalık` : undefined,
            status: statusFromSigned(derived?.realDepositRate ?? null),
            termId: 'realReturn' as const,
            infoAriaLabel: 'Reel mevduat farkı hakkında bilgi',
        },
        {
            title: 'Ortalama Fonlama Maliyeti',
            value:
                funding != null && Number.isFinite(Number(funding.value))
                    ? formatPercent2(Number(funding.value), locale)
                    : awaiting,
            meta: funding?.date ? formatLocaleDate(funding.date, locale) : undefined,
            termId: 'fundingCost' as const,
            infoAriaLabel: 'Ortalama fonlama maliyeti hakkında bilgi',
        },
    ];

    return (
        <section className="macro-summary-strip" aria-label="Özet göstergeler">
            {kpis.map((k) => (
                <KpiCard
                    key={k.title}
                    title={k.title}
                    value={k.value}
                    meta={k.meta}
                    status={k.status}
                    termId={k.termId}
                    infoAriaLabel={k.infoAriaLabel}
                    tokens={tokens}
                    loading={panelLoading}
                />
            ))}
        </section>
    );
}
