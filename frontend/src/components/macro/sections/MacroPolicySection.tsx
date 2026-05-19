import { useMemo } from 'react';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { MacroPanelDerivedMetrics } from '../../../services/marketDataService';
import { formatLocaleDate, formatPercent2, lastObservation, selectMacroSeries } from '../../../utils/macroPanelSeries';
import type { MacroIntelligencePanel } from '../hooks/useMacroIntelligenceData';
import { MACRO_CHART_COLORS } from '../MacroTheme';
import type { MacroTheme } from '../MacroTheme';
import { ChartCard } from '../primitives/ChartCard';
import { InsightCard } from '../primitives/InsightCard';
import { KpiCard } from '../primitives/KpiCard';
import { MacroSection } from '../primitives/MacroSection';

type Props = {
    panel: MacroIntelligencePanel;
    derived: MacroPanelDerivedMetrics | undefined;
    panelLoading: boolean;
    locale: string;
    tokens: MacroTheme;
};

export function MacroPolicySection({ panel, derived, panelLoading, locale, tokens }: Props) {
    const awaiting = '—';
    const fmt = (v: number | null | undefined) =>
        v == null || !Number.isFinite(Number(v)) ? awaiting : formatPercent2(v, locale);

    const polSeries = selectMacroSeries(panel?.series, 'POLICY_RATE_TR');
    const pol = lastObservation(polSeries);
    const funding = lastObservation(selectMacroSeries(panel?.series, 'TCMB_WEIGHTED_AVG_FUNDING_COST_TR'));

    const policyChart = useMemo(() => {
        const cpiYoY = derived?.cpiYoY;
        return (polSeries?.observations ?? [])
            .map((o) => ({
                period: String(o.date).slice(0, 7),
                policy: Number(o.value),
                inflationRef: cpiYoY != null && Number.isFinite(Number(cpiYoY)) ? Number(cpiYoY) : undefined,
            }))
            .filter((r) => Number.isFinite(r.policy))
            .slice(-48);
    }, [polSeries?.observations, derived?.cpiYoY]);

    const realInsight =
        derived?.realPolicyRate != null && Number.isFinite(Number(derived.realPolicyRate))
            ? Number(derived.realPolicyRate) >= 0
                ? `Reel politika faizi pozitif (${fmt(derived.realPolicyRate)}); TL faizli araçlar görece cazip olabilir.`
                : `Reel politika faizi negatif (${fmt(derived.realPolicyRate)}); alım gücü baskısı sürüyor olabilir.`
            : 'Reel politika faizi hesaplanamadı.';

    return (
        <MacroSection
            id="macro-policy"
            title="Politika Faizi"
            summary="Politika faizi, TL piyasasındaki temel referans faizdir."
            termId="policyRate"
            infoAriaLabel="Politika faizi bölümü hakkında bilgi"
            tokens={tokens}
        >
            <div className="macro-grid macro-grid--3">
                <KpiCard
                    title="Politika Faizi"
                    value={pol ? fmt(pol.value) : awaiting}
                    meta={pol?.date ? formatLocaleDate(pol.date, locale) : undefined}
                    termId="policyRate"
                    infoAriaLabel="Politika faizi hakkında bilgi"
                    tokens={tokens}
                    loading={panelLoading}
                />
                <KpiCard
                    title="Ortalama Fonlama Maliyeti"
                    value={funding ? fmt(funding.value) : awaiting}
                    meta={funding?.date ? formatLocaleDate(funding.date, locale) : undefined}
                    termId="fundingCost"
                    infoAriaLabel="Fonlama maliyeti hakkında bilgi"
                    tokens={tokens}
                    loading={panelLoading}
                />
                <KpiCard
                    title="Reel Politika Faizi"
                    value={fmt(derived?.realPolicyRate)}
                    termId="realPolicyRate"
                    infoAriaLabel="Reel politika faizi hakkında bilgi"
                    tokens={tokens}
                    loading={panelLoading}
                />
            </div>

            <div className="macro-grid macro-grid--8-4">
                <ChartCard
                    title="Politika faizi ve enflasyon referansı"
                    termId="policyRate"
                    infoAriaLabel="Politika-enflasyon grafiği hakkında bilgi"
                    empty={policyChart.length < 2}
                    tokens={tokens}
                >
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={policyChart} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
                            <XAxis dataKey="period" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                            <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={42} />
                            <Tooltip formatter={(v) => [`${Number(v).toFixed(2)}%`, '']} />
                            <Legend wrapperStyle={{ fontSize: 10 }} />
                            <Line type="monotone" dataKey="policy" name="Politika faizi" stroke={MACRO_CHART_COLORS.blue} dot={false} strokeWidth={2} connectNulls />
                            <Line type="monotone" dataKey="inflationRef" name="TÜFE Yıllık (güncel)" stroke={MACRO_CHART_COLORS.rose} dot={false} strokeDasharray="4 4" strokeWidth={2} connectNulls />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>
                <InsightCard title="Reel faiz durumu" tokens={tokens} accent="cyan">
                    <p>{realInsight}</p>
                </InsightCard>
            </div>
        </MacroSection>
    );
}
