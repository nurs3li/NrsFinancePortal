import { useEffect, useMemo, useState } from 'react';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useLanguage } from '../../../i18n/LanguageContext';
import { useTheme } from '../../../theme/ThemeContext';
import { chartGridStroke, chartTooltipContentStyle } from '../../../lib/chartTheme';
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

const TERMS = [
    { id: '1M', titleKey: 'macro.deposit.term.1m', titleFb: 'TL 1 Ay', legendKey: 'macro.deposit.legend.1m', legendFb: '1 Ay', color: MACRO_CHART_COLORS.blue },
    { id: '3M', titleKey: 'macro.deposit.term.3m', titleFb: 'TL 3 Ay', legendKey: 'macro.deposit.legend.3m', legendFb: '3 Ay', color: MACRO_CHART_COLORS.cyan },
    { id: '6M', titleKey: 'macro.deposit.term.6m', titleFb: 'TL 6 Ay', legendKey: 'macro.deposit.legend.6m', legendFb: '6 Ay', color: MACRO_CHART_COLORS.violet },
    { id: '1Y', titleKey: 'macro.deposit.term.1y', titleFb: 'TL 1 Yıl', legendKey: 'macro.deposit.legend.1y', legendFb: '1 Yıl', color: MACRO_CHART_COLORS.green },
] as const;

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
    const { t } = useLanguage();
    const { theme } = useTheme();
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
            if (!TERMS.some((x) => x.id === term)) continue;
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
            title={t('macro.deposit.title', 'Mevduat')}
            summary={t('macro.deposit.summary', 'Vadeli TL mevduat faizleri ve reel getiri görünümü.')}
            termId="depositRate"
            infoAriaLabel={t('macro.deposit.infoSection', 'Mevduat bölümü hakkında bilgi')}
            tokens={tokens}
        >
            <div className="macro-grid macro-grid--4">
                {TERMS.map((term) => (
                    <KpiCard
                        key={term.id}
                        title={t(term.titleKey, term.titleFb)}
                        value={usePanelDepositsTry ? panelRate(`DEPOSIT_RATE_TRY_${term.id}_WEEKLY`) : fmtRate(term.id)}
                        termId={term.id === '1M' ? 'depositRate' : 'maturity'}
                        infoAriaLabel={t('macro.deposit.infoRate', 'TL mevduat faizi hakkında bilgi')}
                        tokens={tokens}
                        loading={panelLoading || rows === undefined}
                    />
                ))}
            </div>

            <div className="macro-grid macro-grid--8-4">
                <ChartCard
                    title={t('macro.deposit.chart.trend', 'Vade bazlı TL mevduat faiz trendi')}
                    termId="depositRate"
                    infoAriaLabel={t('macro.deposit.chart.trendInfo', 'Mevduat faiz grafiği hakkında bilgi')}
                    empty={!hasChart}
                    tokens={tokens}
                >
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={depositTryChart} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke={chartGridStroke(theme)} />
                            <XAxis dataKey="date" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                            <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={42} />
                            <Tooltip
                                formatter={(v) => [`${Number(v).toFixed(2)}%`, '']}
                                contentStyle={chartTooltipContentStyle(tokens)}
                            />
                            <Legend wrapperStyle={{ fontSize: 10, color: tokens.textMuted }} />
                            {TERMS.map((term) => (
                                <Line
                                    key={term.id}
                                    type="monotone"
                                    dataKey={`TRY_${term.id}`}
                                    name={t(term.legendKey, term.legendFb)}
                                    stroke={term.color}
                                    dot={false}
                                    strokeWidth={2}
                                    connectNulls
                                />
                            ))}
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>

                <InsightCard title={t('macro.deposit.insight.title', '100.000 TL örnek mevduat')} tokens={tokens} accent="green">
                    {scenario ? (
                        <>
                            <p>
                                {t('macro.deposit.insight.nominal', 'Yaklaşık nominal kazanç (1 ay):')}{' '}
                                <strong>
                                    {scenario.nominalGain.toLocaleString(locale, { maximumFractionDigits: 0 })} TL
                                </strong>
                            </p>
                            <p>
                                {t('macro.deposit.insight.real', 'Tahmini reel getiri (yıllık TÜFE ile):')}{' '}
                                <strong>{formatPercent2(scenario.real, locale)}</strong>
                            </p>
                        </>
                    ) : (
                        <p>{t('macro.deposit.insight.unavailable', 'Bu veri şu anda kullanılamıyor.')}</p>
                    )}
                </InsightCard>
            </div>

            {showFx ? (
                <details className="macro-advanced" open={fxOpen} onToggle={(e) => setFxOpen((e.target as HTMLDetailsElement).open)}>
                    <summary>{t('macro.deposit.fxAdvanced', 'Gelişmiş — Döviz mevduat faizleri')}</summary>
                    <FxDepositCharts panel={panel} tokens={tokens} t={t} theme={theme} />
                </details>
            ) : null}
        </MacroSection>
    );
}

function FxDepositCharts({
    panel,
    tokens,
    t,
    theme,
}: {
    panel: MacroIntelligencePanel;
    tokens: MacroTheme;
    t: (key: string, fallback?: string) => string;
    theme: 'light' | 'dark';
}) {
    const usd = mergeUsdDepositWeeklyChart(panel?.series);
    const eur = mergeEurDepositWeeklyChart(panel?.series);
    return (
        <div className="macro-grid macro-grid--2" style={{ marginTop: 12 }}>
            <ChartCard title={t('macro.deposit.fxUsd', 'USD mevduat')} empty={usd.length === 0} tokens={tokens} height={200}>
                <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={usd}>
                        <CartesianGrid strokeDasharray="3 3" stroke={chartGridStroke(theme)} />
                        <XAxis dataKey="date" tick={{ fontSize: 8, fill: tokens.textMuted }} />
                        <YAxis tickFormatter={(v) => `${v}%`} width={40} tick={{ fontSize: 8, fill: tokens.textMuted }} />
                        <Tooltip contentStyle={chartTooltipContentStyle(tokens)} formatter={(v) => [`${Number(v).toFixed(2)}%`, '']} />
                        <Line dataKey="USD_1M" stroke={MACRO_CHART_COLORS.blue} dot={false} connectNulls />
                    </LineChart>
                </ResponsiveContainer>
            </ChartCard>
            <ChartCard title={t('macro.deposit.fxEur', 'EUR mevduat')} empty={eur.length === 0} tokens={tokens} height={200}>
                <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={eur}>
                        <CartesianGrid strokeDasharray="3 3" stroke={chartGridStroke(theme)} />
                        <XAxis dataKey="date" tick={{ fontSize: 8, fill: tokens.textMuted }} />
                        <YAxis tickFormatter={(v) => `${v}%`} width={40} tick={{ fontSize: 8, fill: tokens.textMuted }} />
                        <Tooltip contentStyle={chartTooltipContentStyle(tokens)} formatter={(v) => [`${Number(v).toFixed(2)}%`, '']} />
                        <Line dataKey="EUR_1M" stroke={MACRO_CHART_COLORS.violet} dot={false} connectNulls />
                    </LineChart>
                </ResponsiveContainer>
            </ChartCard>
        </div>
    );
}
