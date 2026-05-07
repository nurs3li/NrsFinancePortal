import { useEffect, useMemo, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import type { LogicalRange, Time } from 'lightweight-charts';
import { computeTerminalTimeScaleLayout, parseTerminalChartRange } from './terminalChartScale';

type ViopPoint = {
    time: string;
    price: number;
    basis: number;
    annualizedBasisPct: number;
    openInterest: number;
};

type ThemeSlice = {
    bgCard: string;
    border: string;
    text: string;
    textMuted: string;
};

type Props = {
    points: ViopPoint[];
    ma7: { time: string; value: number }[];
    ma21: { time: string; value: number }[];
    showMa: boolean;
    loading: boolean;
    timeframeLabel?: string;
    trendLabel?: 'UP' | 'DOWN';
    tokens: ThemeSlice;
};

function toChartTime(value: string): Time {
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return String(value).slice(0, 10) as Time;
    // VIOP ekranı günlük veri ile çalışıyor; tüm noktaları tek tipte (BusinessDay) tutup
    // karışık Time formatının (unix + date string) zaman ekseninde sağ-sol zıplama üretmesini engeller.
    return d.toISOString().slice(0, 10) as Time;
}

function chartTimeKey(value: Time): string {
    if (typeof value === 'number') {
        return new Date(value * 1000).toISOString();
    }
    return String(value);
}

export function ViopTerminalChart({ points, ma7, ma21, showMa, loading, timeframeLabel, trendLabel, tokens }: Props) {
    const chartRef = useRef<HTMLDivElement>(null);
    const chartApiRef = useRef<ReturnType<typeof createChart> | null>(null);
    const priceAreaRef = useRef<ReturnType<ReturnType<typeof createChart>['addAreaSeries']> | null>(null);
    const priceLineRef = useRef<ReturnType<ReturnType<typeof createChart>['addLineSeries']> | null>(null);
    const ma7Ref = useRef<ReturnType<ReturnType<typeof createChart>['addLineSeries']> | null>(null);
    const ma21Ref = useRef<ReturnType<ReturnType<typeof createChart>['addLineSeries']> | null>(null);
    const oiRef = useRef<ReturnType<ReturnType<typeof createChart>['addHistogramSeries']> | null>(null);
    const hasInitialFitRef = useRef(false);
    const logicalRangeRef = useRef<LogicalRange | null>(null);
    const dataByKeyRef = useRef<Record<string, ViopPoint>>({});
    const barCountRef = useRef(0);
    const rangeRef = useRef(parseTerminalChartRange(timeframeLabel));
    const [hover, setHover] = useState<ViopPoint | null>(null);
    const chartHeight = 520;
    const sorted = useMemo(() => {
        const byTime = new Map<string, ViopPoint>();
        [...points]
            .filter((p) => Number.isFinite(p.price) && p.price > 0)
            .sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime())
            .forEach((p) => byTime.set(chartTimeKey(toChartTime(p.time)), p));
        return [...byTime.values()];
    }, [points]);
    const limitedData = sorted.length > 0 && sorted.length < 5;

    useEffect(() => {
        const el = chartRef.current;
        if (!el || chartApiRef.current) return;
        const widthPx = Math.max(320, el.clientWidth);
        const chart = createChart(el, {
            width: widthPx,
            height: chartHeight,
            layout: {
                background: { color: tokens.bgCard },
                textColor: tokens.text,
            },
            grid: {
                vertLines: { color: 'rgba(71, 85, 105, 0.18)' },
                horzLines: { color: 'rgba(71, 85, 105, 0.18)' },
            },
            rightPriceScale: { borderColor: tokens.border, scaleMargins: { top: 0.1, bottom: 0.1 } },
            timeScale: {
                borderColor: tokens.border,
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

        const priceArea = chart.addAreaSeries({
            lineColor: '#38bdf8',
            topColor: 'rgba(56,189,248,0.45)',
            bottomColor: 'rgba(56,189,248,0.12)',
            lineWidth: 3,
            priceLineVisible: false,
            lastValueVisible: true,
        });
        priceAreaRef.current = priceArea;

        const priceLine = chart.addLineSeries({
            color: '#0ea5e9',
            lineWidth: 3,
            pointMarkersVisible: true,
            crosshairMarkerVisible: true,
            crosshairMarkerRadius: 4,
            priceLineVisible: false,
            lastValueVisible: false,
        });
        priceLineRef.current = priceLine;

        const ma7Series = chart.addLineSeries({
            color: '#22c55e',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
        });
        ma7Ref.current = ma7Series;

        const ma21Series = chart.addLineSeries({
            color: '#f59e0b',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
        });
        ma21Ref.current = ma21Series;

        const oiHistogram = chart.addHistogramSeries({
            color: 'rgba(148,163,184,0.22)',
            priceFormat: { type: 'volume' },
            priceScaleId: '',
            lastValueVisible: false,
            priceLineVisible: false,
        });
        oiRef.current = oiHistogram;
        oiHistogram.priceScale().applyOptions({
            scaleMargins: { top: 0.92, bottom: 0 },
        });

        chart.subscribeCrosshairMove((param) => {
            if (!param?.time) {
                setHover(null);
                return;
            }
            const key = chartTimeKey(param.time);
            const row = dataByKeyRef.current[key];
            setHover(row ?? null);
        });
        chart.timeScale().subscribeVisibleLogicalRangeChange((range) => {
            if (range) logicalRangeRef.current = range;
        });

        const onResize = () => {
            const w = Math.max(320, el.clientWidth);
            const lay = computeTerminalTimeScaleLayout(w, Math.max(2, barCountRef.current), rangeRef.current);
            chart.applyOptions({
                width: w,
                timeScale: {
                    borderColor: tokens.border,
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
            chart.remove();
            chartApiRef.current = null;
            priceAreaRef.current = null;
            priceLineRef.current = null;
            ma7Ref.current = null;
            ma21Ref.current = null;
            oiRef.current = null;
            hasInitialFitRef.current = false;
        };
    }, [tokens, timeframeLabel]);

    useEffect(() => {
        const chart = chartApiRef.current;
        if (!chart || loading || !sorted.length) return;
        const chartRange = parseTerminalChartRange(timeframeLabel);
        const widthPx = Math.max(320, chartRef.current?.clientWidth ?? 320);
        const tsLay = computeTerminalTimeScaleLayout(widthPx, sorted.length, chartRange);
        rangeRef.current = chartRange;
        barCountRef.current = sorted.length;

        const priceData = sorted
            .filter((p) => Number.isFinite(p.price) && p.price > 0)
            .map((p) => ({ time: toChartTime(p.time), value: p.price }));

        const byKey: Record<string, ViopPoint> = {};
        sorted.forEach((p) => {
            byKey[chartTimeKey(toChartTime(p.time))] = p;
        });
        dataByKeyRef.current = byKey;

        priceAreaRef.current?.setData(priceData);
        priceLineRef.current?.setData(priceData);
        ma7Ref.current?.setData(showMa ? ma7.map((p) => ({ time: toChartTime(p.time), value: p.value })) : []);
        ma21Ref.current?.setData(showMa ? ma21.map((p) => ({ time: toChartTime(p.time), value: p.value })) : []);
        oiRef.current?.setData(
            sorted.map((p) => ({
                time: toChartTime(p.time),
                value: Number(p.openInterest ?? 0),
                color: 'rgba(148,163,184,0.2)',
            }))
        );

        chart.applyOptions({
            timeScale: {
                borderColor: tokens.border,
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

        if (!hasInitialFitRef.current) {
            hasInitialFitRef.current = true;
            requestAnimationFrame(() => {
                chart.timeScale().fitContent();
                logicalRangeRef.current = chart.timeScale().getVisibleLogicalRange();
            });
            return;
        }
        if (logicalRangeRef.current) {
            chart.timeScale().setVisibleLogicalRange(logicalRangeRef.current);
        }
    }, [loading, sorted, ma7, ma21, showMa, tokens, timeframeLabel]);

    if (loading) {
        return <div className="terminal-chart-empty">Grafik yükleniyor...</div>;
    }
    if (!sorted.length) {
        return <div className="terminal-chart-empty">VİOP veri noktası bulunamadı.</div>;
    }
    if (sorted.length < 2) {
        return <div className="terminal-chart-empty">Bu kontrat için çizim yapacak yeterli VİOP geçmişi yok (en az 2 nokta gerekli).</div>;
    }

    return (
        <div className="terminal-chart-wrap">
            <div className="terminal-chart-header">
                <div className="terminal-chart-title">VİOP Analiz (Line/Area)</div>
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
                        <span>Fiyat {hover.price.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</span>
                        <span>Baz {hover.basis.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</span>
                        <span>Carry %{hover.annualizedBasisPct.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</span>
                    </div>
                ) : (
                    <div className="terminal-ohlc muted">Fiyat/baz/carry için imleci grafik üzerine getir</div>
                )}
            </div>
            {limitedData ? (
                <div style={{ color: tokens.textMuted, marginBottom: 8, fontSize: 12 }}>Bu periyotta sınırlı VİOP verisi.</div>
            ) : null}
            <div ref={chartRef} style={{ width: '100%', height: chartHeight }} />
        </div>
    );
}

