import { useEffect, useRef } from 'react';
import { createChart } from 'lightweight-charts';
import type { ISeriesApi, Time } from 'lightweight-charts';
import { computeTerminalTimeScaleLayout, parseTerminalChartRange } from './terminalChartScale';

type Row = { time: string; values: Record<string, number> };

type ThemeSlice = {
    bgCard: string;
    border: string;
    text: string;
    textMuted: string;
};

type Props = {
    rows: Row[];
    symbols: string[];
    colors: string[];
    lineWidthBySymbol: Record<string, number>;
    tokens: ThemeSlice;
    height?: number;
    /** Market terminal ile aynı 1D/1W/1M/1Y — eksen sıkılığını buna göre ayarlar */
    timeframeLabel?: string;
};

function lineWidthClamp(n: number): 1 | 2 | 3 | 4 {
    const r = Math.round(Math.max(1, Math.min(4, n)));
    return r as 1 | 2 | 3 | 4;
}

export function MarketCompareLwChart({
    rows,
    symbols,
    colors,
    lineWidthBySymbol,
    tokens,
    height = 300,
    timeframeLabel,
}: Props) {
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const el = containerRef.current;
        if (!el || symbols.length < 2 || !rows.length) return;

        const chartRange = parseTerminalChartRange(timeframeLabel);
        const widthPx = Math.max(320, el.clientWidth);
        const tsLay = computeTerminalTimeScaleLayout(widthPx, rows.length, chartRange);

        const chart = createChart(el, {
            width: widthPx,
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
            timeScale: {
                borderColor: tokens.border,
                timeVisible: true,
                secondsVisible: false,
                ...tsLay,
            },
            crosshair: { mode: 1 },
        });

        const seriesList: ISeriesApi<'Line'>[] = [];

        symbols.forEach((sym, i) => {
            const baseW = lineWidthClamp(lineWidthBySymbol[sym] ?? 2);
            const line = chart.addLineSeries({
                color: colors[i % colors.length],
                lineWidth: baseW,
                priceLineVisible: false,
                lastValueVisible: true,
            });
            const pts = rows
                .filter((r) => r.values[sym] != null && !Number.isNaN(r.values[sym]))
                .map((r) => ({
                    time: r.time.slice(0, 10) as Time,
                    value: r.values[sym],
                }));
            line.setData(pts);
            seriesList.push(line);
        });

        const fit = () => requestAnimationFrame(() => chart.timeScale().fitContent());
        fit();

        chart.subscribeCrosshairMove((param) => {
            seriesList.forEach((s, idx) => {
                const sym = symbols[idx];
                const baseW = lineWidthClamp(lineWidthBySymbol[sym] ?? 2);
                const hovered = param.seriesData?.has(s) ?? false;
                s.applyOptions({
                    lineWidth: lineWidthClamp(hovered ? baseW + 1 : baseW),
                });
            });
        });

        const onResize = () => {
            const w = Math.max(320, el.clientWidth);
            const lay = computeTerminalTimeScaleLayout(w, rows.length, chartRange);
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
            chart.remove();
        };
    }, [rows, symbols, colors, lineWidthBySymbol, tokens, height, timeframeLabel]);

    if (symbols.length < 2 || !rows.length) {
        return null;
    }

    return <div ref={containerRef} style={{ width: '100%', height }} />;
}
