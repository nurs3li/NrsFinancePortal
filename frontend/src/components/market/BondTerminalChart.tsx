import { useEffect, useMemo, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import type { Time } from 'lightweight-charts';

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

function chartTimeKey(value: Time): string {
    if (typeof value === 'number') {
        return new Date(value * 1000).toISOString();
    }
    return String(value);
}

export function BondTerminalChart({ points, ma7, ma21, showMa, loading, timeframeLabel, trendLabel, tokens }: Props) {
    const chartRef = useRef<HTMLDivElement>(null);
    const [hover, setHover] = useState<{ time: string; price: number; yieldPct: number } | null>(null);
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
        if (!el || loading || !sorted.length) return;

        const chart = createChart(el, {
            width: el.clientWidth,
            height: 430,
            layout: {
                background: { color: tokens.bgCard },
                textColor: tokens.text,
            },
            grid: {
                vertLines: { color: 'rgba(71, 85, 105, 0.25)' },
                horzLines: { color: 'rgba(71, 85, 105, 0.25)' },
            },
            leftPriceScale: { visible: true, borderColor: tokens.border },
            rightPriceScale: { visible: true, borderColor: tokens.border },
            timeScale: { borderColor: tokens.border, timeVisible: true, secondsVisible: false, barSpacing: 7, minBarSpacing: 5 },
            crosshair: { mode: 1 },
        });

        const priceArea = chart.addAreaSeries({
            priceScaleId: 'left',
            lineColor: '#0f3d91',
            topColor: 'rgba(15,61,145,0.45)',
            bottomColor: 'rgba(15,61,145,0.06)',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: true,
        });
        priceArea.setData(
            sorted
                .filter((p) => Number.isFinite(p.price) && p.price > 0)
                .map((p) => ({ time: toChartTime(p.time), value: p.price }))
        );

        const yieldLine = chart.addLineSeries({
            priceScaleId: 'right',
            color: '#cbd5e1',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: true,
        });
        yieldLine.setData(
            sorted
                .filter((p) => Number.isFinite(p.yieldPct))
                .map((p) => ({ time: toChartTime(p.time), value: p.yieldPct }))
        );

        if (showMa) {
            const ma7Series = chart.addLineSeries({
                priceScaleId: 'left',
                color: '#38bdf8',
                lineWidth: 1,
                priceLineVisible: false,
                lastValueVisible: false,
            });
            ma7Series.setData(ma7.map((p) => ({ time: toChartTime(p.time), value: p.value })));

            const ma21Series = chart.addLineSeries({
                priceScaleId: 'left',
                color: '#f59e0b',
                lineWidth: 1,
                priceLineVisible: false,
                lastValueVisible: false,
            });
            ma21Series.setData(ma21.map((p) => ({ time: toChartTime(p.time), value: p.value })));
        }

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
        volumeSeries.setData(
            sorted.map((p) => ({
                time: toChartTime(p.time),
                value: Number(p.volume ?? 0),
                color: 'rgba(148,163,184,0.2)',
            }))
        );

        chart.subscribeCrosshairMove((param) => {
            if (!param?.time) {
                setHover(null);
                return;
            }
            const key = chartTimeKey(param.time);
            const row = sorted.find((p) => chartTimeKey(toChartTime(p.time)) === key);
            if (!row) {
                setHover(null);
                return;
            }
            setHover({
                time: key.slice(0, 10),
                price: row.price,
                yieldPct: row.yieldPct,
            });
        });

        chart.timeScale().fitContent();
        const onResize = () => chart.applyOptions({ width: el.clientWidth });
        window.addEventListener('resize', onResize);
        return () => {
            window.removeEventListener('resize', onResize);
            chart.remove();
        };
    }, [loading, sorted, ma7, ma21, showMa, tokens]);

    if (loading) {
        return <div className="terminal-chart-empty">Grafik yükleniyor...</div>;
    }
    if (!sorted.length) {
        return <div className="terminal-chart-empty">Tahvil fiyat/getiri verisi bulunamadı.</div>;
    }
    if (sorted.length < 2) {
        return <div className="terminal-chart-empty">Tahvil grafik için en az 2 veri noktası gerekli.</div>;
    }

    return (
        <div className="terminal-chart-wrap">
            <div className="terminal-chart-header">
                <div className="terminal-chart-title">Tahvil Analiz (Fiyat + Getiri)</div>
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
                        <span>YTM %{hover.yieldPct.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</span>
                        <span>{hover.time}</span>
                    </div>
                ) : (
                    <div className="terminal-ohlc muted">Fiyat ve getiri için imleci grafik üzerine getir</div>
                )}
            </div>
            <div ref={chartRef} style={{ width: '100%', height: 430 }} />
        </div>
    );
}

