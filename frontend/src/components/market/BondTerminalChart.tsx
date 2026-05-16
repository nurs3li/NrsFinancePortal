import { memo, useEffect, useMemo, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import type { LogicalRange, Time } from 'lightweight-charts';
import { apiDatetimeToChartTime as toChartTime } from '../../lib/chartApiTime';
import { computeTerminalTimeScaleLayout, parseTerminalChartRange } from './terminalChartScale';

type BondPoint = {
    time: string;
    price: number;
    yieldPct: number;
    volume?: number;
};

type ThemeSlice = {
    bgCard: string;
    border: string;
    text: string;
    textMuted: string;
};

type Props = {
    points: BondPoint[];
    ma7: { time: string; value: number }[];
    ma21: { time: string; value: number }[];
    showMa: boolean;
    loading: boolean;
    timeframeLabel?: string;
    trendLabel?: 'UP' | 'DOWN';
    tokens: ThemeSlice;
    /** false: yalnızca piyasa fiyatı (TRY); sağ eksende yield / getiri serisi gösterilmez. */
    showYieldSeries?: boolean;
};

function chartTimeKey(value: Time): string {
    if (typeof value === 'number') {
        return new Date(value * 1000).toISOString();
    }
    return String(value);
}

function BondTerminalChartImpl({
    points,
    ma7,
    ma21,
    showMa,
    loading,
    timeframeLabel,
    trendLabel,
    tokens,
    showYieldSeries = true,
}: Props) {
    const chartRef = useRef<HTMLDivElement>(null);
    const chartApiRef = useRef<ReturnType<typeof createChart> | null>(null);
    const priceAreaRef = useRef<ReturnType<ReturnType<typeof createChart>['addAreaSeries']> | null>(null);
    const yieldLineRef = useRef<ReturnType<ReturnType<typeof createChart>['addLineSeries']> | null>(null);
    const ma7Ref = useRef<ReturnType<ReturnType<typeof createChart>['addLineSeries']> | null>(null);
    const ma21Ref = useRef<ReturnType<ReturnType<typeof createChart>['addLineSeries']> | null>(null);
    const volumeRef = useRef<ReturnType<ReturnType<typeof createChart>['addHistogramSeries']> | null>(null);
    const hasInitialFitRef = useRef(false);
    /** Seri uzunluğu / uç zamanları değişince yeniden fitContent (ilk 2 noktada fit alınıp tam hafta gelince zoom takılı kalmasın). */
    const lastFitSeriesKeyRef = useRef<string>('');
    const logicalRangeRef = useRef<LogicalRange | null>(null);
    const dataByKeyRef = useRef<Record<string, BondPoint>>({});
    const barCountRef = useRef(0);
    const rangeRef = useRef(parseTerminalChartRange(timeframeLabel));
    const crosshairRafRef = useRef<(() => void) | null>(null);
    const tokensRef = useRef(tokens);
    const showYieldRef = useRef(showYieldSeries);
    useEffect(() => {
        tokensRef.current = tokens;
    });
    useEffect(() => {
        showYieldRef.current = showYieldSeries;
    });
    const [hover, setHover] = useState<{ time: string; price: number; yieldPct: number | null } | null>(null);
    const chartHeight = 520;

    const sorted = useMemo(() => {
        const byTime = new Map<string, BondPoint>();
        [...points]
            .filter((p) => Number.isFinite(p.price) && p.price > 0)
            .sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime())
            .forEach((p) => byTime.set(chartTimeKey(toChartTime(p.time)), p));
        return [...byTime.values()];
    }, [points]);

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
                vertLines: { color: 'rgba(148, 163, 184, 0.25)' },
                horzLines: { color: 'rgba(148, 163, 184, 0.25)' },
            },
            leftPriceScale: { visible: true, borderColor: t.border, scaleMargins: { top: 0.1, bottom: 0.1 } },
            rightPriceScale: { visible: showYieldSeries, borderColor: t.border, scaleMargins: { top: 0.1, bottom: 0.1 } },
            timeScale: {
                borderColor: t.border,
                timeVisible: true,
                secondsVisible: false,
                ...computeTerminalTimeScaleLayout(widthPx, Math.max(2, barCountRef.current), rangeRef.current),
                shiftVisibleRangeOnNewBar: false,
            },
            crosshair: { mode: 1 },
        });
        chartApiRef.current = chart;

        const priceArea = chart.addAreaSeries({
            priceScaleId: 'left',
            lineColor: '#0f3d91',
            topColor: 'rgba(15,61,145,0.45)',
            bottomColor: 'rgba(15,61,145,0.06)',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: true,
        });
        priceAreaRef.current = priceArea;

        const yieldLine = chart.addLineSeries({
            priceScaleId: 'right',
            color: '#cbd5e1',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: true,
        });
        yieldLineRef.current = yieldLine;
        yieldLine.applyOptions({ visible: showYieldSeries, lastValueVisible: showYieldSeries });

        const ma7Series = chart.addLineSeries({
            priceScaleId: 'left',
            color: '#38bdf8',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
        });
        ma7Ref.current = ma7Series;

        const ma21Series = chart.addLineSeries({
            priceScaleId: 'left',
            color: '#f59e0b',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
        });
        ma21Ref.current = ma21Series;

        const volumeSeries = chart.addHistogramSeries({
            color: 'rgba(148, 163, 184, 0.25)',
            priceFormat: { type: 'volume' },
            priceScaleId: '',
            lastValueVisible: false,
            priceLineVisible: false,
        });
        volumeSeries.priceScale().applyOptions({
            scaleMargins: { top: 0.92, bottom: 0 },
        });
        volumeRef.current = volumeSeries;

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
                    setHover((prev) => (prev == null ? prev : null));
                    return;
                }
                const key = chartTimeKey(p.time);
                const row = dataByKeyRef.current[key] ?? null;
                const next = row
                    ? {
                          time: String(row.time).slice(0, 10),
                          price: row.price,
                          yieldPct: showYieldRef.current ? row.yieldPct : null,
                      }
                    : null;
                setHover((prev) => {
                    if (!next) return prev == null ? prev : null;
                    if (
                        prev &&
                        prev.time === next.time &&
                        prev.price === next.price &&
                        prev.yieldPct === next.yieldPct
                    ) {
                        return prev;
                    }
                    return next;
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
                    ...lay,
                    shiftVisibleRangeOnNewBar: false,
                },
            });
            chart.timeScale().fitContent();
            logicalRangeRef.current = chart.timeScale().getVisibleLogicalRange();
        };
        window.addEventListener('resize', onResize);
        return () => {
            window.removeEventListener('resize', onResize);
            crosshairRafRef.current?.();
            chart.remove();
            chartApiRef.current = null;
            priceAreaRef.current = null;
            yieldLineRef.current = null;
            ma7Ref.current = null;
            ma21Ref.current = null;
            volumeRef.current = null;
            hasInitialFitRef.current = false;
            lastFitSeriesKeyRef.current = '';
        };
    }, []);

    useEffect(() => {
        const chart = chartApiRef.current;
        if (!chart) return;
        chart.applyOptions({
            layout: { background: { color: tokens.bgCard }, textColor: tokens.text },
            leftPriceScale: { borderColor: tokens.border },
            rightPriceScale: { borderColor: tokens.border },
            timeScale: { borderColor: tokens.border },
        });
    }, [tokens.bgCard, tokens.border, tokens.text, tokens.textMuted]);

    useEffect(() => {
        const chart = chartApiRef.current;
        if (!chart || loading || !sorted.length) return;

        const chartRange = parseTerminalChartRange(timeframeLabel);
        const widthPx = Math.max(320, chartRef.current?.clientWidth ?? 320);
        const tsLay = computeTerminalTimeScaleLayout(widthPx, sorted.length, chartRange);
        const rangeChanged = rangeRef.current !== chartRange;
        rangeRef.current = chartRange;
        barCountRef.current = sorted.length;

        const seriesFitKey =
            sorted.length > 0
                ? `${sorted.length}|${String(sorted[0]!.time)}|${String(sorted[sorted.length - 1]!.time)}`
                : '';
        const seriesChanged = lastFitSeriesKeyRef.current !== seriesFitKey;
        if (seriesChanged) {
            lastFitSeriesKeyRef.current = seriesFitKey;
        }

        const byKey: Record<string, BondPoint> = {};
        sorted.forEach((p) => {
            byKey[chartTimeKey(toChartTime(p.time))] = p;
        });
        dataByKeyRef.current = byKey;

        priceAreaRef.current?.setData(
            sorted
                .filter((p) => Number.isFinite(p.price) && p.price > 0)
                .map((p) => ({ time: toChartTime(p.time), value: p.price }))
        );
        if (showYieldSeries) {
            yieldLineRef.current?.setData(
                sorted
                    .filter((p) => Number.isFinite(p.yieldPct))
                    .map((p) => ({ time: toChartTime(p.time), value: p.yieldPct }))
            );
        } else {
            yieldLineRef.current?.setData([]);
        }
        try {
            chart.priceScale('right').applyOptions({ visible: showYieldSeries });
        } catch {
            /* lightweight-charts sürümü */
        }
        const ma7Series = ma7Ref.current;
        const ma21Series = ma21Ref.current;
        if (ma7Series) {
            ma7Series.applyOptions({ visible: showMa });
            ma7Series.setData(showMa ? ma7.map((p) => ({ time: toChartTime(p.time), value: p.value })) : []);
        }
        if (ma21Series) {
            ma21Series.applyOptions({ visible: showMa });
            ma21Series.setData(showMa ? ma21.map((p) => ({ time: toChartTime(p.time), value: p.value })) : []);
        }
        volumeRef.current?.setData(
            sorted.map((p) => ({
                time: toChartTime(p.time),
                value: Number(p.volume ?? 0),
                color: 'rgba(148,163,184,0.2)',
            }))
        );

        chart.applyOptions({
            width: widthPx,
            timeScale: {
                borderColor: tokensRef.current.border,
                timeVisible: true,
                secondsVisible: false,
                ...tsLay,
                shiftVisibleRangeOnNewBar: false,
            },
        });

        if (!hasInitialFitRef.current || rangeChanged || seriesChanged) {
            requestAnimationFrame(() => {
                chart.timeScale().fitContent();
                logicalRangeRef.current = chart.timeScale().getVisibleLogicalRange();
                hasInitialFitRef.current = true;
            });
        }
    }, [loading, sorted, ma7, ma21, showMa, timeframeLabel, showYieldSeries]);

    const showChart = !loading && sorted.length >= 2;
    const emptyMessage = loading
        ? 'Grafik yükleniyor...'
        : !sorted.length
            ? showYieldSeries
                ? 'Tahvil fiyat / yield serisi bulunamadı.'
                : 'Tahvil piyasa fiyatı verisi bulunamadı.'
            : sorted.length < 2
                ? 'Tahvil grafik için en az 2 veri noktası gerekli.'
                : null;

    const chartTitle = showYieldSeries ? 'Tahvil — Piyasa fiyatı ve yield (veri varsa)' : 'Tahvil — Piyasa fiyatı (TRY)';

    return (
        <div className="terminal-chart-wrap">
            <div className="terminal-chart-header">
                <div className="terminal-chart-title">{chartTitle}</div>
                <div className="terminal-chart-badges">
                    {timeframeLabel ? <span className="terminal-chart-badge">Zaman: {timeframeLabel}</span> : null}
                    {trendLabel ? (
                        <span className={`terminal-chart-badge ${trendLabel === 'UP' ? 'up' : 'down'}`}>
                            Trend: {trendLabel}
                        </span>
                    ) : null}
                </div>
                {hover ? (
                    <div className="terminal-ohlc">
                        <span>Piyasa fiyatı {hover.price.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</span>
                        {showYieldSeries && hover.yieldPct != null ? (
                            <span>Yield %{hover.yieldPct.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</span>
                        ) : null}
                        <span>{hover.time}</span>
                    </div>
                ) : (
                    <div className="terminal-ohlc muted">
                        {showYieldSeries
                            ? 'Piyasa fiyatı ve yield için imleci grafik üzerine getir'
                            : 'Piyasa fiyatı (fiyat performansı) için imleci grafik üzerine getir'}
                    </div>
                )}
            </div>
            {emptyMessage ? <div className="terminal-chart-empty">{emptyMessage}</div> : null}
            <div
                ref={chartRef}
                style={{
                    width: '100%',
                    maxWidth: '100%',
                    height: chartHeight,
                    overflow: 'hidden',
                    display: showChart ? 'block' : 'none',
                }}
            />
        </div>
    );
}

export const BondTerminalChart = memo(BondTerminalChartImpl);
