import { useEffect, useRef } from 'react';
import { createChart } from 'lightweight-charts';
import type { Time } from 'lightweight-charts';

type IndicatorPoint = { t: string; value: number };

type ThemeSlice = {
    bgCard: string;
    border: string;
    text: string;
    textMuted: string;
};

type Props = {
    close: IndicatorPoint[];
    chartMa: Record<string, IndicatorPoint[]>;
    selectedMa: string[];
    loading: boolean;
    tokens: ThemeSlice;
    height?: number;
};

function toDayTime(iso: string): string {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso.slice(0, 10);
    return d.toISOString().slice(0, 10);
}

const MA_COLORS: Record<string, string> = {
    '7': '#22c55e',
    '30': '#eab308',
    '90': '#f87171',
};

export function MarketIndicatorAreaChart({
    close,
    chartMa,
    selectedMa,
    loading,
    tokens,
    height = 300,
}: Props) {
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const el = containerRef.current;
        if (!el || loading || !close.length) return;

        const chart = createChart(el, {
            width: el.clientWidth,
            height,
            layout: {
                background: { color: tokens.bgCard },
                textColor: tokens.text,
            },
            grid: {
                vertLines: { color: tokens.border },
                horzLines: { color: tokens.border },
            },
            rightPriceScale: { borderColor: tokens.border },
            timeScale: { borderColor: tokens.border },
            crosshair: { mode: 1 },
        });

        const area = chart.addAreaSeries({
            lineColor: '#38bdf8',
            topColor: 'rgba(56, 189, 248, 0.22)',
            bottomColor: 'rgba(56, 189, 248, 0)',
            lineWidth: 2,
            priceLineVisible: false,
        });
        area.setData(
            close.map((p) => ({
                time: toDayTime(p.t) as Time,
                value: Number(p.value),
            })),
        );

        for (const w of ['7', '30', '90'] as const) {
            if (!selectedMa.includes(w)) continue;
            const pts = chartMa[w];
            if (!pts?.length) continue;
            const line = chart.addLineSeries({
                color: MA_COLORS[w] ?? tokens.textMuted,
                lineWidth: 2,
                priceLineVisible: false,
                lastValueVisible: false,
            });
            line.setData(
                pts.map((p) => ({
                    time: toDayTime(p.t) as Time,
                    value: Number(p.value),
                })),
            );
        }

        chart.timeScale().fitContent();

        const onResize = () => {
            chart.applyOptions({ width: el.clientWidth });
        };
        window.addEventListener('resize', onResize);

        return () => {
            window.removeEventListener('resize', onResize);
            chart.remove();
        };
    }, [close, chartMa, selectedMa, loading, tokens, height]);

    if (loading) {
        return <p style={{ color: tokens.textMuted, fontSize: 14 }}>Grafik yükleniyor...</p>;
    }
    if (!close.length) {
        return <p style={{ color: tokens.textMuted, fontSize: 14 }}>Bu sembol için geçmiş veri yok.</p>;
    }

    return <div ref={containerRef} style={{ width: '100%', height }} />;
}
