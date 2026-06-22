import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useLanguage } from '../../../i18n/LanguageContext';
import { useTheme } from '../../../theme/ThemeContext';
import { chartGridStroke, chartTooltipContentStyle } from '../../../lib/chartTheme';
import {
    formatBorrowingCostPercent,
    getLoanRatesHistory,
    getLoanRatesLatest,
    macroLoanRatesQueryKeys,
} from '../../../services/macroRatesApi';
import { formatLocaleDate, lastObservation, panelSpreadConsumerMinusDepositTry1m, selectMacroSeries } from '../../../utils/macroPanelSeries';
import { useInfoTerm } from '../education/InfoTermProvider';
import type { MacroIntelligencePanel } from '../hooks/useMacroIntelligenceData';
import { macroChartColorsForTheme } from '../MacroTheme';
import type { MacroTheme } from '../MacroTheme';
import { ChartCard } from '../primitives/ChartCard';
import { KpiCard } from '../primitives/KpiCard';
import { MacroSection } from '../primitives/MacroSection';

const LOAN_TYPES = [
    { ui: 'LOAN_RATE_CONSUMER_WEEKLY', col: 'CONSUMER_TRY', titleKey: 'macro.loans.type.consumer', titleFb: 'İhtiyaç Kredisi', legendKey: 'macro.loans.legend.consumer', legendFb: 'İhtiyaç', colorKey: 'rose' as const },
    { ui: 'LOAN_RATE_VEHICLE_WEEKLY', col: 'VEHICLE_TRY', titleKey: 'macro.loans.type.vehicle', titleFb: 'Taşıt Kredisi', legendKey: 'macro.loans.legend.vehicle', legendFb: 'Taşıt', colorKey: 'amber' as const },
    { ui: 'LOAN_RATE_HOUSING_WEEKLY', col: 'HOUSING_TRY', titleKey: 'macro.loans.type.housing', titleFb: 'Konut Kredisi', legendKey: 'macro.loans.legend.housing', legendFb: 'Konut', colorKey: 'blue' as const },
    { ui: 'LOAN_RATE_COMMERCIAL_WEEKLY', col: 'COMMERCIAL_TRY', titleKey: 'macro.loans.type.commercial', titleFb: 'Ticari Kredi', legendKey: 'macro.loans.legend.commercial', legendFb: 'Ticari', colorKey: 'violet' as const },
] as const;

const LOAN_RATES_TYPES_CSV = 'CONSUMER_TRY,VEHICLE_TRY,HOUSING_TRY,COMMERCIAL_TRY';

function loanHistoryRange(): { from: string; to: string } {
    const to = new Date();
    const from = new Date();
    from.setFullYear(from.getFullYear() - 2);
    return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

type Props = {
    panel: MacroIntelligencePanel;
    loanChart: Record<string, string | number>[];
    usePanelLoans: boolean;
    panelLoading: boolean;
    locale: string;
    tokens: MacroTheme;
};

export function MacroLoansSection({ panel, loanChart, usePanelLoans, panelLoading, locale, tokens }: Props) {
    const { t } = useLanguage();
    const { theme } = useTheme();
    const { openTerm } = useInfoTerm();
    const { from, to } = useMemo(() => loanHistoryRange(), []);

    const latestQ = useQuery({
        queryKey: macroLoanRatesQueryKeys.latest(),
        queryFn: ({ signal }) => getLoanRatesLatest(signal),
        enabled: !usePanelLoans,
    });
    const historyQ = useQuery({
        queryKey: macroLoanRatesQueryKeys.history(LOAN_RATES_TYPES_CSV, from, to),
        queryFn: ({ signal }) => getLoanRatesHistory({ types: LOAN_RATES_TYPES_CSV, from, to }, signal),
        enabled: !usePanelLoans,
    });

    type LoanKpi = { titleKey: string; titleFb: string; value: number; date: string };

    const panelItems = useMemo((): LoanKpi[] => {
        if (!usePanelLoans || !panel?.series) return [];
        return LOAN_TYPES.flatMap(({ ui, titleKey, titleFb }) => {
            const lo = lastObservation(selectMacroSeries(panel.series, ui));
            if (!lo) return [];
            return [{ titleKey, titleFb, value: lo.value, date: lo.date }];
        });
    }, [usePanelLoans, panel?.series]);

    const legacyItems = useMemo((): LoanKpi[] => {
        const items = latestQ.data?.items ?? [];
        const asOf = latestQ.data?.asOf ?? '';
        return LOAN_TYPES.flatMap(({ col, titleKey, titleFb }) => {
            const it = items.find((x) => String(x.type) === col);
            if (!it) return [];
            return [{ titleKey, titleFb, value: it.value, date: asOf }];
        });
    }, [latestQ.data]);

    const items = usePanelLoans ? panelItems : legacyItems;

    const compareChart = useMemo(() => {
        if (usePanelLoans) return loanChart;
        if (!historyQ.data?.series?.length) return [];
        const byDate = new Map<string, Record<string, string | number>>();
        for (const s of historyQ.data.series) {
            const typ = String(s.type ?? '');
            for (const p of s.points ?? []) {
                const d = String(p.date ?? '').slice(0, 10);
                if (!d) continue;
                const row = byDate.get(d) ?? { date: d };
                row[typ] = Number(p.value);
                byDate.set(d, row);
            }
        }
        return Array.from(byDate.values()).sort((a, b) => String(a.date).localeCompare(String(b.date)));
    }, [usePanelLoans, loanChart, historyQ.data]);

    const spread = usePanelLoans ? panelSpreadConsumerMinusDepositTry1m(panel?.series) : [];

    const loading = panelLoading || (!usePanelLoans && (latestQ.isPending || historyQ.isPending));
    const chartColors = macroChartColorsForTheme(theme);

    return (
        <MacroSection
            id="macro-loans"
            title={t('macro.loans.title', 'Kredi Faizleri')}
            summary={t('macro.loans.summary', 'Yeni açılan kredilere uygulanan ortalama faizler.')}
            termId="loanRate"
            infoAriaLabel={t('macro.loans.infoSection', 'Kredi faizleri bölümü hakkında bilgi')}
            tokens={tokens}
        >
            <p className="macro-callout macro-callout--warning">
                {t('macro.loans.warning', 'Kredi faizleri yatırım getirisi değil, borçlanma maliyetidir.')}{' '}
                <button type="button" className="macro-link-btn" onClick={() => openTerm('borrowingCost')}>
                    {t('macro.loans.detail', 'Detay')}
                </button>
            </p>

            <div className="macro-grid macro-grid--4">
                {LOAN_TYPES.map(({ titleKey, titleFb }) => {
                    const it = items.find((row) => row.titleKey === titleKey);
                    const title = t(titleKey, titleFb);
                    return (
                        <KpiCard
                            key={titleKey}
                            title={title}
                            value={it ? formatBorrowingCostPercent(it.value) : '—'}
                            meta={it?.date ? formatLocaleDate(it.date, locale) : undefined}
                            termId="loanRate"
                            infoAriaLabel={`${title} — ${t('macro.loans.infoSection', 'Kredi faizleri')}`}
                            tokens={tokens}
                            loading={loading}
                        />
                    );
                })}
            </div>

            <div className="macro-grid macro-grid--2">
                <ChartCard
                    title={t('macro.loans.chart.trends', 'Kredi faiz trendleri')}
                    termId="loanRate"
                    infoAriaLabel={t('macro.loans.chart.trendsInfo', 'Kredi trend grafiği')}
                    empty={compareChart.length < 2}
                    tokens={tokens}
                >
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={compareChart} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke={chartGridStroke(theme)} />
                            <XAxis dataKey="date" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                            <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={42} />
                            <Tooltip formatter={(v) => [`${Number(v).toFixed(2)}%`, '']} contentStyle={chartTooltipContentStyle(tokens)} />
                            <Legend wrapperStyle={{ fontSize: 10, color: tokens.textMuted }} />
                            {LOAN_TYPES.map((lt) => (
                                <Line
                                    key={lt.col}
                                    type="monotone"
                                    dataKey={lt.col}
                                    name={t(lt.legendKey, lt.legendFb)}
                                    stroke={chartColors[lt.colorKey]}
                                    dot={false}
                                    strokeWidth={theme === 'light' ? 2.5 : 2}
                                    connectNulls
                                />
                            ))}
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>

                <ChartCard
                    title={t('macro.loans.chart.spread', 'Kredi – mevduat makası')}
                    termId="creditSpread"
                    infoAriaLabel={t('macro.loans.chart.spreadInfo', 'Kredi mevduat makası hakkında bilgi')}
                    empty={spread.length < 2}
                    tokens={tokens}
                >
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={spread} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke={chartGridStroke(theme)} />
                            <XAxis dataKey="date" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                            <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={42} />
                            <Tooltip formatter={(v) => [`${Number(v).toFixed(2)}%`, '']} contentStyle={chartTooltipContentStyle(tokens)} />
                            <Line
                                type="monotone"
                                dataKey="spread"
                                name={t('macro.loans.legend.spread', 'Makas')}
                                stroke={chartColors.amber}
                                dot={false}
                                strokeWidth={theme === 'light' ? 2.5 : 2}
                                connectNulls
                            />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>
            </div>
        </MacroSection>
    );
}
