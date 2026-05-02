import { useEffect, useMemo, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import type { Time } from 'lightweight-charts';

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

function toDayTime(value: string): Time {
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return value.slice(0, 10) as Time;
    return d.toISOString().slice(0, 10) as Time;
}

export function ViopTerminalChart({ points, ma7, ma21, showMa, loading, timeframeLabel, trendLabel, tokens }: Props) {
    const chartRef = useRef<HTMLDivElement>(null);
    const [hover, setHover] = useState<ViopPoint | null>(null);
    const sorted = useMemo(() => [...points].sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime()), [points]);
    const limitedData = sorted.length > 0 && sorted.length < 5;

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
                vertLines: { color: 'rgba(71, 85, 105, 0.18)' },
                horzLines: { color: 'rgba(71, 85, 105, 0.18)' },
            },
            rightPriceScale: { borderColor: tokens.border },
            timeScale: {
                borderColor: tokens.border,
                timeVisible: true,
                secondsVisible: false,
                barSpacing: sorted.length < 8 ? 10 : 7,
                minBarSpacing: sorted.length < 8 ? 8 : 5,
                rightOffset: sorted.length < 8 ? 8 : 2,
            },
            crosshair: { mode: 1 },
        });

        const priceArea = chart.addAreaSeries({
            lineColor: '#38bdf8',
            topColor: 'rgba(56,189,248,0.35)',
            bottomColor: 'rgba(56,189,248,0.06)',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: true,
        });
        priceArea.setData(
            sorted
                .filter((p) => Number.isFinite(p.price) && p.price > 0)
                .map((p) => ({ time: toDayTime(p.time), value: p.price }))
        );

        if (showMa) {
            const ma7Series = chart.addLineSeries({
                color: '#22c55e',
                lineWidth: 1,
                priceLineVisible: false,
                lastValueVisible: false,
            });
            ma7Series.setData(ma7.map((p) => ({ time: toDayTime(p.time), value: p.value })));

            const ma21Series = chart.addLineSeries({
                color: '#f59e0b',
                lineWidth: 1,
                priceLineVisible: false,
                lastValueVisible: false,
            });
            ma21Series.setData(ma21.map((p) => ({ time: toDayTime(p.time), value: p.value })));
        }

        const oiHistogram = chart.addHistogramSeries({
            color: 'rgba(148,163,184,0.22)',
            priceFormat: { type: 'volume' },
            priceScaleId: '',
            lastValueVisible: false,
            priceLineVisible: false,
        });
        oiHistogram.priceScale().applyOptions({
            scaleMargins: { top: 0.92, bottom: 0 },
        });
        oiHistogram.setData(
            sorted.map((p) => ({
                time: toDayTime(p.time),
                value: Number(p.openInterest ?? 0),
                color: 'rgba(148,163,184,0.2)',
            }))
        );

        chart.subscribeCrosshairMove((param) => {
            if (!param?.time) {
                setHover(null);
                return;
            }
            const day = String(param.time).slice(0, 10);
            const row = sorted.find((p) => toDayTime(p.time) === day);
            setHover(row ?? null);
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
        return <div className="terminal-chart-empty">VİOP veri noktası bulunamadı.</div>;
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
            <div ref={chartRef} style={{ width: '100%', height: 430 }} />
        </div>
    );
}

