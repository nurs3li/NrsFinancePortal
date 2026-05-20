import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import {
    formatBorrowingCostPercent,
    getLoanRatesHistory,
    getLoanRatesLatest,
    macroLoanRatesQueryKeys,
} from '../../../services/macroRatesApi';
import { formatLocaleDate, lastObservation, panelSpreadConsumerMinusDepositTry1m, selectMacroSeries } from '../../../utils/macroPanelSeries';
import { useInfoTerm } from '../education/InfoTermProvider';
import type { MacroIntelligencePanel } from '../hooks/useMacroIntelligenceData';
import { MACRO_CHART_COLORS } from '../MacroTheme';
import type { MacroTheme } from '../MacroTheme';
import { ChartCard } from '../primitives/ChartCard';
import { KpiCard } from '../primitives/KpiCard';
import { MacroSection } from '../primitives/MacroSection';

const LOAN_TYPES = [
    { ui: 'LOAN_RATE_CONSUMER_WEEKLY', col: 'CONSUMER_TRY', title: 'İhtiyaç Kredisi' },
    { ui: 'LOAN_RATE_VEHICLE_WEEKLY', col: 'VEHICLE_TRY', title: 'Taşıt Kredisi' },
    { ui: 'LOAN_RATE_HOUSING_WEEKLY', col: 'HOUSING_TRY', title: 'Konut Kredisi' },
    { ui: 'LOAN_RATE_COMMERCIAL_WEEKLY', col: 'COMMERCIAL_TRY', title: 'Ticari Kredi' },
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

    type LoanKpi = { title: string; value: number; date: string };

    const panelItems = useMemo((): LoanKpi[] => {
        if (!usePanelLoans || !panel?.series) return [];
        return LOAN_TYPES.flatMap(({ ui, title }) => {
            const lo = lastObservation(selectMacroSeries(panel.series, ui));
            if (!lo) return [];
            return [{ title, value: lo.value, date: lo.date }];
        });
    }, [usePanelLoans, panel?.series]);

    const legacyItems = useMemo((): LoanKpi[] => {
        const items = latestQ.data?.items ?? [];
        const asOf = latestQ.data?.asOf ?? '';
        return LOAN_TYPES.flatMap(({ col, title }) => {
            const it = items.find((x) => String(x.type) === col);
            if (!it) return [];
            return [{ title, value: it.value, date: asOf }];
        });
    }, [latestQ.data]);

    const items = usePanelLoans ? panelItems : legacyItems;

    const compareChart = useMemo(() => {
        if (usePanelLoans) return loanChart;
        if (!historyQ.data?.series?.length) return [];
        const byDate = new Map<string, Record<string, string | number>>();
        for (const s of historyQ.data.series) {
            const t = String(s.type ?? '');
            for (const p of s.points ?? []) {
                const d = String(p.date ?? '').slice(0, 10);
                if (!d) continue;
                const row = byDate.get(d) ?? { date: d };
                row[t] = Number(p.value);
                byDate.set(d, row);
            }
        }
        return Array.from(byDate.values()).sort((a, b) => String(a.date).localeCompare(String(b.date)));
    }, [usePanelLoans, loanChart, historyQ.data]);

    const spread = usePanelLoans
        ? panelSpreadConsumerMinusDepositTry1m(panel?.series)
        : [];

    const loading = panelLoading || (!usePanelLoans && (latestQ.isPending || historyQ.isPending));

    return (
        <MacroSection
            id="macro-loans"
            title="Kredi Faizleri"
            summary="Yeni açılan kredilere uygulanan ortalama faizler."
            termId="loanRate"
            infoAriaLabel="Kredi faizleri bölümü hakkında bilgi"
            tokens={tokens}
        >
            <p className="macro-callout macro-callout--warning">
                Kredi faizleri yatırım getirisi değil, borçlanma maliyetidir.{' '}
                <button type="button" className="macro-link-btn" onClick={() => openTerm('borrowingCost')}>
                    Detay
                </button>
            </p>

            <div className="macro-grid macro-grid--4">
                {LOAN_TYPES.map(({ title }) => {
                    const it = items.find((row) => row.title === title);
                    return (
                        <KpiCard
                            key={title}
                            title={title}
                            value={it ? formatBorrowingCostPercent(it.value) : '—'}
                            meta={it?.date ? formatLocaleDate(it.date, locale) : undefined}
                            termId="loanRate"
                            infoAriaLabel={`${title} hakkında bilgi`}
                            tokens={tokens}
                            loading={loading}
                        />
                    );
                })}
            </div>

            <div className="macro-grid macro-grid--2">
                <ChartCard title="Kredi faiz trendleri" termId="loanRate" infoAriaLabel="Kredi trend grafiği" empty={compareChart.length < 2} tokens={tokens}>
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={compareChart} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
                            <XAxis dataKey="date" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                            <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={42} />
                            <Tooltip formatter={(v) => [`${Number(v).toFixed(2)}%`, '']} />
                            <Legend wrapperStyle={{ fontSize: 10 }} />
                            <Line type="monotone" dataKey="CONSUMER_TRY" name="İhtiyaç" stroke={MACRO_CHART_COLORS.rose} dot={false} strokeWidth={2} connectNulls />
                            <Line type="monotone" dataKey="VEHICLE_TRY" name="Taşıt" stroke={MACRO_CHART_COLORS.amber} dot={false} strokeWidth={2} connectNulls />
                            <Line type="monotone" dataKey="HOUSING_TRY" name="Konut" stroke={MACRO_CHART_COLORS.blue} dot={false} strokeWidth={2} connectNulls />
                            <Line type="monotone" dataKey="COMMERCIAL_TRY" name="Ticari" stroke={MACRO_CHART_COLORS.violet} dot={false} strokeWidth={2} connectNulls />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>

                <ChartCard
                    title="Kredi – mevduat makası"
                    termId="creditSpread"
                    infoAriaLabel="Kredi mevduat makası hakkında bilgi"
                    empty={spread.length < 2}
                    tokens={tokens}
                >
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={spread} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
                            <XAxis dataKey="date" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                            <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={42} />
                            <Tooltip formatter={(v) => [`${Number(v).toFixed(2)}%`, '']} />
                            <Line type="monotone" dataKey="spread" name="Makas" stroke={MACRO_CHART_COLORS.amber} dot={false} strokeWidth={2} connectNulls />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>
            </div>
        </MacroSection>
    );
}
