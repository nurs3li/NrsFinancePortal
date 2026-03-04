import { useEffect, useRef, useState, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { createChart } from 'lightweight-charts';
import type {
    IChartApi,
    ISeriesApi,
    CandlestickData,
    LineData,
} from 'lightweight-charts';
import { marketClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';

type TabId = 'doviz' | 'crypto' | 'metals' | 'funds';

type LatestPrice = {
    symbol?: string;
    buyPrice?: number;
    sellPrice?: number;
    price?: number;
    source?: string;
    timestamp?: string;
    status?: string;
    message?: string;
};

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

const DAYS_OPTIONS = [30, 90, 180];

function getMarketType(tab: TabId): 'FX' | 'CRYPTO' | 'METALS' | 'FUNDS' {
    switch (tab) {
        case 'doviz':
            return 'FX';
        case 'crypto':
            return 'CRYPTO';
        case 'metals':
            return 'METALS';
        case 'funds':
            return 'FUNDS';
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
        default:
            return null;
    }
}

export function AdvancedMarket() {
    const { tokens } = useTheme();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    const containerRef = useRef<HTMLDivElement | null>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
    const ma7SeriesRef = useRef<ISeriesApi<'Line'> | null>(null);
    const ma30SeriesRef = useRef<ISeriesApi<'Line'> | null>(null);

    const [activeTab, setActiveTab] = useState<TabId>('doviz');
    const [latestFx, setLatestFx] = useState<Record<string, LatestPrice>>({});
    const [latestCrypto, setLatestCrypto] = useState<Record<string, LatestPrice>>({});
    const [latestMetals, setLatestMetals] = useState<Record<string, LatestPrice>>({});
    const [latestFunds, setLatestFunds] = useState<Record<string, LatestPrice>>({});

    const [selectedSymbol, setSelectedSymbol] = useState<string>('USDTRY');
    const [days, setDays] = useState<number>(30);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);

    const tabs: { id: TabId; label: string }[] = [
        { id: 'doviz', label: 'Döviz' },
        { id: 'crypto', label: 'Kripto' },
        { id: 'metals', label: 'Altın' },
        { id: 'funds', label: 'Fonlar' },
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
        ]).then(([fx, crypto, metals, funds]) => {
            setLatestFx(fx ?? {});
            setLatestCrypto(crypto ?? {});
            setLatestMetals(metals ?? {});
            setLatestFunds(funds ?? {});
        });
    }, []);

    const getSymbolsForTab = (tab: TabId): string[] => {
        const map: Record<TabId, Record<string, LatestPrice>> = {
            doviz: latestFx,
            crypto: latestCrypto,
            metals: latestMetals,
            funds: latestFunds,
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

    // İlk yüklemede query param'larından state ayarla
    useEffect(() => {
        const typeParam = searchParams.get('type');
        const symbolParam = searchParams.get('symbol');
        const daysParam = searchParams.get('days');

        const tabFromType = getTabFromType(typeParam);
        if (tabFromType) {
            setActiveTab(tabFromType);
        }

        if (symbolParam) {
            setSelectedSymbol(symbolParam.toUpperCase());
        }

        const parsedDays = daysParam ? Number(daysParam) : NaN;
        if (!Number.isNaN(parsedDays) && DAYS_OPTIONS.includes(parsedDays)) {
            setDays(parsedDays);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []); // sadece ilk render'da

    useEffect(() => {
        loadLatestAll().catch(() => {
            // ignore
        });
    }, [loadLatestAll]);

    useEffect(() => {
        if (!containerRef.current) return;

        const chart = createChart(containerRef.current, {
            width: containerRef.current.clientWidth,
            height: 420,
            layout: {
                background: { color: tokens.bgCard },
                textColor: tokens.text,
            },
            grid: {
                vertLines: { color: tokens.border },
                horzLines: { color: tokens.border },
            },
            rightPriceScale: {
                borderColor: tokens.border,
            },
            timeScale: {
                borderColor: tokens.border,
            },
            crosshair: {
                mode: 1,
            },
        });

        const candleSeries = chart.addCandlestickSeries({
            upColor: '#22c55e',
            downColor: '#ef4444',
            borderVisible: false,
            wickUpColor: '#22c55e',
            wickDownColor: '#ef4444',
        });

        const ma7 = chart.addLineSeries({
            color: '#3b82f6',
            lineWidth: 2,
        });

        const ma30 = chart.addLineSeries({
            color: '#eab308',
            lineWidth: 2,
        });

        chartRef.current = chart;
        candleSeriesRef.current = candleSeries;
        ma7SeriesRef.current = ma7;
        ma30SeriesRef.current = ma30;

        const handleResize = () => {
            if (!containerRef.current || !chartRef.current) return;
            chartRef.current.applyOptions({
                width: containerRef.current.clientWidth,
            });
        };

        window.addEventListener('resize', handleResize);

        return () => {
            window.removeEventListener('resize', handleResize);
            chart.remove();
            chartRef.current = null;
            candleSeriesRef.current = null;
            ma7SeriesRef.current = null;
            ma30SeriesRef.current = null;
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

                if (ma7SeriesRef.current) {
                    const ma7Data: LineData[] = ma7.map((p) => ({
                        time: p.t.slice(0, 10),
                        value: p.value,
                    }));
                    ma7SeriesRef.current.setData(ma7Data);
                }

                if (ma30SeriesRef.current) {
                    const ma30Data: LineData[] = ma30.map((p) => ({
                        time: p.t.slice(0, 10),
                        value: p.value,
                    }));
                    ma30SeriesRef.current.setData(ma30Data);
                }

                if (chartRef.current && candles.length > 0) {
                    chartRef.current.timeScale().fitContent();
                }
            })
            .catch((e) => {
                setError(e?.message ?? 'Grafik verisi yüklenemedi');
            })
            .finally(() => setLoading(false));
    }, [activeTab, selectedSymbol, days, fetchBatchHistory, fetchIndicators]);

    const symbolsForTab = getSymbolsForTab(activeTab);

    useEffect(() => {
        if (!selectedSymbol && symbolsForTab.length > 0) {
            setSelectedSymbol(symbolsForTab[0]);
        }
    }, [symbolsForTab, selectedSymbol]);

    return (
        <div style={pageStyle}>
            <div style={titleStyle}>
                <button
                    type="button"
                    onClick={() => navigate('/market')}
                    style={backButtonStyle}
                >
                    ← Piyasa özeti
                </button>
                <span>Gelişmiş Piyasa Grafiği</span>
            </div>
            <p style={mutedStyle}>
                Mum grafik, hareketli ortalama (MA) ve gelişmiş görünümler — TradingView benzeri
                deneyim.
            </p>

            <div
                style={{
                    display: 'flex',
                    gap: 8,
                    marginBottom: 20,
                    flexWrap: 'wrap',
                }}
            >
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
                            background:
                                activeTab === t.id ? tokens.accent : tokens.bgCard,
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
                <h2
                    style={{
                        fontSize: '1rem',
                        fontWeight: 600,
                        marginBottom: 12,
                    }}
                >
                    Mum Grafik + MA (7 / 30)
                </h2>

                <div
                    style={{
                        marginBottom: 12,
                        display: 'flex',
                        alignItems: 'center',
                        gap: 16,
                        flexWrap: 'wrap',
                    }}
                >
                    <label style={{ fontSize: '0.875rem' }}>
                        Sembol:
                        <select
                            value={selectedSymbol}
                            onChange={(e) => setSelectedSymbol(e.target.value)}
                            style={{
                                marginLeft: 8,
                                padding: '6px 10px',
                                borderRadius: 8,
                                border: `1px solid ${tokens.border}`,
                                background:
                                    (tokens as { inputBg?: string }).inputBg ??
                                    tokens.bgCard,
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
                        Dönem:
                        <select
                            value={days}
                            onChange={(e) => setDays(Number(e.target.value))}
                            style={{
                                marginLeft: 8,
                                padding: '6px 10px',
                                borderRadius: 8,
                                border: `1px solid ${tokens.border}`,
                                background:
                                    (tokens as { inputBg?: string }).inputBg ??
                                    tokens.bgCard,
                                color: tokens.text,
                                fontSize: '0.875rem',
                            }}
                        >
                            {DAYS_OPTIONS.map((d) => (
                                <option key={d} value={d}>
                                    Son {d} gün
                                </option>
                            ))}
                        </select>
                    </label>
                </div>

                {error && <p style={{ ...mutedStyle, color: tokens.error }}>{error}</p>}
                {loading && !error && <p style={mutedStyle}>Grafik yükleniyor...</p>}

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
        </div>
    );
}