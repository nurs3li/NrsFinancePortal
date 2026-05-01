import { useEffect, useMemo, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import type { ISeriesApi, Time, CandlestickData } from 'lightweight-charts';

type CandleVM = {
    time: string;
    open: number;
    high: number;
    low: number;
    close: number;
    volume?: number;
};

type MarkerVM = {
    time: string;
    position: 'aboveBar' | 'belowBar';
    color: string;
    shape: 'circle' | 'square' | 'arrowUp' | 'arrowDown';
    text: string;
};

type ThemeSlice = {
    bg: string;
    bgCard: string;
    border: string;
    text: string;
    textMuted: string;
};

type NewsMarkerLite = {
    id: string;
    time: string;
    tone: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
    count: number;
    items: { id: number; title: string; source?: string | null; publishedAt: string }[];
    matchedSymbols: string[];
    relevanceScore: number;
    impact: 'HIGH' | 'MEDIUM' | 'LOW';
};

type Props = {
    candles: CandleVM[];
    ma7: { time: string; value: number }[];
    ma21: { time: string; value: number }[];
    rsi14: { time: string; value: number }[];
    showMa: boolean;
    showRsi: boolean;
    markers: MarkerVM[];
    tokens: ThemeSlice;
    loading: boolean;
    symbol: string;
    trendLabel?: 'UP' | 'DOWN';
    timeframeLabel?: string;
    newsMarkers?: NewsMarkerLite[];
    onNewsSelect?: (marker: NewsMarkerLite | null) => void;
};

function toDayTime(value: string): Time {
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return value.slice(0, 10) as Time;
    return d.toISOString().slice(0, 10) as Time;
}
function toDayKey(value: string): string {
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return value.slice(0, 10);
    return d.toISOString().slice(0, 10);
}
function impactLabel(level: 'HIGH' | 'MEDIUM' | 'LOW'): string {
    if (level === 'HIGH') return 'Yüksek';
    if (level === 'MEDIUM') return 'Orta';
    return 'Düşük';
}

export function MarketTerminalChart({
    candles,
    ma7,
    ma21,
    rsi14,
    showMa,
    showRsi,
    markers,
    tokens,
    loading,
    symbol,
    trendLabel,
    timeframeLabel,
    newsMarkers = [],
    onNewsSelect,
}: Props) {
    const wrapRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<HTMLDivElement>(null);
    const [hoverData, setHoverData] = useState<CandleVM | null>(null);
    const [hoverNews, setHoverNews] = useState<NewsMarkerLite | null>(null);
    const newsByDay = useMemo(() => {
        const m = new Map<string, NewsMarkerLite>();
        newsMarkers.forEach((n) => m.set(toDayKey(n.time), n));
        return m;
    }, [newsMarkers]);

    const sortedCandles = useMemo(
        () => [...candles].sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime()),
        [candles]
    );
    const candleCount = sortedCandles.length;
    const compactBars = candleCount <= 6;

    useEffect(() => {
        const el = chartRef.current;
        if (!el || loading || !sortedCandles.length) return;

        const chart = createChart(el, {
            width: el.clientWidth,
            height: 430,
            layout: {
                background: { color: tokens.bgCard },
                textColor: tokens.text,
            },
            grid: {
                vertLines: { color: 'rgba(71, 85, 105, 0.3)' },
                horzLines: { color: 'rgba(71, 85, 105, 0.3)' },
            },
            rightPriceScale: { borderColor: tokens.border },
            timeScale: {
                borderColor: tokens.border,
                timeVisible: true,
                secondsVisible: false,
                barSpacing: compactBars ? 10 : 6,
                minBarSpacing: compactBars ? 8 : 4,
                rightOffset: compactBars ? 10 : 2,
            },
            crosshair: { mode: 1 },
        });

        const candleSeries = chart.addCandlestickSeries({
            upColor: '#22c55e',
            downColor: '#ef4444',
            wickUpColor: '#22c55e',
            wickDownColor: '#ef4444',
            borderVisible: false,
            priceLineVisible: false,
        });

        const candleData: CandlestickData[] = sortedCandles.map((c) => ({
            time: toDayTime(c.time),
            open: c.open,
            high: c.high,
            low: c.low,
            close: c.close,
        }));
        candleSeries.setData(candleData);
        const volumeSeries = chart.addHistogramSeries({
            color: 'rgba(56, 189, 248, 0.35)',
            priceFormat: { type: 'volume' },
            priceScaleId: '',
            lastValueVisible: false,
            priceLineVisible: false,
        });
        volumeSeries.priceScale().applyOptions({
            scaleMargins: {
                top: compactBars ? 0.9 : 0.84,
                bottom: 0,
            },
        });
        volumeSeries.setData(
            sortedCandles.map((c) => ({
                time: toDayTime(c.time),
                value: Number(c.volume ?? 0),
                color: c.close >= c.open ? 'rgba(34,197,94,0.45)' : 'rgba(239,68,68,0.45)',
            }))
        );
        candleSeries.setMarkers(
            markers.map((m) => ({
                ...m,
                time: toDayTime(m.time),
            }))
        );

        let ma7Series: ISeriesApi<'Line'> | null = null;
        let ma21Series: ISeriesApi<'Line'> | null = null;

        if (showMa) {
            ma7Series = chart.addLineSeries({
                color: '#38bdf8',
                lineWidth: 2,
                priceLineVisible: false,
                lastValueVisible: false,
            });
            ma7Series.setData(ma7.map((p) => ({ time: toDayTime(p.time), value: p.value })));

            ma21Series = chart.addLineSeries({
                color: '#f59e0b',
                lineWidth: 2,
                priceLineVisible: false,
                lastValueVisible: false,
            });
            ma21Series.setData(ma21.map((p) => ({ time: toDayTime(p.time), value: p.value })));
        }

        chart.subscribeCrosshairMove((param) => {
            if (!param?.time) {
                setHoverData(null);
                setHoverNews(null);
                return;
            }
            const ohlc = param.seriesData.get(candleSeries) as
                | { open: number; high: number; low: number; close: number }
                | undefined;
            if (!ohlc) {
                setHoverData(null);
                setHoverNews(null);
                return;
            }
            const day = String(param.time).slice(0, 10);
            setHoverNews(newsByDay.get(day) ?? null);
            setHoverData({
                time: String(param.time),
                open: Number(ohlc.open),
                high: Number(ohlc.high),
                low: Number(ohlc.low),
                close: Number(ohlc.close),
            });
        });

        chart.subscribeClick((param) => {
            if (!param?.time || !onNewsSelect) return;
            const day = String(param.time).slice(0, 10);
            onNewsSelect(newsByDay.get(day) ?? null);
        });

        chart.timeScale().fitContent();
        const onResize = () => chart.applyOptions({ width: el.clientWidth });
        window.addEventListener('resize', onResize);

        return () => {
            window.removeEventListener('resize', onResize);
            ma7Series = null;
            ma21Series = null;
            chart.remove();
        };
    }, [loading, sortedCandles, markers, showMa, ma7, ma21, tokens, newsByDay, onNewsSelect, compactBars]);

    if (loading) {
        return <div className="terminal-chart-empty">Grafik yükleniyor...</div>;
    }
    if (!sortedCandles.length) {
        return <div className="terminal-chart-empty">`{symbol}` için mum verisi bulunamadı.</div>;
    }

    return (
        <div ref={wrapRef} className="terminal-chart-wrap">
            <div className="terminal-chart-header">
                <div className="terminal-chart-title">Mum Grafik</div>
                <div className="terminal-chart-badges">
                    {timeframeLabel ? <span className="terminal-chart-badge">Zaman: {timeframeLabel}</span> : null}
                    {trendLabel ? (
                        <span className={`terminal-chart-badge ${trendLabel === 'UP' ? 'up' : 'down'}`}>
                            Trend: {trendLabel}
                        </span>
                    ) : null}
                </div>
                {hoverData ? (
                    <div className="terminal-ohlc">
                        <span>O {hoverData.open.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</span>
                        <span>H {hoverData.high.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</span>
                        <span>L {hoverData.low.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</span>
                        <span>C {hoverData.close.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</span>
                    </div>
                ) : (
                    <div className="terminal-ohlc muted">OHLC için imleci grafik üzerine getir</div>
                )}
            </div>
            {hoverNews ? (
                <div className="terminal-news-tooltip">
                    <div style={{ fontWeight: 700 }}>
                        {hoverNews.tone === 'POSITIVE' ? 'Olumlu Haber' : hoverNews.tone === 'NEGATIVE' ? 'Negatif Haber' : 'Nötr Haber'}
                    </div>
                    <div>{hoverNews.items[0]?.title ?? '—'}</div>
                    <div>
                        {new Date(hoverNews.time).toLocaleString('tr-TR')} · {hoverNews.items[0]?.source ?? 'Kaynak yok'}
                    </div>
                    <div>Etki: {impactLabel(hoverNews.impact)}</div>
                </div>
            ) : null}
            <div ref={chartRef} style={{ width: '100%', height: 430 }} />
            {showRsi ? (
                <div className="terminal-rsi">
                <div className="terminal-rsi-head">RSI (14)</div>
                    {rsi14.length ? (
                        <div className="terminal-rsi-row">
                            {rsi14.slice(-48).map((p) => (
                                <div
                                    key={`${p.time}-${p.value}`}
                                    className="terminal-rsi-bar"
                                    style={{
                                        height: `${Math.max(2, Math.min(100, p.value))}%`,
                                        background:
                                            p.value > 70 ? 'rgba(239,68,68,.75)' : p.value < 30 ? 'rgba(34,197,94,.75)' : 'rgba(56,189,248,.75)',
                                    }}
                                    title={`${new Date(p.time).toLocaleString('tr-TR')} · RSI ${p.value.toFixed(2)}`}
                                />
                            ))}
                        </div>
                    ) : (
                        <div className="terminal-chart-empty">RSI verisi yok</div>
                    )}
                </div>
            ) : null}
        </div>
    );
}

