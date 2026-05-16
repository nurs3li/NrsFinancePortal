import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { useLanguage } from '../../i18n/LanguageContext';
import { fetchInterestInflationMacroPanel } from '../../services/marketDataService';
import {
    DEPOSIT_TRY_CHART_LOGICAL_KEYS,
    mergeDepositTryChart,
    mergeIndexLevelChart,
} from '../../utils/macroPanelSeries';

type Props = {
    tokens: { bgCard: string; border: string; text: string; textMuted: string };
};

export function MarketMacroRatesChartCard({ tokens }: Props) {
    const { t } = useLanguage();

    const { data: panel, isLoading } = useQuery({
        queryKey: ['market', 'macro', 'interest-inflation-panel'],
        queryFn: ({ signal }) => fetchInterestInflationMacroPanel(signal),
        staleTime: 60_000,
    });

    const indexChart = useMemo(() => mergeIndexLevelChart(panel?.series), [panel?.series]);
    const depositChart = useMemo(
        () => mergeDepositTryChart(panel?.series, [...DEPOSIT_TRY_CHART_LOGICAL_KEYS]),
        [panel?.series],
    );

    const merged = useMemo(() => {
        const byMonth = new Map<string, Record<string, string | number>>();
        for (const row of indexChart) {
            const k = String(row.period);
            const cur = byMonth.get(k) ?? { period: k };
            if (row.cpi != null) cur.cpiIndex = row.cpi;
            if (row.ppi != null) cur.ppiIndex = row.ppi;
            byMonth.set(k, cur);
        }
        for (const row of depositChart) {
            const d = String(row.date).slice(0, 7);
            if (!d) continue;
            const cur = byMonth.get(d) ?? { period: d };
            const v = row.TRY_1M;
            if (typeof v === 'number' && Number.isFinite(v)) cur.deposit1m = v;
            byMonth.set(d, cur);
        }
        return [...byMonth.values()].sort((a, b) => String(a.period).localeCompare(String(b.period)));
    }, [indexChart, depositChart]);

    const hasData = merged.some(
        (r) => Number.isFinite(Number(r.cpiIndex)) || Number.isFinite(Number(r.deposit1m)),
    );

    return (
        <section className="terminal-card terminal-macro-chart-card" style={{ borderColor: tokens.border }}>
            <h3 className="terminal-macro-card__title" style={{ color: tokens.text }}>
                {t('market.macro.ratesChartTitle', 'Enflasyon endeksi ve TL mevduat faizi')}
            </h3>
            <p className="terminal-macro-card__muted" style={{ color: tokens.textMuted, margin: '0 0 10px' }}>
                {t(
                    'market.macro.ratesChartSubtitle',
                    'TÜFE endeks seviyesi (sol) ve yeni açılan TL 1 ay mevduat faizi % (sağ, haftalık akım).',
                )}
            </p>
            {isLoading ? (
                <div className="terminal-chart-empty">{t('market.loading', 'Yükleniyor...')}</div>
            ) : !hasData ? (
                <div className="terminal-chart-empty">{t('market.macro.ratesChartEmpty', 'Makro veri bekleniyor')}</div>
            ) : (
                <div className="terminal-macro-rates-chart-wrap">
                    <ResponsiveContainer width="100%" height={220}>
                        <LineChart data={merged} margin={{ top: 8, right: 12, left: 4, bottom: 4 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
                            <XAxis dataKey="period" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                            <YAxis
                                yAxisId="idx"
                                tick={{ fontSize: 9, fill: tokens.textMuted }}
                                width={48}
                                domain={['auto', 'auto']}
                            />
                            <YAxis
                                yAxisId="pct"
                                orientation="right"
                                tick={{ fontSize: 9, fill: tokens.textMuted }}
                                tickFormatter={(v) => `${v}%`}
                                width={40}
                            />
                            <Tooltip
                                contentStyle={{
                                    background: tokens.bgCard,
                                    border: `1px solid ${tokens.border}`,
                                    fontSize: 11,
                                }}
                            />
                            <Legend wrapperStyle={{ fontSize: 10 }} />
                            <Line
                                yAxisId="idx"
                                type="monotone"
                                dataKey="cpiIndex"
                                name={t('market.macro.cpiIndex', 'TÜFE endeksi')}
                                stroke="#f59e0b"
                                dot={false}
                                strokeWidth={2}
                                connectNulls
                            />
                            <Line
                                yAxisId="pct"
                                type="monotone"
                                dataKey="deposit1m"
                                name={t('market.macro.depositTry1mSeries', 'TL mevduat 1A %')}
                                stroke="#38bdf8"
                                dot={false}
                                strokeWidth={2}
                                connectNulls
                            />
                        </LineChart>
                    </ResponsiveContainer>
                </div>
            )}
        </section>
    );
}
