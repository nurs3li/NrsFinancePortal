import { memo, useEffect, useRef } from 'react';
import { createChart } from 'lightweight-charts';
import type { Time } from 'lightweight-charts';
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

function toChartTime(raw: string): Time | null {
    const value = String(raw ?? '').trim();
    if (!value) return null;
    if (!value.includes('T')) return value.slice(0, 10) as Time;
    const parsed = Date.parse(value);
    if (!Number.isFinite(parsed)) return value.slice(0, 10) as Time;
    return Math.floor(parsed / 1000) as Time;
}

function MarketCompareLwChartImpl({
    rows,
    symbols,
    colors,
    lineWidthBySymbol,
    tokens,
    height = 300,
    timeframeLabel,
}: Props) {
    const containerRef = useRef<HTMLDivElement>(null);
    const tokensRef = useRef(tokens);
    tokensRef.current = tokens;

    useEffect(() => {
        const el = containerRef.current;
        if (!el) return;

        const chartRange = parseTerminalChartRange(timeframeLabel);
        const widthPx = Math.max(160, Math.floor(el.clientWidth));
        const t = tokensRef.current;
        const hasData = symbols.length >= 2 && rows.length > 0;
        const tsLay = computeTerminalTimeScaleLayout(widthPx, hasData ? rows.length : 2, chartRange);

        const chart = createChart(el, {
            width: widthPx,
            height,
            layout: {
                background: { color: t.bgCard },
                textColor: t.text,
            },
            grid: {
                vertLines: { color: t.border },
                horzLines: { color: t.border },
            },
            rightPriceScale: { borderColor: t.border },
            timeScale: {
                borderColor: t.border,
                timeVisible: true,
                secondsVisible: false,
                ...tsLay,
            },
            crosshair: { mode: 1 },
        });

        if (hasData) {
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
                    .map((r) => {
                        const time = toChartTime(r.time);
                        if (time == null) return null;
                        return {
                            time,
                            value: r.values[sym],
                        };
                    })
                    .filter((p): p is { time: Time; value: number } => p != null);
                line.setData(pts);
            });
        } else {
            const ghost = chart.addLineSeries({
                color: 'rgba(148, 163, 184, 0.14)',
                lineWidth: 1,
                priceLineVisible: false,
                lastValueVisible: false,
                crosshairMarkerVisible: false,
            });
            const end = new Date();
            const start = new Date(end.getTime() - 120 * 86_400_000);
            ghost.setData([
                { time: start.toISOString().slice(0, 10) as Time, value: 100 },
                { time: end.toISOString().slice(0, 10) as Time, value: 100 },
            ]);
        }

        const fit = () => requestAnimationFrame(() => chart.timeScale().fitContent());
        fit();

        const onResize = () => {
            const w = Math.max(160, Math.floor(el.clientWidth));
            const lay = computeTerminalTimeScaleLayout(w, hasData ? rows.length : 2, chartRange);
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
    }, [
        rows,
        symbols,
        colors,
        lineWidthBySymbol,
        height,
        timeframeLabel,
        tokens.bgCard,
        tokens.border,
        tokens.text,
        tokens.textMuted,
    ]);

    return <div ref={containerRef} style={{ width: '100%', height }} />;
}

/*
 * React.memo: Trend periyodu vb. parent state guncellemelerinde karsilastirma grafiginin
 * gereksiz re-render'larini engeller. Prop'lar parent'ta useMemo'lu (rows/symbols/colors).
 */
export const MarketCompareLwChart = memo(MarketCompareLwChartImpl);
