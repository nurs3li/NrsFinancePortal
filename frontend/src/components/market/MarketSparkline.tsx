import { useEffect, useRef } from 'react';
import { createChart } from 'lightweight-charts';
import type { Time } from 'lightweight-charts';

type Props = {
    closes: number[];
    width?: number;
    height?: number;
    bgColor: string;
    /** Trend çizgisi — koyu zeminde okunaklı */
    lineColor?: string;
};

function pad2(n: number) {
    return String(n).padStart(2, '0');
}

function syntheticDay(i: number): string {
    const d = new Date(2020, 0, 1);
    d.setDate(d.getDate() + i);
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

export function MarketSparkline({
    closes,
    width = 88,
    height = 32,
    bgColor,
    lineColor = '#38bdf8',
}: Props) {
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const el = ref.current;
        if (!el || closes.length < 2) return;

        const chart = createChart(el, {
            width,
            height,
            layout: {
                background: { color: bgColor },
                textColor: 'transparent',
            },
            grid: {
                vertLines: { visible: false },
                horzLines: { visible: false },
            },
            rightPriceScale: { visible: false },
            leftPriceScale: { visible: false },
            timeScale: { visible: false },
            crosshair: { mode: 0 },
            handleScroll: false,
            handleScale: false,
        });

        const series = chart.addLineSeries({
            color: lineColor,
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
            crosshairMarkerVisible: false,
        });

        const data = closes.map((value, i) => ({
            time: syntheticDay(i) as Time,
            value,
        }));
        series.setData(data);
        chart.timeScale().fitContent();

        return () => {
            chart.remove();
        };
    }, [closes, width, height, bgColor, lineColor]);

    if (closes.length < 2) {
        return <span style={{ fontSize: 11, color: '#64748b' }}>—</span>;
    }

    return <div ref={ref} style={{ width, height, flexShrink: 0 }} />;
}
