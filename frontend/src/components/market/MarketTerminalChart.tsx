import { useEffect, useMemo, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import type { ISeriesApi, Time, CandlestickData } from 'lightweight-charts';
import { computeTerminalTimeScaleLayout, parseTerminalChartRange } from './terminalChartScale';

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
    size?: 1 | 2 | 3;
    text: string;
};

type ThemeSlice = {
    bg: string;
    bgCard: string;
    border: string;
    text: string;
    textMuted: string;
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
};

function toChartTime(value: string): Time {
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return value.slice(0, 10) as Time;
    const hasClock = /T\d{2}:\d{2}:\d{2}/.test(value);
    if (hasClock) {
        return Math.floor(d.getTime() / 1000) as Time;
    }
    return d.toISOString().slice(0, 10) as Time;
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
}: Props) {
    const wrapRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<HTMLDivElement>(null);
    const [hoverData, setHoverData] = useState<CandleVM | null>(null);
    const sortedCandles = useMemo(() => {
        const byTime = new Map<string, CandleVM>();
        [...candles]
            .filter(
                (c) =>
                    Number.isFinite(c.open) &&
                    Number.isFinite(c.high) &&
                    Number.isFinite(c.low) &&
                    Number.isFinite(c.close) &&
                    c.close > 0
            )
            .sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime())
            .forEach((c) => byTime.set(String(toChartTime(c.time)), c));
        return [...byTime.values()];
    }, [candles]);
    const chartHeight = 520;

    useEffect(() => {
        const el = chartRef.current;
        if (!el || loading || !sortedCandles.length) return;

        const chartRange = parseTerminalChartRange(timeframeLabel);
        const widthPx = Math.max(320, el.clientWidth);
        const tsLay = computeTerminalTimeScaleLayout(widthPx, sortedCandles.length, chartRange);

        const chart = createChart(el, {
            width: widthPx,
            height: chartHeight,
            layout: {
                background: { color: tokens.bgCard },
                textColor: tokens.text,
            },
            grid: {
                vertLines: { color: 'rgba(71, 85, 105, 0.3)' },
                horzLines: { color: 'rgba(71, 85, 105, 0.3)' },
            },
            rightPriceScale: { borderColor: tokens.border, scaleMargins: { top: 0.1, bottom: 0.1 } },
            timeScale: {
                borderColor: tokens.border,
                timeVisible: true,
                secondsVisible: false,
                ...tsLay,
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
            time: toChartTime(c.time),
            open: c.open,
            high: c.high,
            low: c.low,
            close: c.close,
        }));
        candleSeries.setData(candleData);
        candleSeries.setMarkers(
            markers.map((m) => ({
                ...m,
                time: toChartTime(m.time),
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
            ma7Series.setData(ma7.map((p) => ({ time: toChartTime(p.time), value: p.value })));

            ma21Series = chart.addLineSeries({
                color: '#f59e0b',
                lineWidth: 2,
                priceLineVisible: false,
                lastValueVisible: false,
            });
            ma21Series.setData(ma21.map((p) => ({ time: toChartTime(p.time), value: p.value })));
        }

        chart.subscribeCrosshairMove((param) => {
            if (!param?.time) {
                setHoverData(null);
                return;
            }
            const ohlc = param.seriesData.get(candleSeries) as
                | { open: number; high: number; low: number; close: number }
                | undefined;
            if (!ohlc) {
                setHoverData(null);
                return;
            }
            setHoverData({
                time: String(param.time),
                open: Number(ohlc.open),
                high: Number(ohlc.high),
                low: Number(ohlc.low),
                close: Number(ohlc.close),
            });
        });

        const fit = () => requestAnimationFrame(() => chart.timeScale().fitContent());
        fit();

        const onResize = () => {
            const w = Math.max(320, el.clientWidth);
            const lay = computeTerminalTimeScaleLayout(w, sortedCandles.length, chartRange);
            chart.applyOptions({
                width: w,
                timeScale: {
                    borderColor: tokens.border,
                    timeVisible: true,
                    secondsVisible: false,
                    ...lay,
                },
            });
            fit();
        };
        window.addEventListener('resize', onResize);

        return () => {
            window.removeEventListener('resize', onResize);
            ma7Series = null;
            ma21Series = null;
            chart.remove();
        };
    }, [loading, sortedCandles, markers, showMa, ma7, ma21, tokens, timeframeLabel]);

    if (loading) {
        return <div className="terminal-chart-empty">Grafik yükleniyor...</div>;
    }
    if (!sortedCandles.length) {
        return <div className="terminal-chart-empty">`{symbol}` için mum verisi bulunamadı.</div>;
    }
    if (sortedCandles.length < 2) {
        return <div className="terminal-chart-empty">`{symbol}` için mum grafik için en az 2 veri noktası gerekli.</div>;
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
            <div ref={chartRef} style={{ width: '100%', height: chartHeight }} />
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

