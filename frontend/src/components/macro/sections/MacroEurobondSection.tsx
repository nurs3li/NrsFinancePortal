import { useMemo } from 'react';
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
import { useLanguage } from '../../../i18n/LanguageContext';
import {
    EUROBOND_MACRO_SERIES,
    eurobondMacroDefaultFromYmd,
    eurobondMacroDefaultToYmd,
    eurobondMacroQueryKeys,
    fetchEurobondMacroBatchHistory,
    fetchEurobondMacroOverview,
} from '../../../services/eurobondMacroApi';
import type { EurobondMacroBatchHistory } from '../../../types/eurobondMacro';
import { useInfoTerm } from '../education/InfoTermProvider';
import { InfoButton } from '../education/InfoButton';
import { ChartCard } from '../primitives/ChartCard';
import { EmptyStateCard } from '../primitives/EmptyStateCard';
import { KpiCard } from '../primitives/KpiCard';
import { MacroSection } from '../primitives/MacroSection';
import type { MacroTheme } from '../MacroTheme';

const CHART_COLORS = ['#3b82f6', '#f59e0b', '#166534', '#8b5cf6', '#991B1B', '#06b6d4'];

function num(v: unknown): number | null {
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
}

function fmtMillion(v: number | null | undefined, locale: string): string {
    if (v == null || !Number.isFinite(v)) return '—';
    return v.toLocaleString(locale, { maximumFractionDigits: 1 });
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
    for (const p of a) map.set(p.date, { date: p.date, [keys[0]]: p.value });
    for (const p of b) {
        const row = map.get(p.date) ?? { date: p.date };
        row[keys[1]] = p.value;
        map.set(p.date, row);
    }
    return [...map.values()].sort((x, y) => String(x.date).localeCompare(String(y.date)));
}

function mergeStacked(batch: EurobondMacroBatchHistory | undefined, shortCode: string, longCode: string) {
    if (!batch?.series) return [];
    const s = batch.series[shortCode]?.points ?? [];
    const l = batch.series[longCode]?.points ?? [];
    const map = new Map<string, { date: string; short: number; long: number }>();
    for (const p of s) map.set(p.date, { date: p.date, short: p.value, long: 0 });
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
        return pts.length ? pts[pts.length - 1]?.value ?? null : null;
    };
    return [
        { name: 'USD', value: pick(EUROBOND_MACRO_SERIES.usd) ?? 0 },
        { name: 'EUR', value: pick(EUROBOND_MACRO_SERIES.eur) ?? 0 },
        { name: 'JPY', value: pick(EUROBOND_MACRO_SERIES.jpy) ?? 0 },
    ].filter((x) => x.value > 0);
}

type Props = { tokens: MacroTheme };

export function MacroEurobondSection({ tokens }: Props) {
    const { lang } = useLanguage();
    const { openTerm } = useInfoTerm();
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
        queryFn: ({ signal }) => fetchEurobondMacroBatchHistory({ series: chartSeriesList, from, to }, signal),
        staleTime: 120_000,
    });

    const ov = overviewQ.data;
    const batch = historyQ.data;
    const unit = ov?.unitLabel ?? batch?.unitLabel ?? 'milyon ABD doları';
    const loading = overviewQ.isLoading || historyQ.isLoading;
    const noData = !loading && !ov?.available && !batch?.available;

    const marketLine = useMemo(
        () => mergeLineChart(batch, [EUROBOND_MACRO_SERIES.marketValue, EUROBOND_MACRO_SERIES.bookValue], ['market', 'book']),
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

    return (
        <MacroSection
            id="macro-eurobond"
            title="Eurobond"
            summary="Döviz cinsinden uzun vadeli borçlanma ve kamu stok kompozisyonu."
            termId="eurobond"
            infoAriaLabel="Eurobond bölümü hakkında bilgi"
            tokens={tokens}
        >
            <article className="macro-euro-def" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                <h3 className="macro-euro-def__title" style={{ color: tokens.text }}>
                    Eurobond nedir?
                    <InfoButton termId="eurobond" ariaLabel="Eurobond nedir" />
                </h3>
                <p className="macro-euro-def__short" style={{ color: tokens.textMuted }}>
                    Döviz cinsinden uzun vadeli tahvil.
                </p>
                <div className="macro-bond-edu">
                    <button type="button" className="macro-term-chip" onClick={() => openTerm('coupon')}>
                        Kupon
                    </button>
                    <button type="button" className="macro-term-chip" onClick={() => openTerm('yield')}>
                        Yield
                    </button>
                    <button type="button" className="macro-term-chip" onClick={() => openTerm('fxRisk')}>
                        Kur riski
                    </button>
                    <button type="button" className="macro-term-chip" onClick={() => openTerm('interestRateRisk')}>
                        Faiz riski
                    </button>
                </div>
            </article>

            <p className="macro-callout macro-callout--info">
                Bu bölüm tekil Eurobond fiyatı değil; EVDS genel yönetim stok/kompozisyon görünümüdür.{' '}
                <button type="button" className="macro-link-btn" onClick={() => openTerm('eurobondComposition')}>
                    Detay
                </button>
            </p>

            <h3 className="macro-subsection-title" style={{ color: tokens.text }}>
                Genel Yönetim Eurobond Kompozisyonu
            </h3>

            {noData ? (
                <EmptyStateCard
                    title="Bu veri şu anda kullanılamıyor."
                    hint="Son kullanılabilir özet göstergeler gösterilmeye devam ediyor."
                    tokens={tokens}
                    onRetry={() => {
                        void overviewQ.refetch();
                        void historyQ.refetch();
                    }}
                />
            ) : (
                <>
                    <div className="macro-grid macro-grid--4">
                        <KpiCard
                            title="Toplam Piyasa Değeri"
                            value={loading ? '…' : fmtMillion(num(ov?.marketValue), locale)}
                            meta={unit}
                            termId="marketValue"
                            infoAriaLabel="Piyasa değeri hakkında bilgi"
                            tokens={tokens}
                        />
                        <KpiCard
                            title="Yazılı Değer"
                            value={loading ? '…' : fmtMillion(num(ov?.bookValue), locale)}
                            meta={unit}
                            termId="bookValue"
                            infoAriaLabel="Yazılı değer hakkında bilgi"
                            tokens={tokens}
                        />
                        <KpiCard
                            title="USD İhraç Payı"
                            value={loading ? '…' : fmtPct(num(ov?.distribution?.usdSharePct), locale)}
                            termId="currencyMix"
                            infoAriaLabel="Para birimi dağılımı hakkında bilgi"
                            tokens={tokens}
                        />
                        <KpiCard
                            title="Uzun Kalan Vade Payı"
                            value={loading ? '…' : fmtPct(num(ov?.distribution?.remainingLongSharePct), locale)}
                            termId="remainingMaturity"
                            infoAriaLabel="Kalan vade hakkında bilgi"
                            tokens={tokens}
                        />
                    </div>

                    {ov?.asOfDate ? (
                        <p className="macro-meta-line" style={{ color: tokens.textMuted }}>
                            Son gözlem: {ov.asOfDate} · {unit}
                        </p>
                    ) : null}

                    <div className="macro-grid macro-grid--2">
                        <ChartCard
                            title="Piyasa değeri vs yazılı değer"
                            termId="marketValue"
                            infoAriaLabel="Piyasa ve yazılı değer grafiği"
                            empty={marketLine.length < 2}
                            tokens={tokens}
                            footer={unit}
                        >
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={marketLine}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
                                    <XAxis dataKey="date" tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                    <YAxis tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                    <Tooltip formatter={(v) => [fmtMillion(Number(v), locale), unit]} />
                                    <Legend />
                                    <Line type="monotone" dataKey="market" name="Piyasa" stroke={CHART_COLORS[0]} dot={false} strokeWidth={2} />
                                    <Line type="monotone" dataKey="book" name="Yazılı" stroke={CHART_COLORS[1]} dot={false} strokeWidth={2} />
                                </LineChart>
                            </ResponsiveContainer>
                        </ChartCard>

                        <ChartCard
                            title="Kalan vade dağılımı"
                            termId="remainingMaturity"
                            infoAriaLabel="Kalan vade grafiği"
                            empty={remainingStack.length < 2}
                            tokens={tokens}
                            footer={unit}
                        >
                            <ResponsiveContainer width="100%" height="100%">
                                <AreaChart data={remainingStack}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
                                    <XAxis dataKey="date" tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                    <YAxis tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                    <Tooltip formatter={(v) => [fmtMillion(Number(v), locale), unit]} />
                                    <Legend />
                                    <Area type="monotone" dataKey="short" stackId="1" name="Kısa" fill={CHART_COLORS[2]} stroke={CHART_COLORS[2]} />
                                    <Area type="monotone" dataKey="long" stackId="1" name="Uzun" fill={CHART_COLORS[3]} stroke={CHART_COLORS[3]} />
                                </AreaChart>
                            </ResponsiveContainer>
                        </ChartCard>

                        <ChartCard
                            title="Para birimi dağılımı"
                            termId="currencyMix"
                            infoAriaLabel="Para birimi dağılımı"
                            empty={currencyPie.length === 0}
                            tokens={tokens}
                        >
                            <ResponsiveContainer width="100%" height="100%">
                                <PieChart>
                                    <Pie data={currencyPie} dataKey="value" nameKey="name" innerRadius={50} outerRadius={90}>
                                        {currencyPie.map((_, i) => (
                                            <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                                        ))}
                                    </Pie>
                                    <Tooltip formatter={(v) => [fmtMillion(Number(v), locale), unit]} />
                                    <Legend />
                                </PieChart>
                            </ResponsiveContainer>
                        </ChartCard>

                        <ChartCard
                            title="Orijinal vade dağılımı"
                            termId="maturity"
                            infoAriaLabel="Orijinal vade"
                            empty={originalStack.length < 2}
                            tokens={tokens}
                            footer={unit}
                        >
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={originalStack.slice(-24)}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
                                    <XAxis dataKey="date" tick={{ fill: tokens.textMuted, fontSize: 9 }} />
                                    <YAxis tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                    <Tooltip formatter={(v) => [fmtMillion(Number(v), locale), unit]} />
                                    <Legend />
                                    <Bar dataKey="short" stackId="a" name="Kısa" fill={CHART_COLORS[4]} />
                                    <Bar dataKey="long" stackId="a" name="Uzun" fill={CHART_COLORS[5]} />
                                </BarChart>
                            </ResponsiveContainer>
                        </ChartCard>
                    </div>
                </>
            )}
        </MacroSection>
    );
}
