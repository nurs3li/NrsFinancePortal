import { memo, useEffect, useMemo, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import type { CandlestickData, LogicalRange } from 'lightweight-charts';
import { apiDatetimeToChartTime } from '../../lib/chartApiTime';
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
    /** 1M/1Y günlük seri: İstanbul gece yarısı mumlarını `YYYY-MM-DD` iş günü zamanına çevirir. */
    chartTimePreferIstanbulBusinessDay?: boolean;
};

/** Doji / sentetik (O≈C) mumlarda kütüphane varsayılanı hep yeşil; önceki kapanşa göre kırmızı/yeşil/nötr. */
const CANDLE_PRICE_EPS = 1e-6;

function terminalCandlestickColor(c: CandleVM, prevClose: number | null): string {
    const o = c.open;
    const cl = c.close;
    if (cl < o - CANDLE_PRICE_EPS) return '#ef4444';
    if (cl > o + CANDLE_PRICE_EPS) return '#22c55e';
    if (prevClose != null && Number.isFinite(prevClose)) {
        if (cl < prevClose - CANDLE_PRICE_EPS) return '#ef4444';
        if (cl > prevClose + CANDLE_PRICE_EPS) return '#22c55e';
    }
    return '#64748b';
}
/*
 * React.memo: Parent (Market.tsx) state'i (orn. trendPeriod selector) degisince sayfa yeniden
 * render olur. Chart prop'lari (candles/ma/markers/tokens) parent'ta useMemo ile stabilize
 * edildiginden, memo ile sarinca chart hic dokunulmadan birakilir; bu da trend periyodunu
 * her tikladigimizda yasanan "ortadaki grafik titriyor" davranisinin onune gecer.
 */
function MarketTerminalChartImpl({
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
    chartTimePreferIstanbulBusinessDay,
}: Props) {
    const chartRef = useRef<HTMLDivElement>(null);
    const chartApiRef = useRef<ReturnType<typeof createChart> | null>(null);
    const candleSeriesRef = useRef<ReturnType<ReturnType<typeof createChart>['addCandlestickSeries']> | null>(null);
    const ma7SeriesRef = useRef<ReturnType<ReturnType<typeof createChart>['addLineSeries']> | null>(null);
    const ma21SeriesRef = useRef<ReturnType<ReturnType<typeof createChart>['addLineSeries']> | null>(null);
    const logicalRangeRef = useRef<LogicalRange | null>(null);
    const hasInitialFitRef = useRef(false);
    const candleByTimeRef = useRef<Record<string, CandleVM>>({});
    const barCountRef = useRef(0);
    const rangeRef = useRef(parseTerminalChartRange(timeframeLabel));
    // rAF-throttle'lı crosshair handler cleanup (bkz. subscribeCrosshairMove yorumu).
    const crosshairRafRef = useRef<(() => void) | null>(null);
    // Parent her renderda yeni `tokens` literal'i veriyor; useEffect deps'e koyarsak chart sonsuz
    // destroy+create dongusune giriyor (titreme + ana thread kilitlenmesi + sayfa gecisi engellenme).
    // Ref ile en guncel tokens'i tutuyoruz; mount yalnizca bir kez.
    const tokensRef = useRef(tokens);
    useEffect(() => {
        tokensRef.current = tokens;
    });
    const [hoverData, setHoverData] = useState<CandleVM | null>(null);
    const toChartTime = useMemo(
        () => (s: string) =>
            apiDatetimeToChartTime(s, {
                preferIstanbulBusinessDay: Boolean(chartTimePreferIstanbulBusinessDay),
            }),
        [chartTimePreferIstanbulBusinessDay],
    );
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
    }, [candles, toChartTime]);
    const chartHeight = 520;

    useEffect(() => {
        const el = chartRef.current;
        if (!el || chartApiRef.current) return;
        const widthPx = Math.max(320, el.clientWidth);
        const t = tokensRef.current;
        const chart = createChart(el, {
            width: widthPx,
            height: chartHeight,
            layout: {
                background: { color: t.bgCard },
                textColor: t.text,
            },
            grid: {
                vertLines: { color: 'rgba(71, 85, 105, 0.3)' },
                horzLines: { color: 'rgba(71, 85, 105, 0.3)' },
            },
            rightPriceScale: { borderColor: t.border, scaleMargins: { top: 0.1, bottom: 0.1 } },
            timeScale: {
                borderColor: t.border,
                timeVisible: true,
                secondsVisible: false,
                lockVisibleTimeRangeOnResize: true,
                rightOffset: 0,
                fixLeftEdge: true,
                fixRightEdge: true,
                shiftVisibleRangeOnNewBar: false,
            },
            crosshair: { mode: 1 },
        });
        chartApiRef.current = chart;

        const candleSeries = chart.addCandlestickSeries({
            upColor: '#22c55e',
            downColor: '#ef4444',
            wickUpColor: '#22c55e',
            wickDownColor: '#ef4444',
            borderVisible: false,
            priceLineVisible: false,
        });
        candleSeriesRef.current = candleSeries;

        const ma7Series = chart.addLineSeries({
            color: '#38bdf8',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
        });
        ma7SeriesRef.current = ma7Series;

        const ma21Series = chart.addLineSeries({
            color: '#f59e0b',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
        });
        ma21SeriesRef.current = ma21Series;

        // Lightweight-charts crosshair handler saniyede 60+ kez tetikleniyor. Idempotent setState'e
        // ragmen React scheduler her cagrida dispatch isi yapip ana thread'i mesgul ediyor, navigasyon
        // click'leri kuyrukta beklemis kaliyor (VİOP'tan baska sayfaya gecis yapilamiyor bug'i).
        // rAF ile frame basina 1 kez calistir.
        let crosshairRaf = 0;
        let pendingParam: Parameters<Parameters<typeof chart.subscribeCrosshairMove>[0]>[0] | null = null;
        chart.subscribeCrosshairMove((param) => {
            pendingParam = param;
            if (crosshairRaf) return;
            crosshairRaf = window.requestAnimationFrame(() => {
                crosshairRaf = 0;
                const p = pendingParam;
                pendingParam = null;
                if (!p?.time) {
                    setHoverData((prev) => (prev == null ? prev : null));
                    return;
                }
                const key = String(p.time);
                const row = candleByTimeRef.current[key] ?? null;
                setHoverData((prev) => {
                    if (prev?.time === row?.time && prev?.close === row?.close) return prev;
                    return row;
                });
            });
        });
        crosshairRafRef.current = () => {
            if (crosshairRaf) window.cancelAnimationFrame(crosshairRaf);
            crosshairRaf = 0;
            pendingParam = null;
        };
        chart.timeScale().subscribeVisibleLogicalRangeChange((range) => {
            if (range) logicalRangeRef.current = range;
        });

        const onResize = () => {
            const w = Math.max(320, el.clientWidth);
            const lay = computeTerminalTimeScaleLayout(w, Math.max(2, barCountRef.current), rangeRef.current);
            chart.applyOptions({
                width: w,
                timeScale: {
                    borderColor: tokensRef.current.border,
                    timeVisible: true,
                    secondsVisible: false,
                    lockVisibleTimeRangeOnResize: true,
                    ...lay,
                    rightOffset: 0,
                    fixLeftEdge: true,
                    fixRightEdge: true,
                    shiftVisibleRangeOnNewBar: false,
                },
            });
            if (logicalRangeRef.current) {
                chart.timeScale().setVisibleLogicalRange(logicalRangeRef.current);
            }
        };
        window.addEventListener('resize', onResize);
        return () => {
            window.removeEventListener('resize', onResize);
            crosshairRafRef.current?.();
            chart.remove();
            chartApiRef.current = null;
            candleSeriesRef.current = null;
            ma7SeriesRef.current = null;
            ma21SeriesRef.current = null;
            hasInitialFitRef.current = false;
        };
        // Mount tek seferli; tema/timeframe degisikligi ayri effect'lerde yansir.
    }, []);

    /** Tema degisince chart'i destroy etmek yerine sadece renkleri uygula. */
    useEffect(() => {
        const chart = chartApiRef.current;
        if (!chart) return;
        chart.applyOptions({
            layout: { background: { color: tokens.bgCard }, textColor: tokens.text },
            rightPriceScale: { borderColor: tokens.border },
            timeScale: { borderColor: tokens.border },
        });
    }, [tokens.bgCard, tokens.border, tokens.text, tokens.textMuted]);

    useEffect(() => {
        const chart = chartApiRef.current;
        const candleSeries = candleSeriesRef.current;
        if (!chart || !candleSeries || loading || !sortedCandles.length) return;

        const chartRange = parseTerminalChartRange(timeframeLabel);
        const widthPx = Math.max(320, chartRef.current?.clientWidth ?? 320);
        const tsLay = computeTerminalTimeScaleLayout(widthPx, sortedCandles.length, chartRange);
        // Timeframe degisiminde (orn. 1M -> 1Y) tum yeni veriyi sigdirmak icin
        // fitContent zorlanmali; aksi halde onceki pencereye kalitsal zoom kaliyor
        // ve kullanici 1Y'i 1M zoom seviyesinde gormeye devam ediyordu.
        const rangeChanged = rangeRef.current !== chartRange;
        rangeRef.current = chartRange;
        barCountRef.current = sortedCandles.length;

        const candleData: CandlestickData[] = sortedCandles.map((c, i) => {
            const prevClose = i > 0 ? sortedCandles[i - 1]!.close : null;
            const color = terminalCandlestickColor(c, prevClose);
            return {
                time: toChartTime(c.time),
                open: c.open,
                high: c.high,
                low: c.low,
                close: c.close,
                color,
            } satisfies CandlestickData;
        });
        const byTime: Record<string, CandleVM> = {};
        sortedCandles.forEach((c) => {
            byTime[String(toChartTime(c.time))] = c;
        });
        candleByTimeRef.current = byTime;

        candleSeries.setData(candleData);
        candleSeries.setMarkers(
            markers.map((m) => ({
                ...m,
                time: toChartTime(m.time),
            }))
        );
        const ma7Series = ma7SeriesRef.current;
        const ma21Series = ma21SeriesRef.current;
        if (ma7Series) {
            ma7Series.applyOptions({ visible: showMa });
            ma7Series.setData(showMa ? ma7.map((p) => ({ time: toChartTime(p.time), value: p.value })) : []);
        }
        if (ma21Series) {
            ma21Series.applyOptions({ visible: showMa });
            ma21Series.setData(showMa ? ma21.map((p) => ({ time: toChartTime(p.time), value: p.value })) : []);
        }

        chart.applyOptions({
            // display:none -> block gecisinde container genisligi degisiyor; tekrar uygula.
            width: widthPx,
            timeScale: {
                borderColor: tokensRef.current.border,
                timeVisible: true,
                secondsVisible: false,
                lockVisibleTimeRangeOnResize: true,
                ...tsLay,
                rightOffset: 0,
                fixLeftEdge: true,
                fixRightEdge: true,
                shiftVisibleRangeOnNewBar: false,
            },
        });

        if (!hasInitialFitRef.current || rangeChanged) {
            requestAnimationFrame(() => {
                chart.timeScale().fitContent();
                logicalRangeRef.current = chart.timeScale().getVisibleLogicalRange();
                hasInitialFitRef.current = true;
            });
            return;
        }
        if (logicalRangeRef.current) {
            chart.timeScale().setVisibleLogicalRange(logicalRangeRef.current);
        }
    }, [loading, sortedCandles, markers, showMa, ma7, ma21, timeframeLabel, toChartTime]);

    // Erken-return YOK: chart container hep DOM'da kalsin, aksi halde ilk renderda mount useEffect
    // chartRef.current = null gorur ve [] deps oldugu icin bir daha tetiklenmez, veri sonra gelse
    // de chart hic kurulmaz (eski regresyon). Bos/eksik durumlarda chart `display:none` ile gizlenir.
    const showChart = !loading && sortedCandles.length >= 2;
    const emptyMessage = loading
        ? 'Grafik yükleniyor...'
        : !sortedCandles.length
            ? `${symbol} için mum verisi bulunamadı.`
            : sortedCandles.length < 2
                ? `${symbol} için mum grafik için en az 2 veri noktası gerekli.`
                : null;

    return (
        <div className="terminal-chart-wrap">
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
            {emptyMessage ? <div className="terminal-chart-empty">{emptyMessage}</div> : null}
            <div
                ref={chartRef}
                style={{ width: '100%', height: chartHeight, display: showChart ? 'block' : 'none' }}
            />
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

export const MarketTerminalChart = memo(MarketTerminalChartImpl);

