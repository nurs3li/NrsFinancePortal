import { useEffect, useMemo, useState } from 'react';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useLanguage } from '../../../i18n/LanguageContext';
import { useTheme } from '../../../theme/ThemeContext';
import { chartGridStroke, chartTooltipContentStyle } from '../../../lib/chartTheme';
import { fetchInflationCompare, type InflationCompareRow, type MacroPanelDerivedMetrics } from '../../../services/marketDataService';
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

function fillTemplate(template: string, vars: Record<string, string>): string {
    return Object.entries(vars).reduce((s, [k, v]) => s.replaceAll(`{${k}}`, v), template);
}

function ymFromIsoDate(date: string | undefined | null): string | null {
    const raw = String(date ?? '').trim();
    if (!raw) return null;
    return raw.slice(0, 7);
}

export function MacroPolicySection({ panel, derived, panelLoading, locale, tokens }: Props) {
    const { t } = useLanguage();
    const { theme } = useTheme();
    const awaiting = '—';
    const fmt = (v: number | null | undefined) =>
        v == null || !Number.isFinite(Number(v)) ? awaiting : formatPercent2(v, locale);

    const polSeries = selectMacroSeries(panel?.series, 'POLICY_RATE_TR');
    const pol = lastObservation(polSeries);
    const funding = lastObservation(selectMacroSeries(panel?.series, 'TCMB_WEIGHTED_AVG_FUNDING_COST_TR'));
    const [inflationRows, setInflationRows] = useState<InflationCompareRow[] | null | undefined>(undefined);

    useEffect(() => {
        const observations = polSeries?.observations ?? [];
        const fromYm = ymFromIsoDate(observations[0]?.date);
        const toYm = ymFromIsoDate(observations[observations.length - 1]?.date);
        if (!fromYm || !toYm) {
            setInflationRows(null);
            return;
        }
        const ac = new AbortController();
        (async () => {
            setInflationRows(undefined);
            const response = await fetchInflationCompare(fromYm, toYm, ac.signal);
            if (ac.signal.aborted) return;
            setInflationRows(response?.rows ?? null);
        })();
        return () => ac.abort();
    }, [polSeries?.observations]);

    const policyChart = useMemo(() => {
        const inflationByMonth = new Map(
            (inflationRows ?? []).map((row) => [String(row.month ?? '').slice(0, 7), row.cpiAnnualChangePercent] as const),
        );
        return (polSeries?.observations ?? [])
            .map((o) => ({
                period: String(o.date).slice(0, 7),
                policy: Number(o.value),
                inflationRef: inflationByMonth.get(String(o.date).slice(0, 7)) ?? undefined,
            }))
            .filter((r) => Number.isFinite(r.policy))
            .slice(-48);
    }, [polSeries?.observations, inflationRows]);

    const realInsight =
        derived?.realPolicyRate != null && Number.isFinite(Number(derived.realPolicyRate))
            ? Number(derived.realPolicyRate) >= 0
                ? fillTemplate(
                      t(
                          'macro.policy.insight.realPos',
                          'Reel politika faizi pozitif ({value}); TL faizli araçlar görece cazip olabilir.',
                      ),
                      { value: fmt(derived.realPolicyRate) },
                  )
                : fillTemplate(
                      t(
                          'macro.policy.insight.realNeg',
                          'Reel politika faizi negatif ({value}); alım gücü baskısı sürüyor olabilir.',
                      ),
                      { value: fmt(derived.realPolicyRate) },
                  )
            : t('macro.policy.insight.realUnknown', 'Reel politika faizi hesaplanamadı.');

    return (
        <MacroSection
            id="macro-policy"
            title={t('macro.policy.title', 'Politika Faizi')}
            summary={t('macro.policy.summary', 'Politika faizi, TL piyasasındaki temel referans faizdir.')}
            termId="policyRate"
            infoAriaLabel={t('macro.policy.infoSection', 'Politika faizi bölümü hakkında bilgi')}
            tokens={tokens}
        >
            <div className="macro-grid macro-grid--3">
                <KpiCard
                    title={t('macro.policy.title', 'Politika Faizi')}
                    value={pol ? fmt(pol.value) : awaiting}
                    meta={pol?.date ? formatLocaleDate(pol.date, locale) : undefined}
                    termId="policyRate"
                    infoAriaLabel={t('macro.policy.infoPolicy', 'Politika faizi hakkında bilgi')}
                    tokens={tokens}
                    loading={panelLoading}
                />
                <KpiCard
                    title={t('macro.policy.kpi.funding', 'Ortalama Fonlama Maliyeti')}
                    value={funding ? fmt(funding.value) : awaiting}
                    meta={funding?.date ? formatLocaleDate(funding.date, locale) : undefined}
                    termId="fundingCost"
                    infoAriaLabel={t('macro.policy.infoFunding', 'Fonlama maliyeti hakkında bilgi')}
                    tokens={tokens}
                    loading={panelLoading}
                />
                <KpiCard
                    title={t('macro.overview.kpi.realPolicyRate', 'Reel Politika Faizi')}
                    value={fmt(derived?.realPolicyRate)}
                    termId="realPolicyRate"
                    infoAriaLabel={t('macro.policy.infoReal', 'Reel politika faizi hakkında bilgi')}
                    tokens={tokens}
                    loading={panelLoading}
                />
            </div>

            <div className="macro-grid macro-grid--8-4">
                <ChartCard
                    title={t('macro.policy.chart.title', 'Politika faizi ve enflasyon referansı')}
                    termId="policyRate"
                    infoAriaLabel={t('macro.policy.chart.info', 'Politika-enflasyon grafiği hakkında bilgi')}
                    empty={policyChart.length < 2}
                    tokens={tokens}
                >
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={policyChart} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke={chartGridStroke(theme)} />
                            <XAxis dataKey="period" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                            <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={42} />
                            <Tooltip
                                formatter={(v) => [`${Number(v).toFixed(2)}%`, '']}
                                contentStyle={chartTooltipContentStyle(tokens)}
                            />
                            <Legend wrapperStyle={{ fontSize: 10, color: tokens.textMuted }} />
                            <Line
                                type="monotone"
                                dataKey="policy"
                                name={t('macro.policy.chart.policy', 'Politika faizi')}
                                stroke={MACRO_CHART_COLORS.blue}
                                dot={false}
                                strokeWidth={2}
                                connectNulls
                            />
                            <Line
                                type="monotone"
                                dataKey="inflationRef"
                                name={t('macro.policy.chart.cpiYoY', 'TÜFE Yıllık')}
                                stroke={MACRO_CHART_COLORS.rose}
                                dot={false}
                                strokeDasharray="4 4"
                                strokeWidth={2}
                                connectNulls
                            />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>
                <InsightCard title={t('macro.policy.insight.cardTitle', 'Reel faiz durumu')} tokens={tokens} accent="cyan">
                    <p>{realInsight}</p>
                </InsightCard>
            </div>
        </MacroSection>
    );
}
