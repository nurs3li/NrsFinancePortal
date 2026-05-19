import { useMemo } from 'react';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatLocaleDate, formatPercent2, lastObservation, selectMacroSeries } from '../../../utils/macroPanelSeries';
import { MACRO_CHART_COLORS } from '../MacroTheme';
import type { MacroIntelligencePanel } from '../hooks/useMacroIntelligenceData';
import { InsightCard } from '../primitives/InsightCard';
import { MacroSection } from '../primitives/MacroSection';
import { ChartCard } from '../primitives/ChartCard';
import { KpiCard } from '../primitives/KpiCard';
import type { MacroTheme } from '../MacroTheme';
import type { MacroPanelDerivedMetrics } from '../../../services/marketDataService';

type Props = {
    panel: MacroIntelligencePanel;
    derived: MacroPanelDerivedMetrics | undefined;
    panelLoading: boolean;
    locale: string;
    tokens: MacroTheme;
};

export function MacroOverviewSection({ panel, derived, panelLoading, locale, tokens }: Props) {
    const awaiting = '—';
    const fmt = (v: number | null | undefined) =>
        v == null || !Number.isFinite(Number(v)) ? awaiting : formatPercent2(v, locale);

    const pol = lastObservation(selectMacroSeries(panel?.series, 'POLICY_RATE_TR'));
    const insights = useMemo(() => {
        const bullets: string[] = [];
        if (derived?.cpiYoY != null && pol?.value != null) {
            const gap = Number(pol.value) - Number(derived.cpiYoY);
            bullets.push(
                gap >= 0
                    ? `Politika faizi (${fmt(pol.value)}) TÜFE yıllık (${fmt(derived.cpiYoY)}) üzerinde — TL faiz ortamı sıkı görünüyor.`
                    : `Politika faizi TÜFE yıllığın altında — reel faiz baskısı TL varlıklarını zorlayabilir.`,
            );
        }
        if (derived?.realPolicyRate != null) {
            bullets.push(
                Number(derived.realPolicyRate) >= 0
                    ? `Reel politika faizi pozitif (${fmt(derived.realPolicyRate)}).`
                    : `Reel politika faizi negatif (${fmt(derived.realPolicyRate)}).`,
            );
        }
        if (derived?.realDepositRate != null) {
            bullets.push(
                Number(derived.realDepositRate) >= 0
                    ? `1 ay TL mevduat reel farkı pozitif (${fmt(derived.realDepositRate)}).`
                    : `1 ay TL mevduat reel farkı negatif (${fmt(derived.realDepositRate)}).`,
            );
        }
        return bullets.slice(0, 3);
    }, [derived, pol, locale]);

    const miniChart = useMemo(() => {
        const cpi = selectMacroSeries(panel?.series, 'CPI_TR_INDEX');
        const policy = selectMacroSeries(panel?.series, 'POLICY_RATE_TR');
        const dep = selectMacroSeries(panel?.series, 'DEPOSIT_RATE_TRY_1M_WEEKLY');
        const byMonth = new Map<string, { period: string; policy?: number; deposit?: number }>();
        for (const o of policy?.observations ?? []) {
            const k = String(o.date).slice(0, 7);
            if (!k) continue;
            const row = byMonth.get(k) ?? { period: k };
            if (Number.isFinite(Number(o.value))) row.policy = Number(o.value);
            byMonth.set(k, row);
        }
        for (const o of dep?.observations ?? []) {
            const k = String(o.date).slice(0, 7);
            if (!k) continue;
            const row = byMonth.get(k) ?? { period: k };
            if (Number.isFinite(Number(o.value))) row.deposit = Number(o.value);
            byMonth.set(k, row);
        }
        const cpiYoY = derived?.cpiYoY;
        return {
            rows: Array.from(byMonth.values()).sort((a, b) => a.period.localeCompare(b.period)).slice(-36),
            cpiYoY,
            hasCpi: (cpi?.observations?.length ?? 0) > 0,
        };
    }, [panel?.series, derived?.cpiYoY]);

    return (
        <MacroSection
            id="macro-overview"
            title="Genel Bakış"
            summary="Makro ortamın kısa özeti."
            tokens={tokens}
        >
            <div className="macro-grid macro-grid--3">
                <KpiCard
                    title="TÜFE Yıllık"
                    value={fmt(derived?.cpiYoY)}
                    meta={formatLocaleDate(lastObservation(selectMacroSeries(panel?.series, 'CPI_TR_INDEX'))?.date, locale)}
                    termId="cpi"
                    infoAriaLabel="TÜFE hakkında bilgi"
                    tokens={tokens}
                    loading={panelLoading}
                />
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
                    title="Reel Politika Faizi"
                    value={fmt(derived?.realPolicyRate)}
                    termId="realPolicyRate"
                    infoAriaLabel="Reel politika faizi hakkında bilgi"
                    tokens={tokens}
                    loading={panelLoading}
                />
            </div>

            <div className="macro-grid macro-grid--8-4 macro-section__row">
                <ChartCard
                    title="Faiz ortamı karşılaştırması"
                    empty={miniChart.rows.length < 2}
                    tokens={tokens}
                    height={220}
                >
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={miniChart.rows} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
                            <XAxis dataKey="period" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                            <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={42} />
                            <Tooltip formatter={(v) => [`${Number(v).toFixed(2)}%`, '']} />
                            <Legend wrapperStyle={{ fontSize: 10 }} />
                            <Line type="monotone" dataKey="policy" name="Politika" stroke={MACRO_CHART_COLORS.blue} dot={false} strokeWidth={2} connectNulls />
                            <Line type="monotone" dataKey="deposit" name="TL 1M Mevduat" stroke={MACRO_CHART_COLORS.green} dot={false} strokeWidth={2} connectNulls />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>

                <InsightCard title="Bugün ne görüyoruz?" tokens={tokens}>
                    {insights.length === 0 ? (
                        <p>Özet göstergeler yüklendiğinde kısa yorumlar burada görünür.</p>
                    ) : (
                        <ul className="macro-insight-list">
                            {insights.map((line) => (
                                <li key={line}>{line}</li>
                            ))}
                        </ul>
                    )}
                    {miniChart.cpiYoY != null ? (
                        <p className="macro-insight__footnote">
                            Güncel TÜFE yıllık: <strong>{fmt(miniChart.cpiYoY)}</strong>
                        </p>
                    ) : null}
                </InsightCard>
            </div>
        </MacroSection>
    );
}
