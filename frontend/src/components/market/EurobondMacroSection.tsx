import { useMemo, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
    Area,
    AreaChart,
    Bar,
    BarChart,
    CartesianGrid,
    Cell,
    Legend,
    Line,
    LineChart,
    Pie,
    PieChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { useLanguage } from '../../i18n/LanguageContext';
import {
    EUROBOND_MACRO_SERIES,
    eurobondMacroDefaultFromYmd,
    eurobondMacroDefaultToYmd,
    eurobondMacroQueryKeys,
    fetchEurobondMacroBatchHistory,
    fetchEurobondMacroOverview,
} from '../../services/eurobondMacroApi';
import type { EurobondMacroBatchHistory } from '../../types/eurobondMacro';

type Theme = { bgCard: string; border: string; text: string; textMuted: string };

type Props = {
    tokens: Theme;
};

const CHART_COLORS = ['#3b82f6', '#f59e0b', '#10b981', '#8b5cf6', '#ef4444', '#06b6d4'];

function num(v: unknown): number | null {
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
}

function fmtMillion(v: number | null | undefined, locale: string): string {
    if (v == null || !Number.isFinite(v)) return '—';
    return `${v.toLocaleString(locale, { maximumFractionDigits: 1 })}`;
}

function fmtPct(v: number | null | undefined, locale: string): string {
    if (v == null || !Number.isFinite(v)) return '—';
    return `${v.toLocaleString(locale, { maximumFractionDigits: 1 })}%`;
}

function mergeLineChart(
    batch: EurobondMacroBatchHistory | undefined,
    codes: [string, string],
    keys: [string, string],
): { date: string; [k: string]: string | number }[] {
    if (!batch?.series) return [];
    const a = batch.series[codes[0]]?.points ?? [];
    const b = batch.series[codes[1]]?.points ?? [];
    const map = new Map<string, { date: string; [k: string]: string | number }>();
    for (const p of a) {
        map.set(p.date, { date: p.date, [keys[0]]: p.value });
    }
    for (const p of b) {
        const row = map.get(p.date) ?? { date: p.date };
        row[keys[1]] = p.value;
        map.set(p.date, row);
    }
    return [...map.values()].sort((x, y) => String(x.date).localeCompare(String(y.date)));
}

function mergeStacked(
    batch: EurobondMacroBatchHistory | undefined,
    shortCode: string,
    longCode: string,
): { date: string; short: number; long: number }[] {
    if (!batch?.series) return [];
    const s = batch.series[shortCode]?.points ?? [];
    const l = batch.series[longCode]?.points ?? [];
    const map = new Map<string, { date: string; short: number; long: number }>();
    for (const p of s) {
        map.set(p.date, { date: p.date, short: p.value, long: 0 });
    }
    for (const p of l) {
        const row = map.get(p.date) ?? { date: p.date, short: 0, long: 0 };
        row.long = p.value;
        map.set(p.date, row);
    }
    return [...map.values()].sort((a, b) => a.date.localeCompare(b.date));
}

function latestCurrencySlice(batch: EurobondMacroBatchHistory | undefined) {
    const pick = (code: string) => {
        const pts = batch?.series?.[code]?.points ?? [];
        if (!pts.length) return null;
        return pts[pts.length - 1]?.value ?? null;
    };
    return [
        { name: 'USD', value: pick(EUROBOND_MACRO_SERIES.usd) ?? 0 },
        { name: 'EUR', value: pick(EUROBOND_MACRO_SERIES.eur) ?? 0 },
        { name: 'JPY', value: pick(EUROBOND_MACRO_SERIES.jpy) ?? 0 },
    ].filter((x) => x.value > 0);
}

export function EurobondMacroSection({ tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const from = eurobondMacroDefaultFromYmd(5);
    const to = eurobondMacroDefaultToYmd();

    const overviewQ = useQuery({
        queryKey: eurobondMacroQueryKeys.overview(),
        queryFn: ({ signal }) => fetchEurobondMacroOverview(signal),
        staleTime: 60_000,
    });

    const chartSeriesList = [
        EUROBOND_MACRO_SERIES.marketValue,
        EUROBOND_MACRO_SERIES.bookValue,
        EUROBOND_MACRO_SERIES.remainingShort,
        EUROBOND_MACRO_SERIES.remainingLong,
        EUROBOND_MACRO_SERIES.usd,
        EUROBOND_MACRO_SERIES.eur,
        EUROBOND_MACRO_SERIES.jpy,
        EUROBOND_MACRO_SERIES.originalShort,
        EUROBOND_MACRO_SERIES.originalLong,
    ];

    const historyQ = useQuery({
        queryKey: eurobondMacroQueryKeys.batchHistory(chartSeriesList.join(','), from, to),
        queryFn: ({ signal }) =>
            fetchEurobondMacroBatchHistory({ series: chartSeriesList, from, to }, signal),
        staleTime: 120_000,
    });

    const ov = overviewQ.data;
    const batch = historyQ.data;
    const unit = ov?.unitLabel ?? batch?.unitLabel ?? t('macro.eurobond.unit', 'milyon ABD doları');
    const meta = `${ov?.sourceLabel ?? batch?.sourceLabel ?? 'TCMB EVDS'} · ${ov?.frequencyLabel ?? batch?.frequencyLabel ?? t('macro.eurobond.weekly', 'Haftalık')} · ${unit}`;

    const marketLine = useMemo(
        () =>
            mergeLineChart(batch, [EUROBOND_MACRO_SERIES.marketValue, EUROBOND_MACRO_SERIES.bookValue], [
                'market',
                'book',
            ]),
        [batch],
    );
    const remainingStack = useMemo(
        () => mergeStacked(batch, EUROBOND_MACRO_SERIES.remainingShort, EUROBOND_MACRO_SERIES.remainingLong),
        [batch],
    );
    const originalStack = useMemo(
        () => mergeStacked(batch, EUROBOND_MACRO_SERIES.originalShort, EUROBOND_MACRO_SERIES.originalLong),
        [batch],
    );
    const currencyPie = useMemo(() => latestCurrencySlice(batch), [batch]);

    const loading = overviewQ.isLoading || historyQ.isLoading;
    const noData = !loading && !ov?.available && !batch?.available;

    const kpis: { label: string; value: string; isPct?: boolean }[] = [
        {
            label: t('macro.eurobond.kpiMarketValue', 'Toplam Piyasa Değeri'),
            value: fmtMillion(num(ov?.marketValue), locale),
        },
        {
            label: t('macro.eurobond.kpiBookValue', 'Yazılı Değer'),
            value: fmtMillion(num(ov?.bookValue), locale),
        },
        {
            label: t('macro.eurobond.kpiUsdShare', 'USD İhraç Payı'),
            value: fmtPct(num(ov?.distribution?.usdSharePct), locale),
            isPct: true,
        },
        {
            label: t('macro.eurobond.kpiRemainingLongShare', 'Uzun Kalan Vade Payı'),
            value: fmtPct(num(ov?.distribution?.remainingLongSharePct), locale),
            isPct: true,
        },
    ];

    return (
        <div
            className="terminal-card eurobond-macro-section"
            style={{
                marginTop: 12,
                marginBottom: 12,
                border: `1px solid ${tokens.border}`,
                background: tokens.bgCard,
            }}
        >
            <div style={{ padding: '12px 14px 10px' }}>
                <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: tokens.text }}>
                    {t('macro.eurobond.title', 'Genel Yönetim Eurobondları')}
                </h3>
                <p style={{ margin: '8px 0 0', fontSize: 12, color: tokens.textMuted, lineHeight: 1.45 }}>
                    {t(
                        'macro.eurobond.lead',
                        'Bu veriler tekil Eurobond fiyatı değildir. TCMB EVDS tarafından yayımlanan haftalık Genel Yönetim Eurobondları yazılı değer, piyasa değeri, vade ve para birimi dağılımı istatistikleridir.',
                    )}
                </p>
                <p style={{ margin: '6px 0 0', fontSize: 11, color: tokens.textMuted }}>{meta}</p>
                {ov?.asOfDate ? (
                    <p style={{ margin: '4px 0 0', fontSize: 10, color: tokens.textMuted }}>
                        {t('macro.eurobond.asOf', 'Son gözlem')}: {ov.asOfDate}
                    </p>
                ) : null}
            </div>

            <div style={{ borderTop: `1px solid ${tokens.border}`, padding: '12px 14px' }}>
                {noData ? (
                    <p style={{ margin: 0, fontSize: 13, color: tokens.textMuted }}>
                        {t('macro.eurobond.empty', 'Eurobond EVDS verisi bulunamadı.')}
                    </p>
                ) : (
                    <>
                        <div className="eurobond-gov-panel__kpis">
                            {kpis.map((k) => (
                                <div
                                    key={k.label}
                                    className="terminal-mini-card eurobond-gov-panel__kpi"
                                    style={{ borderColor: tokens.border, background: tokens.bgCard }}
                                >
                                    <div className="eurobond-gov-panel__kpi-label" style={{ color: tokens.textMuted }}>
                                        {k.label}
                                    </div>
                                    <div className="eurobond-gov-panel__kpi-value">{loading ? '…' : k.value}</div>
                                    <div className="eurobond-gov-panel__kpi-unit" style={{ color: tokens.textMuted }}>
                                        {k.isPct ? '' : unit}
                                    </div>
                                </div>
                            ))}
                        </div>

                        <ChartBlock
                            title={t('macro.eurobond.chartMarketVsBook', 'Piyasa Değeri vs Yazılı Değer')}
                            unit={unit}
                            empty={marketLine.length < 2}
                            tokens={tokens}
                        >
                            <ResponsiveContainer width="100%" height={260}>
                                <LineChart data={marketLine}>
                                    <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                                    <XAxis dataKey="date" tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                    <YAxis tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                    <Tooltip
                                        formatter={(v) => [
                                            fmtMillion(typeof v === 'number' ? v : Number(v), locale),
                                            unit,
                                        ]}
                                    />
                                    <Legend />
                                    <Line
                                        type="monotone"
                                        dataKey="market"
                                        name={t('macro.eurobond.legendMarket', 'Piyasa değeri')}
                                        stroke={CHART_COLORS[0]}
                                        dot={false}
                                        strokeWidth={2}
                                    />
                                    <Line
                                        type="monotone"
                                        dataKey="book"
                                        name={t('macro.eurobond.legendBook', 'Yazılı değer')}
                                        stroke={CHART_COLORS[1]}
                                        dot={false}
                                        strokeWidth={2}
                                    />
                                </LineChart>
                            </ResponsiveContainer>
                        </ChartBlock>

                        <ChartBlock
                            title={t('macro.eurobond.chartRemainingMaturity', 'Kalan Vade Dağılımı')}
                            unit={unit}
                            empty={remainingStack.length < 2}
                            tokens={tokens}
                        >
                            <ResponsiveContainer width="100%" height={260}>
                                <AreaChart data={remainingStack}>
                                    <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                                    <XAxis dataKey="date" tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                    <YAxis tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                    <Tooltip
                                        formatter={(v) => [
                                            fmtMillion(typeof v === 'number' ? v : Number(v), locale),
                                            unit,
                                        ]}
                                    />
                                    <Legend />
                                    <Area
                                        type="monotone"
                                        dataKey="short"
                                        stackId="1"
                                        name={t('macro.eurobond.legendRemainingShort', 'Kalan vade — kısa')}
                                        fill={CHART_COLORS[2]}
                                        stroke={CHART_COLORS[2]}
                                    />
                                    <Area
                                        type="monotone"
                                        dataKey="long"
                                        stackId="1"
                                        name={t('macro.eurobond.legendRemainingLong', 'Kalan vade — uzun')}
                                        fill={CHART_COLORS[3]}
                                        stroke={CHART_COLORS[3]}
                                    />
                                </AreaChart>
                            </ResponsiveContainer>
                        </ChartBlock>

                        <ChartBlock
                            title={t('macro.eurobond.chartCurrency', 'Para Birimi Dağılımı (son hafta)')}
                            unit={unit}
                            empty={currencyPie.length === 0}
                            tokens={tokens}
                        >
                            <ResponsiveContainer width="100%" height={260}>
                                <PieChart>
                                    <Pie data={currencyPie} dataKey="value" nameKey="name" innerRadius={50} outerRadius={90}>
                                        {currencyPie.map((_, i) => (
                                            <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                                        ))}
                                    </Pie>
                                    <Tooltip
                                        formatter={(v) => [
                                            fmtMillion(typeof v === 'number' ? v : Number(v), locale),
                                            unit,
                                        ]}
                                    />
                                    <Legend />
                                </PieChart>
                            </ResponsiveContainer>
                        </ChartBlock>

                        <ChartBlock
                            title={t('macro.eurobond.chartOriginalMaturity', 'Orijinal Vade Dağılımı')}
                            unit={unit}
                            empty={originalStack.length < 2}
                            tokens={tokens}
                        >
                            <ResponsiveContainer width="100%" height={240}>
                                <BarChart data={originalStack.slice(-24)}>
                                    <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                                    <XAxis dataKey="date" tick={{ fill: tokens.textMuted, fontSize: 9 }} />
                                    <YAxis tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                    <Tooltip
                                        formatter={(v) => [
                                            fmtMillion(typeof v === 'number' ? v : Number(v), locale),
                                            unit,
                                        ]}
                                    />
                                    <Legend />
                                    <Bar
                                        dataKey="short"
                                        stackId="a"
                                        name={t('macro.eurobond.legendOriginalShort', 'Orijinal vade — kısa')}
                                        fill={CHART_COLORS[4]}
                                    />
                                    <Bar
                                        dataKey="long"
                                        stackId="a"
                                        name={t('macro.eurobond.legendOriginalLong', 'Orijinal vade — uzun')}
                                        fill={CHART_COLORS[5]}
                                    />
                                </BarChart>
                            </ResponsiveContainer>
                        </ChartBlock>
                    </>
                )}
            </div>
        </div>
    );
}

function ChartBlock({
    title,
    unit,
    empty,
    tokens,
    children,
}: {
    title: string;
    unit: string;
    empty: boolean;
    tokens: Theme;
    children: ReactNode;
}) {
    return (
        <div
            className="terminal-mini-card eurobond-gov-panel__chart"
            style={{ borderColor: tokens.border, background: tokens.bgCard, marginTop: 12 }}
        >
            <h4 style={{ margin: '0 0 4px', fontSize: 13 }}>{title}</h4>
            <div style={{ fontSize: 11, color: tokens.textMuted, marginBottom: 8 }}>{unit}</div>
            {empty ? null : children}
        </div>
    );
}
