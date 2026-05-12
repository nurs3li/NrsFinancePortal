import { memo, useEffect, useMemo, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import type { Time } from 'lightweight-charts';
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
    candles: CandlePoint[];
    ma7: { time: string; value: number }[];
    ma21: { time: string; value: number }[];
    showMa: boolean;
    loading: boolean;
    trendLabel?: 'UP' | 'DOWN';
    timeframeLabel?: string;
    tokens: {
        bgCard: string;
        border: string;
        text: string;
        textMuted: string;
    };
};

function toChartTime(value: string): Time {
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return value.slice(0, 10) as Time;
    const hasClock = /T\d{2}:\d{2}:\d{2}/.test(value);
    if (hasClock) return Math.floor(d.getTime() / 1000) as Time;
    return d.toISOString().slice(0, 10) as Time;
}

function chartTimeKey(value: Time): string {
    if (typeof value === 'number') return new Date(value * 1000).toISOString();
    return String(value);
}

function SpotTerminalChartImpl({ title, candles, ma7, ma21, showMa, loading, trendLabel, timeframeLabel, tokens }: Props) {
    const chartRef = useRef<HTMLDivElement>(null);
    const [hover, setHover] = useState<{ close: number } | null>(null);
    const chartHeight = 520;
    const tokensRef = useRef(tokens);
    tokensRef.current = tokens;
    const crosshairRafRef = useRef<number | null>(null);
    const pendingHoverRef = useRef<{ key: string; close: number } | null>(null);
    const lastCommittedHoverKeyRef = useRef<string>('');
    useEffect(
        () => () => {
            if (crosshairRafRef.current != null) {
                cancelAnimationFrame(crosshairRafRef.current);
                crosshairRafRef.current = null;
            }
        },
        [],
    );

    const sorted = useMemo(() => {
        const byTime = new Map<string, CandlePoint>();
        [...candles]
            .filter((c) => Number.isFinite(c.close) && c.close > 0)
            .sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime())
            .forEach((c) => byTime.set(chartTimeKey(toChartTime(c.time)), c));
        return [...byTime.values()];
    }, [candles]);

    useEffect(() => {
        const el = chartRef.current;
        if (!el || loading || sorted.length < 2) return;

        const chartRange = parseTerminalChartRange(timeframeLabel);
        const widthPx = Math.max(320, el.clientWidth);
        const tsLay = computeTerminalTimeScaleLayout(widthPx, sorted.length, chartRange);

        const t = tokensRef.current;
        const chart = createChart(el, {
            width: widthPx,
            height: chartHeight,
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
                ...tsLay,
            },
            crosshair: { mode: 1 },
        });

        const closeArea = chart.addAreaSeries({
            lineColor: '#38bdf8',
            topColor: 'rgba(56,189,248,0.45)',
            bottomColor: 'rgba(56,189,248,0.12)',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: true,
        });
        const closeLine = chart.addLineSeries({
            color: '#0ea5e9',
            lineWidth: 2,
            pointMarkersVisible: true,
            crosshairMarkerVisible: true,
            crosshairMarkerRadius: 4,
            priceLineVisible: false,
            lastValueVisible: false,
        });
        const closeData = sorted.map((p) => ({ time: toChartTime(p.time), value: p.close }));
        closeArea.setData(closeData);
        closeLine.setData(closeData);

        if (showMa) {
            const ma7Series = chart.addLineSeries({
                color: '#22c55e',
                lineWidth: 2,
                priceLineVisible: false,
                lastValueVisible: false,
            });
            ma7Series.setData(ma7.map((p) => ({ time: toChartTime(p.time), value: p.value })));
            const ma21Series = chart.addLineSeries({
                color: '#f59e0b',
                lineWidth: 2,
                priceLineVisible: false,
                lastValueVisible: false,
            });
            ma21Series.setData(ma21.map((p) => ({ time: toChartTime(p.time), value: p.value })));
        }

        chart.subscribeCrosshairMove((param) => {
            let next: { key: string; close: number } | null = null;
            if (param?.time) {
                const timeKey = chartTimeKey(param.time);
                const row = sorted.find((p) => chartTimeKey(toChartTime(p.time)) === timeKey);
                if (row) {
                    next = { key: `${timeKey}|${row.close}`, close: row.close };
                }
            }
            pendingHoverRef.current = next;
            if (crosshairRafRef.current != null) return;
            crosshairRafRef.current = requestAnimationFrame(() => {
                crosshairRafRef.current = null;
                const p = pendingHoverRef.current;
                const nextKey = p ? p.key : '';
                if (nextKey === lastCommittedHoverKeyRef.current) return;
                lastCommittedHoverKeyRef.current = nextKey;
                setHover(p ? { close: p.close } : null);
            });
        });

        const fit = () => requestAnimationFrame(() => chart.timeScale().fitContent());
        fit();

        const onResize = () => {
            const w = Math.max(320, el.clientWidth);
            const lay = computeTerminalTimeScaleLayout(w, sorted.length, chartRange);
            chart.applyOptions({
                width: w,
                timeScale: {
                    borderColor: tokensRef.current.border,
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
            chart.remove();
        };
    }, [loading, sorted, ma7, ma21, showMa, timeframeLabel, tokens.bgCard, tokens.border, tokens.text, tokens.textMuted]);

    if (loading) return <div className="terminal-chart-empty">Grafik yükleniyor...</div>;
    if (!sorted.length) return <div className="terminal-chart-empty">Analiz grafiği için veri bulunamadı.</div>;
    if (sorted.length < 2) return <div className="terminal-chart-empty">Analiz grafiği için en az 2 veri noktası gerekli.</div>;

    return (
        <div className="terminal-chart-wrap">
            <div className="terminal-chart-header">
                <div className="terminal-chart-title">{title}</div>
                <div className="terminal-chart-badges">
                    {timeframeLabel ? <span className="terminal-chart-badge">Zaman: {timeframeLabel}</span> : null}
                    {trendLabel ? (
                        <span className={`terminal-chart-badge ${trendLabel === 'UP' ? 'up' : 'down'}`}>Trend: {trendLabel}</span>
                    ) : null}
                </div>
                {hover ? (
                    <div className="terminal-ohlc">
                        <span>Fiyat {hover.close.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</span>
                    </div>
                ) : (
                    <div className="terminal-ohlc muted">Fiyat için imleci grafik üzerine getir</div>
                )}
            </div>
            <div ref={chartRef} style={{ width: '100%', height: chartHeight }} />
        </div>
    );
}

/*
 * React.memo: Parent (Market.tsx) state guncellemelerinde "Piyasa Analiz" alanindaki chart
 * istemsiz re-render'lardan korunur. Prop'lar parent'ta useMemo'lu oldugundan referans-stabil
 * ve memo "esit referans" karsilastirmasi titremeyi engeller.
 */
export const SpotTerminalChart = memo(SpotTerminalChartImpl);

