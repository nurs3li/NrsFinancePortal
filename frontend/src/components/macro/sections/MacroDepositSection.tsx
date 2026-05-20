import { useEffect, useMemo, useState } from 'react';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { fetchDepositRatesLatest, type DepositRateLatestRow } from '../../../services/marketDataService';
import {
    formatPercent2,
    hasFxDepositPanelData,
    lastObservation,
    mergeEurDepositWeeklyChart,
    mergeUsdDepositWeeklyChart,
    selectMacroSeries,
} from '../../../utils/macroPanelSeries';
import type { MacroIntelligencePanel } from '../hooks/useMacroIntelligenceData';
import { MACRO_CHART_COLORS } from '../MacroTheme';
import type { MacroTheme } from '../MacroTheme';
import type { MacroPanelDerivedMetrics } from '../../../services/marketDataService';
import { ChartCard } from '../primitives/ChartCard';
import { InsightCard } from '../primitives/InsightCard';
import { KpiCard } from '../primitives/KpiCard';
import { MacroSection } from '../primitives/MacroSection';

const TERMS = ['1M', '3M', '6M', '1Y'] as const;

type Props = {
    panel: MacroIntelligencePanel;
    derived: MacroPanelDerivedMetrics | undefined;
    depositTryChart: Record<string, string | number>[];
    usePanelDepositsTry: boolean;
    panelLoading: boolean;
    locale: string;
    tokens: MacroTheme;
};

export function MacroDepositSection({
    panel,
    derived,
    depositTryChart,
    usePanelDepositsTry,
    panelLoading,
    locale,
    tokens,
}: Props) {
    const [rows, setRows] = useState<DepositRateLatestRow[] | null | undefined>(undefined);
    const [fxOpen, setFxOpen] = useState(false);

    useEffect(() => {
        const ac = new AbortController();
        (async () => {
            setRows(undefined);
            const data = await fetchDepositRatesLatest(ac.signal);
            if (!ac.signal.aborted) setRows(data === null ? null : data);
        })();
        return () => ac.abort();
    }, []);

    const byKey = useMemo(() => {
        const m = new Map<string, DepositRateLatestRow>();
        if (!rows) return m;
        for (const r of rows) {
            const term = String(r.term ?? '').toUpperCase();
            if (!TERMS.includes(term as (typeof TERMS)[number])) continue;
            m.set(`${String(r.currency ?? '').toUpperCase()}_${term}`, r);
        }
        return m;
    }, [rows]);

    const fmtRate = (term: string) => {
        const r = byKey.get(`TRY_${term}`);
        if (r == null || r.ratePercent == null || !Number.isFinite(Number(r.ratePercent))) return '—';
        return `${Number(r.ratePercent).toLocaleString(locale, { maximumFractionDigits: 2 })}%`;
    };

    const panelRate = (lk: string) => {
        const o = lastObservation(selectMacroSeries(panel?.series, lk));
        return o != null && Number.isFinite(Number(o.value)) ? formatPercent2(Number(o.value), locale) : '—';
    };

    const hasChart = usePanelDepositsTry && depositTryChart.length > 0;
    const showFx = hasFxDepositPanelData(panel?.series);

    const scenario = useMemo(() => {
        const nominalPct = lastObservation(selectMacroSeries(panel?.series, 'DEPOSIT_RATE_TRY_1M_WEEKLY'))?.value;
        const infl = derived?.cpiYoY;
        if (nominalPct == null || infl == null) return null;
        const n = Number(nominalPct) / 100;
        const i = Number(infl) / 100;
        const real = ((1 + n) / (1 + i) - 1) * 100;
        const nominalGain = 100_000 * n;
        return { nominalGain, real };
    }, [panel?.series, derived?.cpiYoY]);

    return (
        <MacroSection
            id="macro-deposit"
            title="Mevduat"
            summary="Vadeli TL mevduat faizleri ve reel getiri görünümü."
            termId="depositRate"
            infoAriaLabel="Mevduat bölümü hakkında bilgi"
            tokens={tokens}
        >
            <div className="macro-grid macro-grid--4">
                {TERMS.map((term) => (
                    <KpiCard
                        key={term}
                        title={`TL ${term === '1M' ? '1 Ay' : term === '3M' ? '3 Ay' : term === '6M' ? '6 Ay' : '1 Yıl'}`}
                        value={usePanelDepositsTry ? panelRate(`DEPOSIT_RATE_TRY_${term}_WEEKLY`) : fmtRate(term)}
                        termId={term === '1M' ? 'depositRate' : 'maturity'}
                        infoAriaLabel="TL mevduat faizi hakkında bilgi"
                        tokens={tokens}
                        loading={panelLoading || rows === undefined}
                    />
                ))}
            </div>

            <div className="macro-grid macro-grid--8-4">
                <ChartCard
                    title="Vade bazlı TL mevduat faiz trendi"
                    termId="depositRate"
                    infoAriaLabel="Mevduat faiz grafiği hakkında bilgi"
                    empty={!hasChart}
                    tokens={tokens}
                >
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={depositTryChart} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
                            <XAxis dataKey="date" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                            <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={42} />
                            <Tooltip formatter={(v) => [`${Number(v).toFixed(2)}%`, '']} />
                            <Legend wrapperStyle={{ fontSize: 10 }} />
                            <Line type="monotone" dataKey="TRY_1M" name="1 Ay" stroke={MACRO_CHART_COLORS.blue} dot={false} strokeWidth={2} connectNulls />
                            <Line type="monotone" dataKey="TRY_3M" name="3 Ay" stroke={MACRO_CHART_COLORS.cyan} dot={false} strokeWidth={2} connectNulls />
                            <Line type="monotone" dataKey="TRY_6M" name="6 Ay" stroke={MACRO_CHART_COLORS.violet} dot={false} strokeWidth={2} connectNulls />
                            <Line type="monotone" dataKey="TRY_1Y" name="1 Yıl" stroke={MACRO_CHART_COLORS.green} dot={false} strokeWidth={2} connectNulls />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>

                <InsightCard title="100.000 TL örnek mevduat" tokens={tokens} accent="green">
                    {scenario ? (
                        <>
                            <p>
                                Yaklaşık nominal kazanç (1 ay):{' '}
                                <strong>
                                    {scenario.nominalGain.toLocaleString(locale, { maximumFractionDigits: 0 })} TL
                                </strong>
                            </p>
                            <p>
                                Tahmini reel getiri (yıllık TÜFE ile):{' '}
                                <strong>{formatPercent2(scenario.real, locale)}</strong>
                            </p>
                        </>
                    ) : (
                        <p>Bu veri şu anda kullanılamıyor.</p>
                    )}
                </InsightCard>
            </div>

            {showFx ? (
                <details className="macro-advanced" open={fxOpen} onToggle={(e) => setFxOpen((e.target as HTMLDetailsElement).open)}>
                    <summary>Gelişmiş — Döviz mevduat faizleri</summary>
                    <FxDepositCharts panel={panel} tokens={tokens} />
                </details>
            ) : null}
        </MacroSection>
    );
}

function FxDepositCharts({ panel, tokens }: { panel: MacroIntelligencePanel; tokens: MacroTheme }) {
    const usd = mergeUsdDepositWeeklyChart(panel?.series);
    const eur = mergeEurDepositWeeklyChart(panel?.series);
    return (
        <div className="macro-grid macro-grid--2" style={{ marginTop: 12 }}>
            <ChartCard title="USD mevduat" empty={usd.length === 0} tokens={tokens} height={200}>
                <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={usd}>
                        <XAxis dataKey="date" tick={{ fontSize: 8, fill: tokens.textMuted }} />
                        <YAxis tickFormatter={(v) => `${v}%`} width={40} tick={{ fontSize: 8, fill: tokens.textMuted }} />
                        <Tooltip formatter={(v) => [`${Number(v).toFixed(2)}%`, '']} />
                        <Line dataKey="USD_1M" stroke={MACRO_CHART_COLORS.blue} dot={false} connectNulls />
                    </LineChart>
                </ResponsiveContainer>
            </ChartCard>
            <ChartCard title="EUR mevduat" empty={eur.length === 0} tokens={tokens} height={200}>
                <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={eur}>
                        <XAxis dataKey="date" tick={{ fontSize: 8, fill: tokens.textMuted }} />
                        <YAxis tickFormatter={(v) => `${v}%`} width={40} tick={{ fontSize: 8, fill: tokens.textMuted }} />
                        <Tooltip formatter={(v) => [`${Number(v).toFixed(2)}%`, '']} />
                        <Line dataKey="EUR_1M" stroke={MACRO_CHART_COLORS.violet} dot={false} connectNulls />
                    </LineChart>
                </ResponsiveContainer>
            </ChartCard>
        </div>
    );
}
