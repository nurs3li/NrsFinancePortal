import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
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
import {
    formatBorrowingCostPercent,
    getLoanRatesHistory,
    getLoanRatesLatest,
    macroLoanRatesQueryKeys,
    type LoanRatesHistoryResponse,
    type LoanRatesLatestResponse,
} from '../../services/macroRatesApi';
import {
    fetchDepositRatesLatest,
    fetchInflationCompare,
    fetchInflationLatest,
    fetchInterestInflationMacroPanel,
    fetchPolicyRateTrMacro,
    type DepositRateLatestRow,
    type InflationCompareRow,
    type InflationLatestResponse,
    type InterestInflationMacroPanelResponse,
    type MacroPanelDerivedMetrics,
} from '../../services/marketDataService';
import {
    DEPOSIT_TRY_CHART_LOGICAL_KEYS,
    formatIndex2,
    formatLocaleDate,
    formatPercent2,
    hasFxDepositPanelData,
    lastObservation,
    LOAN_CHART_LOGICAL_KEYS,
    mergeCreditSeriesChart,
    mergeDepositTryChart,
    mergeEurDepositWeeklyChart,
    mergeIndexLevelChart,
    mergeUsdDepositWeeklyChart,
    panelSpreadConsumerMinusDepositTry1m,
    selectMacroSeries,
} from '../../utils/macroPanelSeries';

/** Makro panelinde tahvil fiyat tablosu kaldırıldı; API’den gelen tahvil açıklama satırlarını gizle. */
function isTahvilMacroPanelNote(text: string): boolean {
    const u = text.toLowerCase();
    return (
        u.includes('tahvil') ||
        u.includes('dirty price') ||
        u.includes('listedoranpercentnotytm') ||
        u.includes('ytm değildir') ||
        u.includes('ytm degildir') ||
        u.includes('hasstructuredyielddata') ||
        u.includes('government bond')
    );
}

type Theme = { bgCard: string; border: string; text: string; textMuted: string };

type Props = {
    tokens: Theme;
};

const DISPLAY_DEPOSIT_TERMS = ['1M', '3M', '6M', '1Y'] as const;
const DISPLAY_DEPOSIT_CCYS = ['TRY', 'USD', 'EUR'] as const;

function MacroDepositRatesEvdsBlock({
    tokens,
    t,
    locale,
    panel,
    panelLoading,
    usePanelDepositsTry,
}: {
    tokens: Theme;
    t: (key: string, defaultValue: string) => string;
    locale: string;
    panel: InterestInflationMacroPanelResponse | null | undefined;
    panelLoading: boolean;
    usePanelDepositsTry: boolean;
}) {
    const [rows, setRows] = useState<DepositRateLatestRow[] | null | undefined>(undefined);

    useEffect(() => {
        const ac = new AbortController();
        (async () => {
            setRows(undefined);
            const data = await fetchDepositRatesLatest(ac.signal);
            if (!ac.signal.aborted) {
                setRows(data === null ? null : data);
            }
        })();
        return () => ac.abort();
    }, []);

    const byKey = useMemo(() => {
        const m = new Map<string, DepositRateLatestRow>();
        if (!rows) return m;
        for (const r of rows) {
            const term = String(r.term ?? '').toUpperCase();
            if (!DISPLAY_DEPOSIT_TERMS.includes(term as (typeof DISPLAY_DEPOSIT_TERMS)[number])) continue;
            m.set(`${String(r.currency ?? '').toUpperCase()}_${term}`, r);
        }
        return m;
    }, [rows]);

    const fmtRate = (ccy: string, term: string) => {
        const r = byKey.get(`${ccy}_${term}`);
        if (r == null || r.ratePercent == null || !Number.isFinite(Number(r.ratePercent))) return '—';
        return `${Number(r.ratePercent).toLocaleString(locale, { maximumFractionDigits: 2 })}%`;
    };

    const depositTryMerged = useMemo(
        () => mergeDepositTryChart(panel?.series, [...DEPOSIT_TRY_CHART_LOGICAL_KEYS]),
        [panel?.series],
    );

    const hasDepositTryChart =
        usePanelDepositsTry &&
        depositTryMerged.some(
            (row) =>
                Number.isFinite(Number(row.TRY_1M)) ||
                Number.isFinite(Number(row.TRY_3M)) ||
                Number.isFinite(Number(row.TRY_6M)) ||
                Number.isFinite(Number(row.TRY_1Y)) ||
                Number.isFinite(Number(row.TRY_GT1Y)),
        );

    const depLatestLabel = useMemo(() => {
        const keys = [...DEPOSIT_TRY_CHART_LOGICAL_KEYS] as string[];
        let max = '';
        for (const k of keys) {
            const d = lastObservation(selectMacroSeries(panel?.series, k))?.date;
            if (d && d > max) max = d;
        }
        return max;
    }, [panel?.series]);

    return (
        <div style={{ marginTop: 12 }}>
            {panelLoading ? (
                <div
                    style={{
                        height: 8,
                        borderRadius: 4,
                        background: 'var(--app-surface-soft-bg)',
                        marginBottom: 8,
                    }}
                />
            ) : null}
            {hasDepositTryChart ? (
                <>
                    <div style={{ fontSize: 12, fontWeight: 650, color: tokens.text, marginBottom: 4 }}>
                        {t('market.macro.panel.depositTryChartTitle', 'TL Mevduat Faizleri - Haftalık Akım Veri')}
                    </div>
                    <p style={{ fontSize: 10, color: tokens.textMuted, margin: '0 0 8px', lineHeight: 1.45 }}>
                        {t(
                            'market.macro.panel.depositTryChartBody',
                            'Bu oranlar bankalarda yeni açılan TL mevduatların ortalama faizlerini gösterir.',
                        )}{' '}
                        <span style={{ color: tokens.textMuted }}>
                            ({t('market.macro.panel.freqWeeklyFlow', 'Haftalık akım')})
                        </span>
                    </p>
                    <div style={{ width: '100%', height: 200 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={depositTryMerged} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                                <XAxis dataKey="date" tick={{ fontSize: 8, fill: tokens.textMuted }} />
                                <YAxis
                                    tick={{ fontSize: 9, fill: tokens.textMuted }}
                                    tickFormatter={(v) => `${v}%`}
                                    width={40}
                                />
                                <Tooltip
                                    formatter={(v) => {
                                        const n = Number(v);
                                        return [Number.isFinite(n) ? `${n.toFixed(2)}%` : '—', ''];
                                    }}
                                />
                                <Legend wrapperStyle={{ fontSize: 9 }} />
                                <Line
                                    type="monotone"
                                    dataKey="TRY_1M"
                                    name="TL 1M"
                                    stroke="#0ea5e9"
                                    dot={false}
                                    strokeWidth={2}
                                    connectNulls
                                />
                                <Line
                                    type="monotone"
                                    dataKey="TRY_3M"
                                    name="TL 3M"
                                    stroke="#22d3ee"
                                    dot={false}
                                    strokeWidth={2}
                                    connectNulls
                                />
                                <Line
                                    type="monotone"
                                    dataKey="TRY_6M"
                                    name="TL 6M"
                                    stroke="#6366f1"
                                    dot={false}
                                    strokeWidth={2}
                                    connectNulls
                                />
                                <Line
                                    type="monotone"
                                    dataKey="TRY_1Y"
                                    name="TL 1Y"
                                    stroke="#a855f7"
                                    dot={false}
                                    strokeWidth={2}
                                    connectNulls
                                />
                                <Line
                                    type="monotone"
                                    dataKey="TRY_GT1Y"
                                    name="TL >1Y"
                                    stroke="#f472b6"
                                    dot={false}
                                    strokeWidth={2}
                                    connectNulls
                                />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                    <div style={{ fontSize: 10, color: tokens.textMuted, marginBottom: 12 }}>
                        {t('market.macro.loanRates.asOf', 'Son gözlem')}: {formatLocaleDate(depLatestLabel, locale)}
                    </div>
                </>
            ) : null}
            <div style={{ fontSize: 12, fontWeight: 650, color: tokens.text, marginBottom: 6 }}>
                {t('market.bondMacroDepositEvdsTitle', 'Haftalık mevduat faizi (EVDS akım %)')}
            </div>
            {rows === undefined ? (
                <div style={{ height: 44, borderRadius: 4, background: 'var(--app-surface-soft-bg)' }} />
            ) : rows === null ? (
                <p style={{ fontSize: 11, color: tokens.textMuted, margin: 0 }}>
                    {t('market.bondMacroDepositEvdsUnavailable', 'Mevduat faiz uçları aktif değil veya veri yok.')}
                </p>
            ) : (
                <>
                    <div style={{ overflowX: 'auto' }}>
                        <table className="terminal-data-table" style={{ fontSize: 11, minWidth: 400 }}>
                            <thead>
                                <tr>
                                    <th />
                                    {DISPLAY_DEPOSIT_TERMS.map((term) => (
                                        <th key={term}>{term}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {DISPLAY_DEPOSIT_CCYS.map((ccy) => (
                                    <tr key={ccy}>
                                        <td>
                                            <strong>{ccy}</strong>
                                        </td>
                                        {DISPLAY_DEPOSIT_TERMS.map((term) => (
                                            <td key={term}>{fmtRate(ccy, term)}</td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <p style={{ fontSize: 10, color: tokens.textMuted, margin: '8px 0 0', lineHeight: 1.4 }}>
                        {t(
                            'market.bondMacroDepositEvdsDisclaimer',
                            'Bu oranlar EVDS Mevduat Faiz Oranları (Akım, %) serileridir; piyasa ortalamasıdır, bireysel koşullar değişebilir.',
                        )}
                        {rows != null && rows.length > 0 && rows[0].frequency ? (
                            <span>
                                {' '}
                                ({rows[0].frequency}
                                {rows[0].flowType ? ` · ${rows[0].flowType}` : ''})
                            </span>
                        ) : null}
                    </p>
                </>
            )}
        </div>
    );
}

function MacroFxDepositRatesPanel({
    tokens,
    t,
    locale,
    panel,
    panelLoading,
    panelError,
}: {
    tokens: Theme;
    t: (key: string, defaultValue: string) => string;
    locale: string;
    panel: InterestInflationMacroPanelResponse | null | undefined;
    panelLoading: boolean;
    panelError: boolean;
}) {
    const series = panel?.series;
    const hasData = useMemo(() => hasFxDepositPanelData(series), [series]);
    const usdMerged = useMemo(() => mergeUsdDepositWeeklyChart(series), [series]);
    const eurMerged = useMemo(() => mergeEurDepositWeeklyChart(series), [series]);

    const usdKeys = ['USD_1M', 'USD_3M', 'USD_6M', 'USD_1Y'] as const;
    const eurKeys = ['EUR_1M', 'EUR_3M', 'EUR_6M', 'EUR_1Y'] as const;

    const usdChartHas = usdMerged.some((row) => usdKeys.some((k) => Number.isFinite(Number(row[k]))));
    const eurChartHas = eurMerged.some((row) => eurKeys.some((k) => Number.isFinite(Number(row[k]))));

    const fmtTip = (v: unknown) => {
        const n = Number(v);
        return Number.isFinite(n) ? `${n.toLocaleString(locale, { maximumFractionDigits: 2 })}%` : '—';
    };

    const freqLabel = t('market.macro.panel.freqWeeklyFlow', 'Haftalık akım');

    const card = (logicalKey: string, title: string) => {
        const lo = lastObservation(selectMacroSeries(series, logicalKey));
        const pct =
            lo != null && Number.isFinite(lo.value)
                ? `${Number(lo.value).toLocaleString(locale, { maximumFractionDigits: 2 })}%`
                : '—';
        return (
            <div
                key={logicalKey}
                style={{
                    border: `1px solid ${tokens.border}`,
                    borderRadius: 8,
                    padding: 8,
                    background: tokens.bgCard,
                }}
            >
                <div style={{ fontSize: 10, color: tokens.textMuted, lineHeight: 1.25 }}>{title}</div>
                <div style={{ fontSize: 15, fontWeight: 700, color: tokens.text, marginTop: 4 }}>{pct}</div>
                <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4, lineHeight: 1.35 }}>
                    {t('market.macro.loanRates.asOf', 'Son gözlem')}: {formatLocaleDate(lo?.date, locale)}
                </div>
                <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 2 }}>{freqLabel}</div>
            </div>
        );
    };

    return (
        <details
            style={{
                marginTop: 14,
                border: `1px solid ${tokens.border}`,
                borderRadius: 8,
                background: tokens.bgCard,
                padding: '0 10px 10px',
            }}
        >
            <summary
                style={{
                    cursor: 'pointer',
                    fontSize: 12,
                    fontWeight: 650,
                    color: tokens.text,
                    padding: '10px 4px',
                    listStyle: 'none',
                }}
            >
                {t(
                    'market.macro.panel.fxDepositAdvancedSummary',
                    'Gelişmiş — Döviz mevduat faizleri (USD / EUR)',
                )}
            </summary>
            <div style={{ paddingTop: 4 }}>
                <div style={{ fontSize: 13, fontWeight: 650, color: tokens.text }}>
                    {t('market.macro.panel.fxDepositTitle', 'Döviz Mevduat Faizleri')}
                </div>
                <p style={{ fontSize: 10, color: tokens.textMuted, margin: '6px 0 10px', lineHeight: 1.45 }}>
                    {t(
                        'market.macro.panel.fxDepositIntro',
                        'Döviz mevduat faizleri USD/EUR cinsi nominal faiz oranlarını gösterir. TL bazında performans, faiz getirisine ek olarak kur değişimine bağlıdır.',
                    )}
                </p>

                {panelLoading && !panelError ? (
                    <div
                        style={{
                            height: 100,
                            borderRadius: 6,
                            background: 'var(--app-surface-soft-bg)',
                        }}
                    />
                ) : !hasData ? (
                    <p style={{ fontSize: 11, color: tokens.textMuted, margin: 0, lineHeight: 1.45 }}>
                        {t('market.macro.panel.fxDepositEmpty', 'Döviz mevduat verisi bekleniyor')}
                    </p>
                ) : (
                    <>
                        <div
                            style={{
                                display: 'grid',
                                gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))',
                                gap: 8,
                                marginBottom: 12,
                            }}
                        >
                            {card('DEPOSIT_RATE_USD_1M_WEEKLY', 'USD 1M')}
                            {card('DEPOSIT_RATE_USD_3M_WEEKLY', 'USD 3M')}
                            {card('DEPOSIT_RATE_EUR_1M_WEEKLY', 'EUR 1M')}
                            {card('DEPOSIT_RATE_EUR_3M_WEEKLY', 'EUR 3M')}
                        </div>

                        {usdChartHas ? (
                            <>
                                <div style={{ fontSize: 11, fontWeight: 650, color: tokens.text, margin: '10px 0 6px' }}>
                                    {t('market.macro.panel.fxDepositUsdChartTitle', 'USD Mevduat Faizleri - Haftalık Akım')}
                                </div>
                                <div style={{ width: '100%', height: 180 }}>
                                    <ResponsiveContainer width="100%" height="100%">
                                        <LineChart data={usdMerged} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                                            <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                                            <XAxis dataKey="date" tick={{ fontSize: 8, fill: tokens.textMuted }} />
                                            <YAxis
                                                tick={{ fontSize: 9, fill: tokens.textMuted }}
                                                tickFormatter={(v) => `${v}%`}
                                                width={40}
                                            />
                                            <Tooltip formatter={(v) => [fmtTip(v), '']} />
                                            <Legend wrapperStyle={{ fontSize: 9 }} />
                                            <Line type="monotone" dataKey="USD_1M" name="USD 1M" stroke="#f59e0b" dot={false} strokeWidth={2} connectNulls />
                                            <Line type="monotone" dataKey="USD_3M" name="USD 3M" stroke="#eab308" dot={false} strokeWidth={2} connectNulls />
                                            <Line type="monotone" dataKey="USD_6M" name="USD 6M" stroke="#84cc16" dot={false} strokeWidth={2} connectNulls />
                                            <Line type="monotone" dataKey="USD_1Y" name="USD 1Y" stroke="#22c55e" dot={false} strokeWidth={2} connectNulls />
                                        </LineChart>
                                    </ResponsiveContainer>
                                </div>
                            </>
                        ) : null}

                        {eurChartHas ? (
                            <>
                                <div style={{ fontSize: 11, fontWeight: 650, color: tokens.text, margin: '14px 0 6px' }}>
                                    {t('market.macro.panel.fxDepositEurChartTitle', 'EUR Mevduat Faizleri - Haftalık Akım')}
                                </div>
                                <div style={{ width: '100%', height: 180 }}>
                                    <ResponsiveContainer width="100%" height="100%">
                                        <LineChart data={eurMerged} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                                            <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                                            <XAxis dataKey="date" tick={{ fontSize: 8, fill: tokens.textMuted }} />
                                            <YAxis
                                                tick={{ fontSize: 9, fill: tokens.textMuted }}
                                                tickFormatter={(v) => `${v}%`}
                                                width={40}
                                            />
                                            <Tooltip formatter={(v) => [fmtTip(v), '']} />
                                            <Legend wrapperStyle={{ fontSize: 9 }} />
                                            <Line type="monotone" dataKey="EUR_1M" name="EUR 1M" stroke="#38bdf8" dot={false} strokeWidth={2} connectNulls />
                                            <Line type="monotone" dataKey="EUR_3M" name="EUR 3M" stroke="#3b82f6" dot={false} strokeWidth={2} connectNulls />
                                            <Line type="monotone" dataKey="EUR_6M" name="EUR 6M" stroke="#6366f1" dot={false} strokeWidth={2} connectNulls />
                                            <Line type="monotone" dataKey="EUR_1Y" name="EUR 1Y" stroke="#8b5cf6" dot={false} strokeWidth={2} connectNulls />
                                        </LineChart>
                                    </ResponsiveContainer>
                                </div>
                            </>
                        ) : null}
                    </>
                )}
            </div>
        </details>
    );
}

function defaultInflationChartFromYm(): string {
    const d = new Date();
    d.setFullYear(d.getFullYear() - 5);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function defaultInflationChartToYm(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function MacroInflationEvdsBlock({
    tokens,
    t,
    locale,
    panel,
    panelLoading,
    fromPanel,
}: {
    tokens: Theme;
    t: (key: string, defaultValue: string) => string;
    locale: string;
    panel: InterestInflationMacroPanelResponse | null | undefined;
    panelLoading: boolean;
    fromPanel: boolean;
}) {
    const [latest, setLatest] = useState<InflationLatestResponse | null | undefined>(undefined);
    const [compareRows, setCompareRows] = useState<InflationCompareRow[] | null | undefined>(undefined);

    useEffect(() => {
        if (panelLoading) return;
        if (fromPanel) return;
        const ac = new AbortController();
        (async () => {
            setLatest(undefined);
            setCompareRows(undefined);
            const [l, cmp] = await Promise.all([
                fetchInflationLatest(ac.signal),
                fetchInflationCompare(defaultInflationChartFromYm(), defaultInflationChartToYm(), ac.signal),
            ]);
            if (ac.signal.aborted) return;
            setLatest(l);
            setCompareRows(cmp?.rows ?? null);
        })();
        return () => ac.abort();
    }, [panelLoading, fromPanel]);

    const indexChartData = useMemo(() => mergeIndexLevelChart(panel?.series), [panel?.series]);
    const hasIndexChart = indexChartData.some(
        (d) => (d.cpi != null && Number.isFinite(Number(d.cpi))) || (d.ppi != null && Number.isFinite(Number(d.ppi))),
    );

    const chartData = useMemo(() => {
        if (!compareRows || compareRows.length === 0) return [];
        return compareRows.map((r) => ({
            period: String(r.month ?? '').slice(0, 7),
            cpiYoY: r.cpiAnnualChangePercent,
            ppiYoY: r.ppiAnnualChangePercent,
        }));
    }, [compareRows]);

    const hasYoYChart = chartData.some(
        (d) => (d.cpiYoY != null && Number.isFinite(Number(d.cpiYoY))) || (d.ppiYoY != null && Number.isFinite(Number(d.ppiYoY))),
    );

    if (panelLoading) {
        return (
            <div style={{ marginTop: 10 }}>
                <div style={{ fontSize: 12, fontWeight: 650, color: tokens.text, marginBottom: 6 }}>
                    {t('market.bondMacroInflationEvdsTitle', 'TÜFE ve Yİ-ÜFE (EVDS, aylık)')}
                </div>
                <div style={{ height: 56, borderRadius: 4, background: 'var(--app-surface-soft-bg)' }} />
            </div>
        );
    }

    if (fromPanel && panel) {
        const d: MacroPanelDerivedMetrics | undefined = panel.derived;
        const awaiting = t('market.macro.panel.awaitingData', 'Veri bekleniyor');
        const fmtD = (v: number | null | undefined) =>
            v == null || !Number.isFinite(Number(v)) ? awaiting : formatPercent2(v, locale);
        const cpi = selectMacroSeries(panel.series, 'CPI_TR_INDEX');
        const ppi = selectMacroSeries(panel.series, 'PPI_TR_INDEX');
        const cpiD = lastObservation(cpi)?.date;
        const ppiD = lastObservation(ppi)?.date;

        return (
            <div style={{ marginTop: 10 }}>
                <div style={{ fontSize: 12, fontWeight: 650, color: tokens.text, marginBottom: 6 }}>
                    {t('market.macro.panel.inflationIndexBlockTitle', 'TÜFE / Yİ-ÜFE Endeks Seviyesi')}
                </div>
                <div style={{ fontSize: 10, color: tokens.textMuted, marginBottom: 8, lineHeight: 1.45 }}>
                    {t('market.macro.panel.freqMonthly', 'Aylık')} ·{' '}
                    {t(
                        'market.macro.panel.inflationIndexBlockIntro',
                        'Aylık ve yıllık enflasyon oranları endeks seviyelerinden hesaplanır.',
                    )}
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))', gap: 8 }}>
                    <div style={{ border: `1px solid ${tokens.border}`, borderRadius: 8, padding: 8, background: tokens.bgCard }}>
                        <div style={{ fontSize: 10, color: tokens.textMuted }}>{t('macro.inflation.kpi.cpiMoM', 'TÜFE Aylık')}</div>
                        <div style={{ fontSize: 14, fontWeight: 700, color: tokens.text }}>{fmtD(d?.cpiMoM ?? null)}</div>
                        <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4 }}>
                            {formatLocaleDate(cpiD, locale)}
                        </div>
                    </div>
                    <div style={{ border: `1px solid ${tokens.border}`, borderRadius: 8, padding: 8, background: tokens.bgCard }}>
                        <div style={{ fontSize: 10, color: tokens.textMuted }}>{t('macro.inflation.kpi.cpiYoY', 'TÜFE Yıllık')}</div>
                        <div style={{ fontSize: 14, fontWeight: 700, color: tokens.text }}>{fmtD(d?.cpiYoY ?? null)}</div>
                        <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4 }}>
                            {formatLocaleDate(cpiD, locale)}
                        </div>
                    </div>
                    <div style={{ border: `1px solid ${tokens.border}`, borderRadius: 8, padding: 8, background: tokens.bgCard }}>
                        <div style={{ fontSize: 10, color: tokens.textMuted }}>{t('macro.inflation.kpi.ppiMoM', 'Yİ-ÜFE Aylık')}</div>
                        <div style={{ fontSize: 14, fontWeight: 700, color: tokens.text }}>{fmtD(d?.ppiMoM ?? null)}</div>
                        <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4 }}>
                            {formatLocaleDate(ppiD, locale)}
                        </div>
                    </div>
                    <div style={{ border: `1px solid ${tokens.border}`, borderRadius: 8, padding: 8, background: tokens.bgCard }}>
                        <div style={{ fontSize: 10, color: tokens.textMuted }}>{t('macro.inflation.kpi.ppiYoY', 'Yİ-ÜFE Yıllık')}</div>
                        <div style={{ fontSize: 14, fontWeight: 700, color: tokens.text }}>{fmtD(d?.ppiYoY ?? null)}</div>
                        <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4 }}>
                            {formatLocaleDate(ppiD, locale)}
                        </div>
                    </div>
                </div>
                <p style={{ fontSize: 10, color: tokens.textMuted, margin: '8px 0 6px', lineHeight: 1.45 }}>
                    {t(
                        'market.macro.panel.inflationIndexChartCaption',
                        'Aşağıdaki grafik TÜFE ve Yİ-ÜFE endeks düzeylerini gösterir; doğrudan “enflasyon oranı” grafiği değildir.',
                    )}
                </p>
                {hasIndexChart ? (
                    <div style={{ width: '100%', height: 200 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={indexChartData} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                                <XAxis dataKey="period" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                                <YAxis
                                    tick={{ fontSize: 9, fill: tokens.textMuted }}
                                    tickFormatter={(v) => formatIndex2(Number(v), locale)}
                                    width={52}
                                />
                                <Tooltip
                                    formatter={(v) => {
                                        const n = Number(v);
                                        return [Number.isFinite(n) ? formatIndex2(n, locale) : '—', ''];
                                    }}
                                    labelStyle={{ fontSize: 11 }}
                                    contentStyle={{ fontSize: 11 }}
                                />
                                <Legend wrapperStyle={{ fontSize: 10 }} />
                                <Line
                                    type="monotone"
                                    dataKey="cpi"
                                    name={t('market.bondMacro.legend.cpiIndex', 'TÜFE endeks')}
                                    stroke="#38bdf8"
                                    dot={false}
                                    strokeWidth={2}
                                    connectNulls
                                />
                                <Line
                                    type="monotone"
                                    dataKey="ppi"
                                    name={t('market.bondMacro.legend.ppiIndex', 'Yİ-ÜFE endeks')}
                                    stroke="#a78bfa"
                                    dot={false}
                                    strokeWidth={2}
                                    connectNulls
                                />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                ) : (
                    <p style={{ fontSize: 10, color: tokens.textMuted, margin: 0 }}>
                        {t('market.bondMacroInflationChartEmpty', 'YoY grafik için yeterli seri verisi yok.')}
                    </p>
                )}
            </div>
        );
    }

    return (
        <div style={{ marginTop: 10 }}>
            <div style={{ fontSize: 12, fontWeight: 650, color: tokens.text, marginBottom: 6 }}>
                {t('market.bondMacroInflationEvdsTitle', 'TÜFE ve Yİ-ÜFE (EVDS, aylık)')}
            </div>
            {latest === undefined || compareRows === undefined ? (
                <div style={{ height: 56, borderRadius: 4, background: 'var(--app-surface-soft-bg)' }} />
            ) : latest === null && compareRows === null ? (
                <p style={{ fontSize: 11, color: tokens.textMuted, margin: 0 }}>
                    {t('market.bondMacroInflationEvdsUnavailable', 'Enflasyon uçlarına ulaşılamadı veya veri yok.')}
                </p>
            ) : (
                <>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))', gap: 8 }}>
                        <div style={{ border: `1px solid ${tokens.border}`, borderRadius: 8, padding: 8, background: tokens.bgCard }}>
                            <div style={{ fontSize: 10, color: tokens.textMuted }}>{t('macro.inflation.kpi.cpiMoM', 'TÜFE Aylık')}</div>
                            <div style={{ fontSize: 14, fontWeight: 700, color: tokens.text }}>
                                {formatPercent2(latest?.cpi?.monthlyChangePercent ?? null, locale)}
                            </div>
                        </div>
                        <div style={{ border: `1px solid ${tokens.border}`, borderRadius: 8, padding: 8, background: tokens.bgCard }}>
                            <div style={{ fontSize: 10, color: tokens.textMuted }}>{t('macro.inflation.kpi.cpiYoY', 'TÜFE Yıllık')}</div>
                            <div style={{ fontSize: 14, fontWeight: 700, color: tokens.text }}>
                                {formatPercent2(latest?.cpi?.annualChangePercent ?? null, locale)}
                            </div>
                        </div>
                        <div style={{ border: `1px solid ${tokens.border}`, borderRadius: 8, padding: 8, background: tokens.bgCard }}>
                            <div style={{ fontSize: 10, color: tokens.textMuted }}>{t('macro.inflation.kpi.ppiMoM', 'Yİ-ÜFE Aylık')}</div>
                            <div style={{ fontSize: 14, fontWeight: 700, color: tokens.text }}>
                                {formatPercent2(latest?.ppi?.monthlyChangePercent ?? null, locale)}
                            </div>
                        </div>
                        <div style={{ border: `1px solid ${tokens.border}`, borderRadius: 8, padding: 8, background: tokens.bgCard }}>
                            <div style={{ fontSize: 10, color: tokens.textMuted }}>{t('macro.inflation.kpi.ppiYoY', 'Yİ-ÜFE Yıllık')}</div>
                            <div style={{ fontSize: 14, fontWeight: 700, color: tokens.text }}>
                                {formatPercent2(latest?.ppi?.annualChangePercent ?? null, locale)}
                            </div>
                        </div>
                    </div>
                    <p style={{ fontSize: 10, color: tokens.textMuted, margin: '8px 0 6px', lineHeight: 1.45 }}>
                        {t(
                            'market.bondMacroInflationUfeTufeNote',
                            'ÜFE = üretici maliyet enflasyonu (Yİ-ÜFE); TÜFE = tüketici fiyat enflasyonu. Aşağıdaki grafik yıllık değişim yüzdelerini (YoY) gösterir; endeks düzeyi enflasyon oranı değildir.',
                        )}
                    </p>
                    {hasYoYChart ? (
                        <div style={{ width: '100%', height: 200 }}>
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={chartData} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                                    <XAxis dataKey="period" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                                    <YAxis
                                        tick={{ fontSize: 9, fill: tokens.textMuted }}
                                        tickFormatter={(v) => `${v}%`}
                                        width={44}
                                    />
                                    <Tooltip
                                        formatter={(v) => {
                                            const n = Number(v);
                                            return [Number.isFinite(n) ? `${n.toFixed(2)}%` : '—', ''];
                                        }}
                                        labelStyle={{ fontSize: 11 }}
                                        contentStyle={{ fontSize: 11 }}
                                    />
                                    <Legend wrapperStyle={{ fontSize: 10 }} />
                                    <Line type="monotone" dataKey="cpiYoY" name={t('macro.inflation.legend.cpiYoY', 'TÜFE Yıllık')} stroke="#38bdf8" dot={false} strokeWidth={2} connectNulls />
                                    <Line type="monotone" dataKey="ppiYoY" name={t('macro.inflation.legend.ppiYoY', 'Yİ-ÜFE Yıllık')} stroke="#a78bfa" dot={false} strokeWidth={2} connectNulls />
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                    ) : (
                        <p style={{ fontSize: 10, color: tokens.textMuted, margin: 0 }}>
                            {t('market.bondMacroInflationChartEmpty', 'YoY grafik için yeterli seri verisi yok.')}
                        </p>
                    )}
                    {latest?.methodologyNote ? (
                        <p style={{ fontSize: 10, color: tokens.textMuted, margin: '8px 0 0', lineHeight: 1.4 }}>
                            {latest.methodologyNote}
                        </p>
                    ) : null}
                </>
            )}
        </div>
    );
}

const LOAN_RATES_TYPES_CSV = 'CONSUMER_TRY,VEHICLE_TRY,HOUSING_TRY,COMMERCIAL_TRY';

function loanHistoryRange(): { from: string; to: string } {
    const to = new Date();
    const from = new Date();
    from.setFullYear(from.getFullYear() - 2);
    return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

function loanTypeToI18nKey(type: string): string {
    const m: Record<string, string> = {
        CONSUMER_TRY: 'market.macro.loanRates.consumer',
        VEHICLE_TRY: 'market.macro.loanRates.vehicle',
        HOUSING_TRY: 'market.macro.loanRates.housing',
        COMMERCIAL_TRY: 'market.macro.loanRates.commercial',
    };
    return m[type] ?? 'market.macro.loanRates.title';
}

function mergeLoanHistoryForMultiChart(h: LoanRatesHistoryResponse | null | undefined): Record<string, string | number>[] {
    if (!h?.series?.length) return [];
    const byDate = new Map<string, Record<string, string | number>>();
    for (const s of h.series) {
        const t = String(s.type ?? '');
        for (const p of s.points ?? []) {
            const d = String(p.date ?? '');
            if (!d) continue;
            const row = byDate.get(d) ?? { date: d };
            row[t] = Number(p.value);
            byDate.set(d, row);
        }
    }
    return Array.from(byDate.values()).sort((a, b) => String(a.date).localeCompare(String(b.date)));
}

function MacroLoanRatesEvdsBlock({
    tokens,
    t,
    locale,
    panel,
    panelLoading,
    usePanelLoans,
}: {
    tokens: Theme;
    t: (key: string, defaultValue: string) => string;
    locale: string;
    panel: InterestInflationMacroPanelResponse | null | undefined;
    panelLoading: boolean;
    usePanelLoans: boolean;
}) {
    const { from, to } = useMemo(() => loanHistoryRange(), []);

    const latestQ = useQuery({
        queryKey: macroLoanRatesQueryKeys.latest(),
        queryFn: ({ signal }) => getLoanRatesLatest(signal),
    });
    const historyQ = useQuery({
        queryKey: macroLoanRatesQueryKeys.history(LOAN_RATES_TYPES_CSV, from, to),
        queryFn: ({ signal }) => getLoanRatesHistory({ types: LOAN_RATES_TYPES_CSV, from, to }, signal),
    });
    const policyQ = useQuery({
        queryKey: macroLoanRatesQueryKeys.policyTr(),
        queryFn: ({ signal }) => fetchPolicyRateTrMacro(signal),
    });
    const depositQ = useQuery({
        queryKey: macroLoanRatesQueryKeys.depositLatest(),
        queryFn: ({ signal }) => fetchDepositRatesLatest(signal),
    });

    const latest: LoanRatesLatestResponse | null | undefined = latestQ.data;
    const merged = useMemo(() => mergeLoanHistoryForMultiChart(historyQ.data ?? null), [historyQ.data]);

    const mergedPanel = useMemo(
        () => mergeCreditSeriesChart(panel?.series, [...LOAN_CHART_LOGICAL_KEYS]),
        [panel?.series],
    );

    const showCompareChartPanel = mergedPanel.some(
        (row) =>
            Number.isFinite(Number(row.CONSUMER_TRY)) ||
            Number.isFinite(Number(row.VEHICLE_TRY)) ||
            Number.isFinite(Number(row.HOUSING_TRY)) ||
            Number.isFinite(Number(row.COMMERCIAL_TRY)),
    );

    const panelLoanAsOf = useMemo(() => {
        let max = '';
        for (const k of LOAN_CHART_LOGICAL_KEYS) {
            const d = lastObservation(selectMacroSeries(panel?.series, k))?.date;
            if (d && d > max) max = d;
        }
        return max;
    }, [panel?.series]);

    const panelLoanLatestItems = useMemo(() => {
        if (!usePanelLoans || !panel?.series) return [];
        const types = [
            { ui: 'LOAN_RATE_CONSUMER_WEEKLY', type: 'CONSUMER_TRY' },
            { ui: 'LOAN_RATE_VEHICLE_WEEKLY', type: 'VEHICLE_TRY' },
            { ui: 'LOAN_RATE_HOUSING_WEEKLY', type: 'HOUSING_TRY' },
            { ui: 'LOAN_RATE_COMMERCIAL_WEEKLY', type: 'COMMERCIAL_TRY' },
        ];
        return types
            .map(({ ui, type }) => {
                const s = selectMacroSeries(panel.series, ui);
                const lo = lastObservation(s);
                if (!lo) return null;
                return { type, value: lo.value, seriesCode: s?.code ?? ui, date: lo.date };
            })
            .filter((x): x is { type: string; value: number; seriesCode: string; date: string } => x != null);
    }, [usePanelLoans, panel?.series]);

    const consumerHistory = useMemo(() => {
        const s = historyQ.data?.series?.find((x) => x.type === 'CONSUMER_TRY');
        return s?.points ?? [];
    }, [historyQ.data]);

    const consumerPanelHistory = useMemo(() => {
        const s = selectMacroSeries(panel?.series, 'LOAN_RATE_CONSUMER_WEEKLY');
        return (s?.observations ?? []).map((o) => ({
            date: String(o.date).slice(0, 10),
            value: Number(o.value),
        }));
    }, [panel?.series]);

    const tryDeposit1m = useMemo(() => {
        const rows = depositQ.data;
        if (!rows || !Array.isArray(rows)) return null;
        return rows.find((r) => String(r.currency ?? '').toUpperCase() === 'TRY' && String(r.term ?? '').toUpperCase() === '1M') ?? null;
    }, [depositQ.data]);

    const spreadChartData = useMemo(() => {
        const dep = tryDeposit1m?.ratePercent;
        if (dep == null || !Number.isFinite(Number(dep))) return [];
        return consumerHistory.map((p) => ({
            date: String(p.date).slice(0, 10),
            spread: Number(p.value) - Number(dep),
        }));
    }, [consumerHistory, tryDeposit1m]);

    const spreadChartDataPanel = useMemo(() => panelSpreadConsumerMinusDepositTry1m(panel?.series), [panel?.series]);

    const policyVsConsumerData = useMemo(() => {
        const pv = policyQ.data?.valuePercent;
        if (pv == null || !Number.isFinite(Number(pv))) return [];
        return consumerHistory.map((p) => ({
            date: String(p.date).slice(0, 10),
            consumer: Number(p.value),
            policy: Number(pv),
        }));
    }, [consumerHistory, policyQ.data]);

    const policyVsConsumerDataPanel = useMemo(() => {
        const pv = lastObservation(selectMacroSeries(panel?.series, 'POLICY_RATE_TR'))?.value;
        if (pv == null || !Number.isFinite(Number(pv))) return [];
        return consumerPanelHistory.map((p) => ({
            date: p.date,
            consumer: p.value,
            policy: Number(pv),
        }));
    }, [consumerPanelHistory, panel?.series]);

    const compareChartData = usePanelLoans ? mergedPanel : merged;
    const showCompareChart = compareChartData.some(
        (row) =>
            Number.isFinite(Number(row.CONSUMER_TRY)) ||
            Number.isFinite(Number(row.VEHICLE_TRY)) ||
            Number.isFinite(Number(row.HOUSING_TRY)) ||
            Number.isFinite(Number(row.COMMERCIAL_TRY)),
    );

    const spreadEffective = usePanelLoans ? spreadChartDataPanel : spreadChartData;
    const policyVsEffective = usePanelLoans ? policyVsConsumerDataPanel : policyVsConsumerData;
    const consumerHistLen = usePanelLoans ? consumerPanelHistory.length : consumerHistory.length;

    const loading = panelLoading || (!usePanelLoans && (latestQ.isPending || historyQ.isPending));
    const hasLatestItems = (latest?.items?.length ?? 0) > 0;
    const canShowLoanPanelPrimary =
        usePanelLoans && (panelLoanLatestItems.length > 0 || showCompareChartPanel);
    const canShowLegacyPrimary = !usePanelLoans && latest != null && hasLatestItems;

    return (
        <div style={{ marginTop: 12 }}>
            <div style={{ fontSize: 12, fontWeight: 650, color: tokens.text, marginBottom: 4 }}>
                {t('market.macro.loanRates.title', 'Kredi Faizleri')}
            </div>
            <div style={{ fontSize: 10, color: tokens.textMuted, marginBottom: 8, lineHeight: 1.4 }}>
                {t('market.macro.loanRates.subtitle', 'Yeni açılan kredilere uygulanan haftalık ağırlıklı ortalama faiz oranlarıdır.')}{' '}
                <span style={{ color: tokens.textMuted }}>
                    ({t('market.macro.panel.freqWeeklyFlow', 'Haftalık akım')})
                </span>
            </div>
            {loading ? (
                <div style={{ height: 72, borderRadius: 4, background: 'var(--app-surface-soft-bg)' }} />
            ) : canShowLoanPanelPrimary ? (
                <>
                    <div style={{ fontSize: 10, color: tokens.textMuted, marginBottom: 8 }}>
                        {t('market.macro.loanRates.asOf', 'Son gözlem')}: {formatLocaleDate(panelLoanAsOf, locale)} ·{' '}
                        {t('market.macro.loanRates.sourceHint', 'TCMB EVDS, haftalık akım veri')}
                    </div>
                    {panelLoanLatestItems.length > 0 ? (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(132px, 1fr))', gap: 8 }}>
                            {panelLoanLatestItems.map((it) => (
                                <div
                                    key={it.type}
                                    style={{
                                        border: `1px solid ${tokens.border}`,
                                        borderRadius: 8,
                                        padding: 8,
                                        background: tokens.bgCard,
                                    }}
                                >
                                    <div style={{ fontSize: 10, color: tokens.textMuted, lineHeight: 1.25 }}>
                                        {t(loanTypeToI18nKey(it.type), it.type)}
                                    </div>
                                    <div style={{ fontSize: 15, fontWeight: 700, color: tokens.text, marginTop: 4 }}>
                                        {formatBorrowingCostPercent(it.value)}
                                    </div>
                                    <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4, lineHeight: 1.3 }}>
                                        {it.seriesCode}
                                    </div>
                                    <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4 }}>
                                        {formatLocaleDate(it.date, locale)}
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : null}
                    <p style={{ fontSize: 10, color: tokens.textMuted, margin: '10px 0 6px', lineHeight: 1.45 }}>
                        {t(
                            'market.macro.loanRates.notReturn',
                            'Bu oranlar yatırım getirisi değil, borçlanma maliyetidir.',
                        )}
                    </p>
                    <p style={{ fontSize: 10, color: tokens.text, margin: '0 0 10px', lineHeight: 1.45 }}>
                        {t(
                            'market.macro.loanRates.uxExplainer',
                            'Kredi faizleri yatırım getirisi değildir; yeni kredi kullanmanın yaklaşık piyasa maliyetini gösterir. Mevduat faizi tasarruf sahibinin getirisini, kredi faizi ise borçlanan kişinin maliyetini ifade eder.',
                        )}
                    </p>
                </>
            ) : canShowLegacyPrimary ? (
                <>
                    <div style={{ fontSize: 10, color: tokens.textMuted, marginBottom: 8 }}>
                        {t('market.macro.loanRates.asOf', 'Son gözlem')}: {latest?.asOf ?? '—'} ·{' '}
                        {t('market.macro.loanRates.sourceHint', 'TCMB EVDS, haftalık akım veri')}
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(132px, 1fr))', gap: 8 }}>
                        {(latest?.items ?? []).map((it) => (
                            <div
                                key={it.type}
                                style={{ border: `1px solid ${tokens.border}`, borderRadius: 8, padding: 8, background: tokens.bgCard }}
                            >
                                <div style={{ fontSize: 10, color: tokens.textMuted, lineHeight: 1.25 }}>
                                    {t(loanTypeToI18nKey(it.type), it.label)}
                                </div>
                                <div style={{ fontSize: 15, fontWeight: 700, color: tokens.text, marginTop: 4 }}>
                                    {formatBorrowingCostPercent(it.value)}
                                </div>
                                <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4, lineHeight: 1.3 }}>{it.seriesCode}</div>
                            </div>
                        ))}
                    </div>
                    <p style={{ fontSize: 10, color: tokens.textMuted, margin: '10px 0 6px', lineHeight: 1.45 }}>
                        {t(
                            'market.macro.loanRates.notReturn',
                            'Bu oranlar yatırım getirisi değil, borçlanma maliyetidir.',
                        )}
                    </p>
                    <p style={{ fontSize: 10, color: tokens.text, margin: '0 0 10px', lineHeight: 1.45 }}>
                        {t(
                            'market.macro.loanRates.uxExplainer',
                            'Kredi faizleri yatırım getirisi değildir; yeni kredi kullanmanın yaklaşık piyasa maliyetini gösterir. Mevduat faizi tasarruf sahibinin getirisini, kredi faizi ise borçlanan kişinin maliyetini ifade eder.',
                        )}
                    </p>
                </>
            ) : !usePanelLoans && latest == null ? (
                <p style={{ fontSize: 11, color: tokens.textMuted, margin: 0 }}>
                    {t('market.macro.loanRates.empty', 'EVDS kredi faizi verisi henüz alınamadı.')}
                </p>
            ) : !usePanelLoans && !hasLatestItems ? (
                <p style={{ fontSize: 11, color: tokens.textMuted, margin: 0 }}>
                    {t('market.macro.loanRates.empty', 'EVDS kredi faizi verisi henüz alınamadı.')}
                </p>
            ) : (
                <p style={{ fontSize: 11, color: tokens.textMuted, margin: 0 }}>
                    {t('market.macro.loanRates.empty', 'EVDS kredi faizi verisi henüz alınamadı.')}
                </p>
            )}

            {!loading && showCompareChart ? (
                <>
                    <div style={{ fontSize: 11, fontWeight: 650, color: tokens.text, margin: '12px 0 6px' }}>
                        {usePanelLoans
                            ? t('market.macro.panel.loanFlowChartTitle', 'Kredi Faizleri - Haftalık Akım Veri')
                            : t('market.macro.loanRates.chartTitle', 'Kredi Faizleri Karşılaştırması')}
                    </div>
                    {usePanelLoans ? (
                        <p style={{ fontSize: 10, color: tokens.textMuted, margin: '0 0 8px', lineHeight: 1.45 }}>
                            {t(
                                'market.macro.panel.loanFlowChartDisclaimer',
                                'Bu oranlar yatırım getirisi değil, yeni kredi kullanmanın borçlanma maliyetidir.',
                            )}
                        </p>
                    ) : null}
                    <div style={{ width: '100%', height: 200 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={compareChartData} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                                <XAxis dataKey="date" tick={{ fontSize: 8, fill: tokens.textMuted }} />
                                <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={40} />
                                <Tooltip
                                    formatter={(v) => {
                                        const n = Number(v);
                                        return [Number.isFinite(n) ? `${n.toLocaleString(locale, { maximumFractionDigits: 2 })}%` : '—', ''];
                                    }}
                                />
                                <Legend wrapperStyle={{ fontSize: 9 }} />
                                <Line type="monotone" dataKey="CONSUMER_TRY" name={t('market.macro.loanRates.consumer', 'İhtiyaç')} stroke="#f97316" dot={false} strokeWidth={2} connectNulls />
                                <Line type="monotone" dataKey="VEHICLE_TRY" name={t('market.macro.loanRates.vehicle', 'Taşıt')} stroke="#22c55e" dot={false} strokeWidth={2} connectNulls />
                                <Line type="monotone" dataKey="HOUSING_TRY" name={t('market.macro.loanRates.housing', 'Konut')} stroke="#3b82f6" dot={false} strokeWidth={2} connectNulls />
                                <Line type="monotone" dataKey="COMMERCIAL_TRY" name={t('market.macro.loanRates.commercial', 'Ticari')} stroke="#a855f7" dot={false} strokeWidth={2} connectNulls />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                </>
            ) : null}

            {!loading && policyVsEffective.length > 0 ? (
                <>
                    <div style={{ fontSize: 11, fontWeight: 650, color: tokens.text, margin: '12px 0 6px' }}>
                        {t('market.macro.loanRates.borrowingCostTrend', 'Borçlanma Maliyeti Trendi')}
                    </div>
                    <div style={{ fontSize: 9, color: tokens.textMuted, marginBottom: 4, lineHeight: 1.35 }}>
                        {t(
                            'market.macro.loanRates.policyVsConsumerHint',
                            'Politika faizi son yayınlanan aya ait tek değer olarak yatay kıyaslanır (aylık seri; haftalık kredi ile doğrudan örtüşmez).',
                        )}
                    </div>
                    <div style={{ width: '100%', height: 200 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={policyVsEffective} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                                <XAxis dataKey="date" tick={{ fontSize: 8, fill: tokens.textMuted }} />
                                <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={40} />
                                <Tooltip
                                    formatter={(v) => {
                                        const n = Number(v);
                                        return [Number.isFinite(n) ? `${n.toLocaleString(locale, { maximumFractionDigits: 2 })}%` : '—', ''];
                                    }}
                                />
                                <Legend wrapperStyle={{ fontSize: 9 }} />
                                <Line type="monotone" dataKey="consumer" name={t('market.macro.loanRates.consumer', 'İhtiyaç')} stroke="#f97316" dot={false} strokeWidth={2} connectNulls />
                                <Line type="monotone" dataKey="policy" name={t('market.bondMacroTcmbPolicyRateTitle', 'TCMB Politika Faizi')} stroke="#64748b" dot={false} strokeWidth={2} connectNulls />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                </>
            ) : !loading && consumerHistLen > 0 && (usePanelLoans ? policyVsConsumerDataPanel.length === 0 : policyQ.data == null || policyQ.data.valuePercent == null) ? (
                <>
                    <div style={{ fontSize: 11, fontWeight: 650, color: tokens.text, margin: '12px 0 6px' }}>
                        {t('market.macro.loanRates.borrowingCostTrend', 'Borçlanma Maliyeti Trendi')}
                    </div>
                    <p style={{ fontSize: 9, color: tokens.textMuted, margin: '0 0 6px', lineHeight: 1.35 }}>
                        {t('market.macro.loanRates.policyChartTodo', 'Politika faizi verisi yok; yalnızca ihtiyaç kredisi haftalık serisi gösterilir.')}
                    </p>
                    <div style={{ width: '100%', height: 200 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart
                                data={
                                    usePanelLoans
                                        ? consumerPanelHistory.map((p) => ({
                                              date: p.date,
                                              consumer: p.value,
                                          }))
                                        : consumerHistory.map((p) => ({
                                              date: String(p.date).slice(0, 10),
                                              consumer: Number(p.value),
                                          }))
                                }
                                margin={{ top: 4, right: 8, left: 0, bottom: 0 }}
                            >
                                <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                                <XAxis dataKey="date" tick={{ fontSize: 8, fill: tokens.textMuted }} />
                                <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={40} />
                                <Tooltip
                                    formatter={(v) => {
                                        const n = Number(v);
                                        return [Number.isFinite(n) ? `${n.toLocaleString(locale, { maximumFractionDigits: 2 })}%` : '—', ''];
                                    }}
                                />
                                <Legend wrapperStyle={{ fontSize: 9 }} />
                                <Line type="monotone" dataKey="consumer" name={t('market.macro.loanRates.consumer', 'İhtiyaç')} stroke="#f97316" dot={false} strokeWidth={2} connectNulls />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                </>
            ) : null}

            {!loading && spreadEffective.length > 0 ? (
                <>
                    <div style={{ fontSize: 11, fontWeight: 650, color: tokens.text, margin: '12px 0 6px' }}>
                        {t('market.macro.loanRates.spreadTitle', 'Kredi-Mevduat Faiz Farkı')}
                    </div>
                    <div style={{ width: '100%', height: 180 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={spreadEffective} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="var(--chart-grid)" />
                                <XAxis dataKey="date" tick={{ fontSize: 8, fill: tokens.textMuted }} />
                                <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={40} />
                                <Tooltip
                                    formatter={(v) => {
                                        const n = Number(v);
                                        return [Number.isFinite(n) ? `${n.toLocaleString(locale, { maximumFractionDigits: 2 })}%` : '—', ''];
                                    }}
                                />
                                <Legend wrapperStyle={{ fontSize: 9 }} />
                                <Line type="monotone" dataKey="spread" name={t('market.macro.loanRates.spreadSeries', 'İhtiyaç − TL 1M mevduat')} stroke="#0ea5e9" dot={false} strokeWidth={2} connectNulls />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                </>
            ) : !loading && consumerHistLen > 0 ? (
                <p style={{ fontSize: 10, color: tokens.textMuted, margin: '10px 0 0', lineHeight: 1.4 }}>
                    {t(
                        'market.macro.loanRates.spreadPlaceholder',
                        'Mevduat faizi bağlandığında kredi-mevduat farkı hesaplanacak.',
                    )}
                </p>
            ) : null}
        </div>
    );
}

function MacroDerivedKpiGrid({
    tokens,
    t,
    locale,
    panel,
    panelLoading,
}: {
    tokens: Theme;
    t: (key: string, defaultValue: string) => string;
    locale: string;
    panel: InterestInflationMacroPanelResponse | null | undefined;
    panelLoading: boolean;
}) {
    const awaiting = t('market.macro.panel.awaitingData', 'Veri bekleniyor');
    const fmtD = (v: number | null | undefined) =>
        v == null || !Number.isFinite(Number(v)) ? awaiting : formatPercent2(v, locale);

    const s = panel?.series;
    const d = panel?.derived;
    const cpi = selectMacroSeries(s, 'CPI_TR_INDEX');
    const ppi = selectMacroSeries(s, 'PPI_TR_INDEX');
    const pol = selectMacroSeries(s, 'POLICY_RATE_TR');
    const dep1m = selectMacroSeries(s, 'DEPOSIT_RATE_TRY_1M_WEEKLY');
    const consLoan = selectMacroSeries(s, 'LOAN_RATE_CONSUMER_WEEKLY');
    const cpiD = lastObservation(cpi)?.date;
    const ppiD = lastObservation(ppi)?.date;
    const polO = lastObservation(pol);
    const depD = lastObservation(dep1m)?.date;
    const consD = lastObservation(consLoan)?.date;

    const freqMo = t('market.macro.panel.freqMonthly', 'Aylık');
    const freqW = t('market.macro.panel.freqWeeklyFlow', 'Haftalık akım');

    const tiles: { title: string; val: string; date?: string; freq: string }[] = [
        { title: t('macro.inflation.kpi.cpiYoY', 'TÜFE Yıllık'), val: fmtD(d?.cpiYoY ?? null), date: cpiD, freq: freqMo },
        { title: t('macro.inflation.kpi.cpiMoM', 'TÜFE Aylık'), val: fmtD(d?.cpiMoM ?? null), date: cpiD, freq: freqMo },
        { title: t('macro.inflation.kpi.ppiYoY', 'Yİ-ÜFE Yıllık'), val: fmtD(d?.ppiYoY ?? null), date: ppiD, freq: freqMo },
        {
            title: t('market.bondMacroTcmbPolicyRateTitle', 'TCMB Politika Faizi'),
            val:
                polO != null && Number.isFinite(Number(polO.value))
                    ? formatPercent2(Number(polO.value), locale)
                    : awaiting,
            date: polO?.date,
            freq: freqMo,
        },
        { title: t('macro.overview.kpi.realPolicyRate', 'Reel Politika Faizi'), val: fmtD(d?.realPolicyRate ?? null), date: polO?.date, freq: freqMo },
        { title: t('market.macro.panel.kpiRealDeposit1m', '1M TL Mevduat Reel Farkı'), val: fmtD(d?.realDepositRate ?? null), date: depD, freq: freqW },
        {
            title: t('market.macro.panel.kpiConsumerMinusPolicy', 'İhtiyaç Kredisi − Politika Faizi'),
            val: fmtD(d?.consumerLoanMinusPolicyRate ?? null),
            date: consD,
            freq: freqW,
        },
        {
            title: t('market.macro.panel.kpiConsumerMinusDeposit', 'İhtiyaç Kredisi − Mevduat Makası'),
            val: fmtD(d?.consumerLoanMinusDepositRate ?? null),
            date: consD,
            freq: freqW,
        },
    ];

    if (panelLoading) {
        return (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(148px, 1fr))', gap: 8 }}>
                {Array.from({ length: 8 }).map((_, i) => (
                    <div
                        key={i}
                        style={{
                            border: `1px solid ${tokens.border}`,
                            borderRadius: 8,
                            padding: 10,
                            minHeight: 88,
                            background: tokens.bgCard,
                        }}
                    >
                        <div
                            style={{
                                height: 10,
                                width: '62%',
                                background: 'var(--app-surface-soft-bg)',
                                borderRadius: 4,
                                marginBottom: 10,
                            }}
                        />
                        <div
                            style={{
                                height: 14,
                                width: '42%',
                                background: 'var(--app-surface-soft-bg)',
                                borderRadius: 4,
                            }}
                        />
                    </div>
                ))}
            </div>
        );
    }

    return (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(148px, 1fr))', gap: 8 }}>
            {tiles.map((tile, i) => (
                <div
                    key={i}
                    style={{
                        border: `1px solid ${tokens.border}`,
                        borderRadius: 8,
                        padding: 10,
                        background: tokens.bgCard,
                        minHeight: 88,
                    }}
                >
                    <div
                        style={{
                            fontSize: 10,
                            color: tokens.textMuted,
                            marginBottom: 6,
                            lineHeight: 1.35,
                            minHeight: '2.4em',
                        }}
                    >
                        {tile.title}
                    </div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: tokens.text }}>{tile.val}</div>
                    <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4, lineHeight: 1.35 }}>
                        {tile.date ? `${formatLocaleDate(tile.date, locale)} · ${tile.freq}` : '—'}
                    </div>
                </div>
            ))}
        </div>
    );
}

function MacroTcmbWeightedFundingCard({
    tokens,
    t,
    locale,
    panel,
    panelLoading,
}: {
    tokens: Theme;
    t: (key: string, defaultValue: string) => string;
    locale: string;
    panel: InterestInflationMacroPanelResponse | null | undefined;
    panelLoading: boolean;
}) {
    const awaiting = t('market.macro.panel.awaitingData', 'Veri bekleniyor');
    const label = t('market.bondMacroTcmbWeightedFunding', 'TCMB Ortalama Fonlama Maliyeti');

    if (panelLoading) {
        return (
            <div
                style={{
                    border: `1px solid ${tokens.border}`,
                    borderRadius: 8,
                    padding: 10,
                    background: tokens.bgCard,
                    minHeight: 72,
                }}
            >
                <div
                    style={{
                        fontSize: 11,
                        color: tokens.textMuted,
                        marginBottom: 8,
                        lineHeight: 1.35,
                        wordBreak: 'break-word',
                        minHeight: '2.6em',
                    }}
                >
                    {label}
                </div>
                <div
                    style={{
                        height: 10,
                        borderRadius: 4,
                        background: 'var(--app-surface-soft-bg)',
                        width: '72%',
                    }}
                />
            </div>
        );
    }

    const obs = lastObservation(selectMacroSeries(panel?.series, 'TCMB_WEIGHTED_AVG_FUNDING_COST_TR'));
    const hasVal = obs != null && Number.isFinite(Number(obs.value));

    return (
        <div
            style={{
                border: `1px solid ${tokens.border}`,
                borderRadius: 8,
                padding: 10,
                background: tokens.bgCard,
                minHeight: 72,
            }}
        >
            <div
                style={{
                    fontSize: 11,
                    color: tokens.textMuted,
                    marginBottom: 8,
                    lineHeight: 1.35,
                    wordBreak: 'break-word',
                    minHeight: '2.6em',
                }}
            >
                {label}
            </div>
            <div style={{ fontSize: 14, fontWeight: 700, color: tokens.text }}>
                {hasVal ? formatPercent2(Number(obs.value), locale) : awaiting}
            </div>
            <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4, lineHeight: 1.35 }}>
                {hasVal && obs.date ? formatLocaleDate(obs.date, locale) : '—'}
            </div>
        </div>
    );
}

export function BondMacroIntelligencePanel({ tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-GB' : 'tr-TR';

    const panelQ = useQuery({
        queryKey: ['market', 'macro', 'interest-inflation-panel'],
        queryFn: ({ signal }) => fetchInterestInflationMacroPanel(signal),
    });
    const panel = panelQ.data;
    const panelLoading = panelQ.isPending;
    const panelError = panelQ.isError;

    const panelInflationFromPanel = useMemo(() => {
        const s = panel?.series;
        if (!s?.length) return false;
        const c = selectMacroSeries(s, 'CPI_TR_INDEX');
        const p = selectMacroSeries(s, 'PPI_TR_INDEX');
        return (c?.observations?.length ?? 0) > 0 || (p?.observations?.length ?? 0) > 0;
    }, [panel?.series]);

    const usePanelLoans = useMemo(() => {
        const m = mergeCreditSeriesChart(panel?.series, [...LOAN_CHART_LOGICAL_KEYS]);
        return m.some(
            (row) =>
                Number.isFinite(Number(row.CONSUMER_TRY)) ||
                Number.isFinite(Number(row.VEHICLE_TRY)) ||
                Number.isFinite(Number(row.HOUSING_TRY)) ||
                Number.isFinite(Number(row.COMMERCIAL_TRY)),
        );
    }, [panel?.series]);

    const usePanelDepositsTry = useMemo(() => {
        const m = mergeDepositTryChart(panel?.series, [...DEPOSIT_TRY_CHART_LOGICAL_KEYS]);
        return m.length > 0;
    }, [panel?.series]);

    const macroPanelNotes = useMemo(
        () => (panel?.notes ?? []).filter((n) => !isTahvilMacroPanelNote(String(n))),
        [panel?.notes],
    );

    return (
        <div
            className="terminal-card"
            style={{
                marginBottom: 12,
                border: `1px solid ${tokens.border}`,
                background: tokens.bgCard,
            }}
        >
            <div style={{ padding: '12px 14px 10px' }}>
                <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: tokens.text }}>
                    {t('market.bondMacroPanelTitle', 'Makro Finans Paneli')}
                </h3>
                <p style={{ margin: '6px 0 0', fontSize: 11, color: tokens.textMuted, lineHeight: 1.35 }}>
                    {t(
                        'market.bondMacroPanelIntro',
                        'Makro göstergeler EVDS ve ilgili uçlardan beslenir: politika faizi, enflasyon endeksleri, kredi ve mevduat faizleri ile türetilmiş reel faiz ve makas özetleri.',
                    )}
                </p>
            </div>

            <div style={{ borderTop: `1px solid ${tokens.border}`, padding: '12px 14px' }}>
                <h4 style={{ margin: '0 0 8px', fontSize: 13, fontWeight: 650, color: tokens.text }}>
                    {t('market.bondMacroBlockA', 'A) Makro Faiz ve Enflasyon')}
                </h4>
                {panelError ? (
                    <div
                        style={{
                            border: `1px solid ${tokens.border}`,
                            borderRadius: 8,
                            padding: 10,
                            marginBottom: 10,
                            background: 'var(--app-surface-soft-bg)',
                        }}
                    >
                        <div style={{ fontSize: 11, fontWeight: 650, color: tokens.text }}>
                            {t('market.macro.panel.partialLoadTitle', 'Normalize makro paneli')}
                        </div>
                        <p style={{ margin: '6px 0 0', fontSize: 10, color: tokens.textMuted, lineHeight: 1.45 }}>
                            {t(
                                'market.macro.panel.fetchError',
                                'Bu bölüm şu an yüklenemedi. KPI ve grafikler mümkün olan yerde mevcut EVDS uçlarıyla yedeklenir.',
                            )}
                        </p>
                    </div>
                ) : null}
                <MacroDerivedKpiGrid
                    tokens={tokens}
                    t={t}
                    locale={locale}
                    panel={panelError ? undefined : panel}
                    panelLoading={panelLoading}
                />
                {panel?.generatedAt && !panelError ? (
                    <p style={{ fontSize: 10, color: tokens.textMuted, margin: '8px 0 0', lineHeight: 1.35 }}>
                        {t('market.macro.panel.generatedAt', 'Özet zamanı')}:{' '}
                        {formatLocaleDate(String(panel.generatedAt).slice(0, 10), locale)}
                    </p>
                ) : null}
                <div style={{ marginTop: 10 }}>
                    <MacroTcmbWeightedFundingCard
                        tokens={tokens}
                        t={t}
                        locale={locale}
                        panel={panelError ? undefined : panel}
                        panelLoading={panelLoading}
                    />
                </div>
                <MacroInflationEvdsBlock
                    tokens={tokens}
                    t={t}
                    locale={locale}
                    panel={panelError ? undefined : panel}
                    panelLoading={panelLoading}
                    fromPanel={panelInflationFromPanel && !panelError}
                />
                <MacroLoanRatesEvdsBlock
                    tokens={tokens}
                    t={t}
                    locale={locale}
                    panel={panelError ? undefined : panel}
                    panelLoading={panelLoading}
                    usePanelLoans={usePanelLoans && !panelError}
                />
                <MacroDepositRatesEvdsBlock
                    tokens={tokens}
                    t={t}
                    locale={locale}
                    panel={panelError ? undefined : panel}
                    panelLoading={panelLoading}
                    usePanelDepositsTry={usePanelDepositsTry && !panelError}
                />
                <MacroFxDepositRatesPanel
                    tokens={tokens}
                    t={t}
                    locale={locale}
                    panel={panelError ? undefined : panel}
                    panelLoading={panelLoading}
                    panelError={panelError}
                />
                {panel != null && macroPanelNotes.length > 0 && !panelError ? (
                    <div style={{ marginTop: 14, fontSize: 10, color: tokens.textMuted, lineHeight: 1.45 }}>
                        <strong style={{ color: tokens.text }}>
                            {t('market.macro.panel.dataNotes', 'Veri notları')}
                        </strong>
                        <ul style={{ margin: '6px 0 0', paddingLeft: 18 }}>
                            {macroPanelNotes.map((n, i) => (
                                <li key={i}>{n}</li>
                            ))}
                        </ul>
                    </div>
                ) : null}
            </div>
        </div>
    );
}
