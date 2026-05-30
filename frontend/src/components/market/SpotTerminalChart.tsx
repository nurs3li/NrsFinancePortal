import { memo, useEffect, useMemo, useRef } from 'react';
import { createChart } from 'lightweight-charts';
import type { LogicalRange } from 'lightweight-charts';
import { apiDatetimeToChartTime } from '../../lib/chartApiTime';
import { chartTimeKey, normalizeChartLineSeries } from '../../lib/chartSeriesData';
import { createChartResizeScheduler } from './chartResize';
import { computeTerminalTimeScaleLayout, parseTerminalChartRange } from './terminalChartScale';

type CandlePoint = {
    time: string;
    open: number;
    high: number;
    low: number;
    close: number;
    volume?: number;
};

type Props = {
    title: string;
    subtitle?: string;
    candles: CandlePoint[];
    ma7: { time: string; value: number }[];
    ma21: { time: string; value: number }[];
    showMa: boolean;
    showRsi?: boolean;
    rsi14?: { time: string; value: number }[];
    loading: boolean;
    trendLabel?: 'UP' | 'DOWN';
    timeframeLabel?: string;
    chartTimePreferIstanbulBusinessDay?: boolean;
    tokens: {
        bgCard: string;
        border: string;
        text: string;
        textMuted: string;
    };
    onCrosshairDate?: (dateYmd: string) => void;
};

function SpotTerminalChartImpl({
    title,
    subtitle,
    candles,
    ma7,
    ma21,
    showMa,
    showRsi = false,
    rsi14 = [],
    loading,
    trendLabel: _trendLabel,
    timeframeLabel,
    chartTimePreferIstanbulBusinessDay,
    tokens,
    onCrosshairDate,
}: Props) {
    const chartRef = useRef<HTMLDivElement>(null);
    const chartApiRef = useRef<ReturnType<typeof createChart> | null>(null);
    const closeAreaRef = useRef<ReturnType<ReturnType<typeof createChart>['addAreaSeries']> | null>(null);
    const closeLineRef = useRef<ReturnType<ReturnType<typeof createChart>['addLineSeries']> | null>(null);
    const ma7SeriesRef = useRef<ReturnType<ReturnType<typeof createChart>['addLineSeries']> | null>(null);
    const ma21SeriesRef = useRef<ReturnType<ReturnType<typeof createChart>['addLineSeries']> | null>(null);
    const logicalRangeRef = useRef<LogicalRange | null>(null);
    const hasInitialFitRef = useRef(false);
    const rangeRef = useRef(parseTerminalChartRange(timeframeLabel));
    const barCountRef = useRef(0);
    const candleByTimeRef = useRef<Map<string, CandlePoint>>(new Map());

    const tokensRef = useRef(tokens);
    tokensRef.current = tokens;
    const onCrosshairDateRef = useRef(onCrosshairDate);
    onCrosshairDateRef.current = onCrosshairDate;
    const lastCrosshairDateRef = useRef('');

    const toChartTime = useMemo(
        () => (s: string) =>
            apiDatetimeToChartTime(s, {
                preferIstanbulBusinessDay: Boolean(chartTimePreferIstanbulBusinessDay),
            }),
        [chartTimePreferIstanbulBusinessDay],
    );

    const sortedCandles = useMemo(() => {
        const byTime = new Map<string, CandlePoint>();
        [...candles]
            .filter((c) => Number.isFinite(c.close) && c.close > 0)
            .sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime())
            .forEach((c) => byTime.set(chartTimeKey(toChartTime(c.time)), c));
        return [...byTime.values()];
    }, [candles, toChartTime]);

    useEffect(() => {
        const el = chartRef.current;
        if (!el || chartApiRef.current) return;

        const t = tokensRef.current;
        const widthPx = Math.max(320, el.clientWidth);
        const heightPx = Math.max(260, el.clientHeight);
        const chart = createChart(el, {
            width: widthPx,
            height: heightPx,
            layout: {
                background: { color: t.bgCard },
                textColor: t.text,
            },
            grid: {
                vertLines: { color: 'rgba(71, 85, 105, 0.18)' },
                horzLines: { color: 'rgba(71, 85, 105, 0.18)' },
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
            crosshair: {
                mode: 1,
                vertLine: { labelVisible: false },
                horzLine: { labelVisible: false },
            },
        });
        chartApiRef.current = chart;

        closeAreaRef.current = chart.addAreaSeries({
            lineColor: '#38bdf8',
            topColor: 'rgba(56,189,248,0.45)',
            bottomColor: 'rgba(56,189,248,0.12)',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: true,
        });
        closeLineRef.current = chart.addLineSeries({
            color: '#0ea5e9',
            lineWidth: 2,
            pointMarkersVisible: true,
            crosshairMarkerVisible: true,
            crosshairMarkerRadius: 4,
            priceLineVisible: false,
            lastValueVisible: false,
        });
        ma7SeriesRef.current = chart.addLineSeries({
            color: '#22c55e',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
        });
        ma21SeriesRef.current = chart.addLineSeries({
            color: '#f59e0b',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
        });

        chart.subscribeCrosshairMove((param) => {
            if (param?.time) {
                const timeKey = chartTimeKey(param.time);
                const row = candleByTimeRef.current.get(timeKey);
                if (row) {
                    const ymd = String(row.time).slice(0, 10);
                    if (ymd && ymd !== lastCrosshairDateRef.current) {
                        lastCrosshairDateRef.current = ymd;
                        onCrosshairDateRef.current?.(ymd);
                    }
                }
            } else if (lastCrosshairDateRef.current) {
                lastCrosshairDateRef.current = '';
                onCrosshairDateRef.current?.('');
            }
        });

        chart.timeScale().subscribeVisibleLogicalRangeChange((range) => {
            if (range) logicalRangeRef.current = range;
        });

        const onResize = () => {
            const w = Math.max(320, el.clientWidth);
            const h = Math.max(260, el.clientHeight);
            const lay = computeTerminalTimeScaleLayout(w, Math.max(2, barCountRef.current), rangeRef.current);
            chart.applyOptions({
                width: w,
                height: h,
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
        const resizeScheduler = createChartResizeScheduler(onResize);
        const unbindResize = resizeScheduler.bind(el);

        return () => {
            unbindResize();
            chart.remove();
            chartApiRef.current = null;
            closeAreaRef.current = null;
            closeLineRef.current = null;
            ma7SeriesRef.current = null;
            ma21SeriesRef.current = null;
            hasInitialFitRef.current = false;
        };
    }, []);

    useEffect(() => {
        const chart = chartApiRef.current;
        if (!chart) return;
        chart.applyOptions({
            layout: { background: { color: tokens.bgCard }, textColor: tokens.text },
            rightPriceScale: { borderColor: tokens.border },
            timeScale: { borderColor: tokens.border },
        });
    }, [tokens.bgCard, tokens.border, tokens.text]);

    useEffect(() => {
        const chart = chartApiRef.current;
        const closeArea = closeAreaRef.current;
        const closeLine = closeLineRef.current;
        if (!chart || !closeArea || !closeLine || loading || sortedCandles.length < 2) return;

        const chartRange = parseTerminalChartRange(timeframeLabel);
        const rangeChanged = rangeRef.current !== chartRange;
        rangeRef.current = chartRange;
        barCountRef.current = sortedCandles.length;

        const byTime = new Map<string, CandlePoint>();
        const closeData = sortedCandles.map((p) => {
            const t = toChartTime(p.time);
            const key = chartTimeKey(t);
            byTime.set(key, p);
            return { time: t, value: p.close };
        });
        candleByTimeRef.current = byTime;

        closeArea.setData(closeData);
        closeLine.setData(closeData);
        const ma7Series = ma7SeriesRef.current;
        const ma21Series = ma21SeriesRef.current;
        if (ma7Series) {
            ma7Series.applyOptions({ visible: showMa });
            ma7Series.setData(showMa ? normalizeChartLineSeries(ma7, toChartTime) : []);
        }
        if (ma21Series) {
            ma21Series.applyOptions({ visible: showMa });
            ma21Series.setData(showMa ? normalizeChartLineSeries(ma21, toChartTime) : []);
        }

        const widthPx = Math.max(320, chartRef.current?.clientWidth ?? 320);
        const tsLay = computeTerminalTimeScaleLayout(widthPx, sortedCandles.length, chartRange);
        chart.applyOptions({
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
    }, [loading, sortedCandles, ma7, ma21, showMa, timeframeLabel, toChartTime]);

    const showChart = !loading && sortedCandles.length >= 2;
    const emptyMessage = loading
        ? 'Grafik yükleniyor...'
        : !sortedCandles.length
          ? 'Analiz grafiği için veri bulunamadı.'
          : sortedCandles.length < 2
            ? 'Analiz grafiği için en az 2 veri noktası gerekli.'
            : null;

    return (
        <div className="terminal-chart-wrap">
            <div className="terminal-chart-header terminal-chart-header--compact">
                <div className="terminal-chart-title">{title}</div>
                {subtitle ? <div className="terminal-chart-subtitle">{subtitle}</div> : null}
            </div>
            {emptyMessage ? <div className="terminal-chart-empty">{emptyMessage}</div> : null}
            <div
                ref={chartRef}
                className="terminal-chart-surface"
                style={{ display: showChart ? 'block' : 'none' }}
            />
            {showRsi && showChart ? (
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
                                            p.value > 70
                                                ? 'rgba(239,68,68,.75)'
                                                : p.value < 30
                                                  ? 'rgba(34,197,94,.75)'
                                                  : 'rgba(56,189,248,.75)',
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

export const SpotTerminalChart = memo(SpotTerminalChartImpl);
