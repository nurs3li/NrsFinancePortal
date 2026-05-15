import { useEffect, useRef, useState, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { createChart } from 'lightweight-charts';
import type {
    IChartApi,
    ISeriesApi,
    CandlestickData,
} from 'lightweight-charts';
import {
    LineChart,
    Line,
    XAxis,
    YAxis,
    Tooltip,
    ResponsiveContainer,
    CartesianGrid,
    Legend,
} from 'recharts';
import { marketClient } from '../api/client';
import type { LatestPriceRow } from '../components/market/marketTypes';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';

type TabId = 'doviz' | 'crypto' | 'metals' | 'funds' | 'equity';

type LatestPrice = LatestPriceRow;

type CandlePoint = {
    t: string;
    o: number;
    h: number;
    l: number;
    c: number;
    v: number;
};

type BatchHistoryResponse = {
    series: Record<string, CandlePoint[]>;
};

type IndicatorPoint = {
    t: string;
    value: number;
};

type IndicatorsResponse = {
    type: string;
    symbol: string;
    days: number;
    close: IndicatorPoint[];
    ma: Record<string, IndicatorPoint[]>;
    trend: {
        direction: string;
        slope: number;
        normalizedReturn: number;
        strength: number;
    };
};

const DAYS_OPTIONS = [1, 7, 14, 30, 90, 180];
const COMPARE_COLORS = ['#3b82f6', '#22c55e', '#eab308', '#ef4444'];

function getMarketType(tab: TabId): 'FX' | 'CRYPTO' | 'METALS' | 'FUNDS' | 'EQUITY' {
    switch (tab) {
        case 'doviz':
            return 'FX';
        case 'crypto':
            return 'CRYPTO';
        case 'metals':
            return 'METALS';
        case 'funds':
            return 'FUNDS';
        case 'equity':
            return 'EQUITY';
        default:
            return 'FX';
    }
}

function getTabFromType(type?: string | null): TabId | null {
    switch (type) {
        case 'FX':
            return 'doviz';
        case 'CRYPTO':
            return 'crypto';
        case 'METALS':
            return 'metals';
        case 'FUNDS':
            return 'funds';
        case 'EQUITY':
            return 'equity';
        default:
            return null;
    }
}

export function AdvancedMarket() {
    const { tokens } = useTheme();
    const { t } = useLanguage();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    const containerRef = useRef<HTMLDivElement | null>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
    const ma7SeriesRef = useRef<ISeriesApi<'Line'> | null>(null);
    const ma30SeriesRef = useRef<ISeriesApi<'Line'> | null>(null);
    const ma90SeriesRef = useRef<ISeriesApi<'Line'> | null>(null);
    const lastCandlesRef = useRef<CandlePoint[]>([]);

    const [activeTab, setActiveTab] = useState<TabId>('doviz');
    const [latestFx, setLatestFx] = useState<Record<string, LatestPrice>>({});
    const [latestCrypto, setLatestCrypto] = useState<Record<string, LatestPrice>>({});
    const [latestMetals, setLatestMetals] = useState<Record<string, LatestPrice>>({});
    const [latestFunds, setLatestFunds] = useState<Record<string, LatestPrice>>({});
    const [latestEquity, setLatestEquity] = useState<Record<string, LatestPrice>>({});

    const [selectedSymbol, setSelectedSymbol] = useState<string>('USDTRY');
    const [days, setDays] = useState<number>(30);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);
    const [tooltip, setTooltip] = useState<{
        date: string;
        o: number;
        h: number;
        l: number;
        c: number;
        pctChange: number;
    } | null>(null);

    const [compareSymbols, setCompareSymbols] = useState<string[]>([]);
    const [compareData, setCompareData] = useState<{ date: string; [key: string]: number | string }[]>([]);
    const [loadingCompare, setLoadingCompare] = useState(false);

    const tabs: { id: TabId; label: string }[] = [
        { id: 'doviz', label: 'Döviz' },
        { id: 'crypto', label: 'Kripto' },
        { id: 'metals', label: 'Kıymetli madenler' },
        { id: 'funds', label: 'Fonlar' },
        { id: 'equity', label: 'Hisse' },
    ];

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
    };

    const titleStyle: React.CSSProperties = {
        fontSize: '1.75rem',
        fontWeight: 700,
        marginBottom: 4,
        display: 'flex',
        alignItems: 'center',
        gap: 12,
    };

    const backButtonStyle: React.CSSProperties = {
        padding: '4px 10px',
        borderRadius: 999,
        border: `1px solid ${tokens.border}`,
        background: tokens.bgCard,
        color: tokens.text,
        fontSize: '0.8rem',
        cursor: 'pointer',
    };

    const mutedStyle: React.CSSProperties = {
        color: tokens.textMuted,
        fontSize: '0.875rem',
    };

    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        marginBottom: 16,
    };

    const loadLatestAll = useCallback(() => {
        return Promise.all([
            marketClient.get<Record<string, LatestPrice>>('/api/market/doviz/latest').then((r) => r.data),
            marketClient.get<Record<string, LatestPrice>>('/api/market/crypto/latest').then((r) => r.data),
            marketClient.get<Record<string, LatestPrice>>('/api/market/metals/latest').then((r) => r.data),
            marketClient.get<Record<string, LatestPrice>>('/api/market/funds/latest').then((r) => r.data),
            marketClient.get<Record<string, LatestPrice>>('/api/market/equity/latest').then((r) => r.data),

        ]).then(([fx, crypto, metals, funds, equity]) => {
            setLatestFx(fx ?? {});
            setLatestCrypto(crypto ?? {});
            setLatestMetals(metals ?? {});
            setLatestFunds(funds ?? {});
            setLatestEquity(equity ?? {});
        });
    }, []);

    const getSymbolsForTab = (tab: TabId): string[] => {
        const map: Record<TabId, Record<string, LatestPrice>> = {
            doviz: latestFx,
            crypto: latestCrypto,
            metals: latestMetals,
            funds: latestFunds,
            equity: latestEquity,
        };
        const data = map[tab];
        if (!data) return [];
        return Object.keys(data).filter(
            (k) => data[k] && typeof data[k] === 'object' && data[k].status !== 'NO_DATA',
        );
    };

    const fetchBatchHistory = useCallback(
        (tab: TabId, symbol: string, d: number) => {
            const type = getMarketType(tab);
            return marketClient
                .get<BatchHistoryResponse>('/api/market/history/batch', {
                    params: { type, symbols: symbol, days: d },
                })
                .then((res) => res.data);
        },
        [],
    );

    const fetchBatchHistoryMulti = useCallback(
        (tab: TabId, symbols: string[], d: number) => {
            if (symbols.length < 2) return Promise.resolve<BatchHistoryResponse>({ series: {} });
            const type = getMarketType(tab);
            return marketClient
                .get<BatchHistoryResponse>('/api/market/history/batch', {
                    params: { type, symbols: symbols.join(','), days: d },
                })
                .then((res) => res.data);
        },
        [],
    );

    const fetchIndicators = useCallback((tab: TabId, symbol: string, d: number) => {
        const type = getMarketType(tab);
        const allMa = [7, 30, 90];
        const allowedMa = allMa.filter((w) => w <= d);
        const maParam = allowedMa.join(',');

        return marketClient
            .get<IndicatorsResponse>('/api/market/indicators', {
                params: { type, symbol, days: d, ma: maParam },
            })
            .then((res) => res.data);
    }, []);

    useEffect(() => {
        const typeParam = searchParams.get('type');
        const symbolParam = searchParams.get('symbol');
        const daysParam = searchParams.get('days');

        const tabFromType = getTabFromType(typeParam);
        if (tabFromType) setActiveTab(tabFromType);
        if (symbolParam) setSelectedSymbol(symbolParam.toUpperCase());
        const parsedDays = daysParam ? Number(daysParam) : NaN;
        if (!Number.isNaN(parsedDays) && DAYS_OPTIONS.includes(parsedDays)) setDays(parsedDays);
    }, []);

    useEffect(() => {
        loadLatestAll().catch(() => {});
    }, [loadLatestAll]);

    useEffect(() => {
        if (!containerRef.current) return;

        const chart = createChart(containerRef.current, {
            width: containerRef.current.clientWidth,
            height: 420,
            layout: { background: { color: tokens.bgCard }, textColor: tokens.text },
            grid: { vertLines: { color: tokens.border }, horzLines: { color: tokens.border } },
            rightPriceScale: { borderColor: tokens.border },
            timeScale: { borderColor: tokens.border },
            crosshair: { mode: 1 },
        });

        const candleSeries = chart.addCandlestickSeries({
            upColor: '#22c55e',
            downColor: '#ef4444',
            borderVisible: false,
            wickUpColor: '#22c55e',
            wickDownColor: '#ef4444',
        });
        const ma7 = chart.addLineSeries({ color: '#3b82f6', lineWidth: 2 });
        const ma30 = chart.addLineSeries({ color: '#eab308', lineWidth: 2 });
        const ma90 = chart.addLineSeries({ color: '#ef4444', lineWidth: 2 });

        chartRef.current = chart;
        candleSeriesRef.current = candleSeries;
        ma7SeriesRef.current = ma7;
        ma30SeriesRef.current = ma30;
        ma90SeriesRef.current = ma90;

        chart.subscribeCrosshairMove((param) => {
            if (!param.time || typeof param.time !== 'string') {
                setTooltip(null);
                return;
            }
            const t = param.time.toString().slice(0, 10);
            const candles = lastCandlesRef.current;
            const c = candles.find((x) => x.t.slice(0, 10) === t);
            if (!c) {
                setTooltip(null);
                return;
            }
            const pctChange = c.o ? ((c.c - c.o) / c.o) * 100 : 0;
            setTooltip({
                date: t,
                o: c.o,
                h: c.h,
                l: c.l,
                c: c.c,
                pctChange,
            });
        });

        const handleResize = () => {
            if (!containerRef.current || !chartRef.current) return;
            chartRef.current.applyOptions({ width: containerRef.current.clientWidth });
        };
        window.addEventListener('resize', handleResize);
        return () => {
            window.removeEventListener('resize', handleResize);
            chart.remove();
            chartRef.current = null;
            candleSeriesRef.current = null;
            ma7SeriesRef.current = null;
            ma30SeriesRef.current = null;
            ma90SeriesRef.current = null;
        };
    }, [tokens]);

    useEffect(() => {
        if (!selectedSymbol) return;
        setLoading(true);
        setError(null);

        Promise.all([
            fetchBatchHistory(activeTab, selectedSymbol, days),
            fetchIndicators(activeTab, selectedSymbol, days),
        ])
            .then(([batch, indicators]) => {
                const candles = batch.series[selectedSymbol] ?? [];
                lastCandlesRef.current = candles;

                if (candleSeriesRef.current) {
                    const candleData: CandlestickData[] = candles.map((c) => ({
                        time: c.t.slice(0, 10),
                        open: c.o,
                        high: c.h,
                        low: c.l,
                        close: c.c,
                    }));
                    candleSeriesRef.current.setData(candleData);
                }

                const ma7 = indicators.ma['7'] ?? [];
                const ma30 = indicators.ma['30'] ?? [];
                const ma90 = indicators.ma['90'] ?? [];

                if (ma7SeriesRef.current) {
                    ma7SeriesRef.current.setData(
                        ma7.map((p) => ({ time: p.t.slice(0, 10), value: p.value })),
                    );
                }
                if (ma30SeriesRef.current) {
                    ma30SeriesRef.current.setData(
                        ma30.map((p) => ({ time: p.t.slice(0, 10), value: p.value })),
                    );
                }
                if (ma90SeriesRef.current && ma90.length > 0) {
                    ma90SeriesRef.current.setData(
                        ma90.map((p) => ({ time: p.t.slice(0, 10), value: p.value })),
                    );
                }

                if (chartRef.current && candles.length > 0) {
                    chartRef.current.timeScale().fitContent();
                }
            })
            .catch((e) => setError(e?.message ?? t('advanced.chartLoadFailed', 'Grafik verisi yüklenemedi')))
            .finally(() => setLoading(false));
    }, [activeTab, selectedSymbol, days, fetchBatchHistory, fetchIndicators]);

    const symbolsForTab = getSymbolsForTab(activeTab);
    const selectedLatest = (
        activeTab === 'doviz' ? latestFx :
        activeTab === 'crypto' ? latestCrypto :
        activeTab === 'metals' ? latestMetals :
        activeTab === 'funds' ? latestFunds :
        latestEquity
    )[selectedSymbol];

    useEffect(() => {
        if (!selectedSymbol && symbolsForTab.length > 0) setSelectedSymbol(symbolsForTab[0]);
    }, [symbolsForTab, selectedSymbol]);

    useEffect(() => {
        if (compareSymbols.length < 2) {
            setCompareData([]);
            return;
        }
        setLoadingCompare(true);
        const syms = compareSymbols.slice(0, 4);
        fetchBatchHistoryMulti(activeTab, syms, days)
            .then((batch) => {
                const byDate: Record<string, Record<string, number>> = {};
                Object.entries(batch.series).forEach(([sym, candles]) => {
                    candles.forEach((c) => {
                        const d = c.t.slice(0, 10);
                        if (!byDate[d]) byDate[d] = {};
                        byDate[d][sym] = c.c;
                    });
                });
                const dates = Object.keys(byDate).sort();
                if (dates.length === 0) {
                    setCompareData([]);
                    return;
                }
                const first: Record<string, number> = {};
                syms.forEach((sym) => {
                    const d = dates.find((dt) => byDate[dt][sym] != null);
                    if (d != null) first[sym] = byDate[d][sym];
                });
                const out = dates.map((date) => {
                    const row: { date: string; [key: string]: number | string } = {
                        date: new Date(date).toLocaleDateString('tr-TR', {
                            day: '2-digit',
                            month: '2-digit',
                        }),
                    };
                    syms.forEach((sym) => {
                        const v = byDate[date][sym];
                        const base = first[sym];
                        row[sym] = base && v != null ? Math.round((v / base) * 1000) / 10 : 0;
                    });
                    return row;
                });
                setCompareData(out);
            })
            .catch(() => setCompareData([]))
            .finally(() => setLoadingCompare(false));
    }, [compareSymbols, activeTab, days, fetchBatchHistoryMulti]);

    return (
        <div style={pageStyle}>
            <div style={titleStyle}>
                <button type="button" onClick={() => navigate('/market')} style={backButtonStyle}>
                    {t('advanced.backToMarketSummary', '← Piyasa özeti')}
                </button>
                <span>{t('advanced.title', 'Gelişmiş Piyasa Grafiği')}</span>
            </div>
            <p style={mutedStyle}>
                {t('advanced.subtitle', 'Mum grafik, hareketli ortalama (MA 7/30/90) ve karşılaştırma — TradingView benzeri deneyim.')}
            </p>

            <div style={{ display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' }}>
                {tabs.map((t) => (
                    <button
                        key={t.id}
                        type="button"
                        onClick={() => {
                            setActiveTab(t.id);
                            const syms = getSymbolsForTab(t.id);
                            setSelectedSymbol(syms[0] ?? '');
                        }}
                        style={{
                            padding: '8px 16px',
                            fontSize: '0.875rem',
                            fontWeight: activeTab === t.id ? 600 : 500,
                            background: activeTab === t.id ? tokens.accent : tokens.bgCard,
                            color: activeTab === t.id ? '#fff' : tokens.text,
                            border: `1px solid ${tokens.border}`,
                            borderRadius: 8,
                            cursor: 'pointer',
                        }}
                    >
                        {t.label}
                    </button>
                ))}
            </div>

            <div style={cardStyle}>
                <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 12 }}>
                    Mum Grafik + MA (7 / 30 / 90)
                </h2>
                <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
                    <label style={{ fontSize: '0.875rem' }}>
                        {t('trade.symbol', 'Sembol')}:
                        <select
                            value={selectedSymbol}
                            onChange={(e) => setSelectedSymbol(e.target.value)}
                            style={{
                                marginLeft: 8,
                                padding: '6px 10px',
                                borderRadius: 8,
                                border: `1px solid ${tokens.border}`,
                                background: (tokens as { inputBg?: string }).inputBg ?? tokens.bgCard,
                                color: tokens.text,
                                fontSize: '0.875rem',
                            }}
                        >
                            {symbolsForTab.map((s) => (
                                <option key={s} value={s}>
                                    {s}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label style={{ fontSize: '0.875rem' }}>
                        {t('advanced.period', 'Dönem')}:
                        <select
                            value={days}
                            onChange={(e) => setDays(Number(e.target.value))}
                            style={{
                                marginLeft: 8,
                                padding: '6px 10px',
                                borderRadius: 8,
                                border: `1px solid ${tokens.border}`,
                                background: (tokens as { inputBg?: string }).inputBg ?? tokens.bgCard,
                                color: tokens.text,
                                fontSize: '0.875rem',
                            }}
                        >
                            {DAYS_OPTIONS.map((d) => (
                                <option key={d} value={d}>
                                    {t('advanced.lastDays', 'Son')} {d} {t('dashboard.days', 'gün')}
                                </option>
                            ))}
                        </select>
                    </label>
                    <span style={{ ...mutedStyle, fontSize: '0.75rem' }}>
                        MA 7 (mavi) · MA 30 (sarı) · MA 90 (kırmızı)
                    </span>
                    {selectedLatest ? (
                        <span style={{ ...mutedStyle, fontSize: '0.75rem' }}>
                            Kaynak: {selectedLatest.source ?? '-'} · AsOf: {selectedLatest.asOf ?? selectedLatest.timestamp ?? '-'}
                            {selectedLatest.qualityFlag ? ` · ${selectedLatest.qualityFlag}` : ''}
                        </span>
                    ) : null}
                </div>

                {tooltip && (
                    <div
                        style={{
                            marginBottom: 8,
                            padding: 8,
                            borderRadius: 8,
                            background: tokens.bg,
                            border: `1px solid ${tokens.border}`,
                            fontSize: '0.8125rem',
                        }}
                    >
                        <strong>{tooltip.date}</strong> · O: {tooltip.o.toLocaleString('tr-TR')} · H:{' '}
                        {tooltip.h.toLocaleString('tr-TR')} · L: {tooltip.l.toLocaleString('tr-TR')} · C:{' '}
                        {tooltip.c.toLocaleString('tr-TR')} ·{' '}
                        <span style={{ color: tooltip.pctChange >= 0 ? '#22c55e' : '#ef4444' }}>
                            %Δ {(tooltip.pctChange >= 0 ? '' : '') + tooltip.pctChange.toFixed(2)}%
                        </span>
                    </div>
                )}

                {error && <p style={{ ...mutedStyle, color: tokens.error }}>{error}</p>}
                {loading && !error && <p style={mutedStyle}>{t('advanced.chartLoading', 'Grafik yükleniyor...')}</p>}

                <div
                    ref={containerRef}
                    style={{
                        width: '100%',
                        height: 420,
                        borderRadius: 12,
                        overflow: 'hidden',
                        border: `1px solid ${tokens.border}`,
                        background: tokens.bgCard,
                    }}
                />
            </div>

            <div style={cardStyle}>
                <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>
                    {t('market.comparisonChart', 'Karşılaştırma Grafiği (Baz 100)')}
                </h2>
                <p style={{ ...mutedStyle, marginBottom: 12 }}>
                    {t('advanced.compareHint', 'Aynı kategoriden 2–4 sembol seçin; ilk gün 100 kabul edilir.')}
                </p>
                <div style={{ marginBottom: 12, display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
                    {symbolsForTab.slice(0, 12).map((sym) => (
                        <label
                            key={sym}
                            style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: '0.8125rem' }}
                        >
                            <input
                                type="checkbox"
                                checked={compareSymbols.includes(sym)}
                                onChange={(e) => {
                                    if (e.target.checked) {
                                        setCompareSymbols((prev) =>
                                            prev.length >= 4 ? prev : [...prev, sym],
                                        );
                                    } else {
                                        setCompareSymbols((prev) => prev.filter((s) => s !== sym));
                                    }
                                }}
                            />
                            {sym}
                        </label>
                    ))}
                </div>
                {loadingCompare && <p style={mutedStyle}>{t('common.loading', 'Yükleniyor...')}</p>}
                {!loadingCompare && compareData.length === 0 && (
                    <p style={mutedStyle}>{t('market.comparePickTwo', 'Karşılaştırma için en az iki sembol seçin.')}</p>
                )}
                {!loadingCompare && compareData.length > 0 && (
                    <div style={{ width: '100%', height: 280 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart
                                data={compareData}
                                margin={{ top: 8, right: 16, left: 8, bottom: 8 }}
                            >
                                <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                                <XAxis
                                    dataKey="date"
                                    tick={{ fill: tokens.textMuted, fontSize: 11 }}
                                />
                                <YAxis
                                    tick={{ fill: tokens.textMuted, fontSize: 11 }}
                                    tickFormatter={(v) => String(v)}
                                />
                                <Tooltip
                                    contentStyle={{
                                        background: tokens.bgCard,
                                        border: `1px solid ${tokens.border}`,
                                        borderRadius: 8,
                                    }}
                                />
                                <Legend />
                                {compareSymbols.slice(0, 4).map((sym, i) => (
                                    <Line
                                        key={sym}
                                        type="monotone"
                                        dataKey={sym}
                                        name={sym}
                                        stroke={COMPARE_COLORS[i % COMPARE_COLORS.length]}
                                        strokeWidth={2}
                                        dot={false}
                                    />
                                ))}
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                )}
            </div>
        </div>
    );
}