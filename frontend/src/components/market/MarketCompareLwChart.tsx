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
        if (!el || symbols.length < 2 || !rows.length) return;

        const chartRange = parseTerminalChartRange(timeframeLabel);
        const widthPx = Math.max(320, el.clientWidth);
        const tsLay = computeTerminalTimeScaleLayout(widthPx, rows.length, chartRange);
        const t = tokensRef.current;

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
        });

        const fit = () => requestAnimationFrame(() => chart.timeScale().fitContent());
        fit();

        const onResize = () => {
            const w = Math.max(320, el.clientWidth);
            const lay = computeTerminalTimeScaleLayout(w, rows.length, chartRange);
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

    if (symbols.length < 2 || !rows.length) {
        return null;
    }

    return <div ref={containerRef} style={{ width: '100%', height }} />;
}

/*
 * React.memo: Trend periyodu vb. parent state guncellemelerinde karsilastirma grafiginin
 * gereksiz re-render'larini engeller. Prop'lar parent'ta useMemo'lu (rows/symbols/colors).
 */
export const MarketCompareLwChart = memo(MarketCompareLwChartImpl);
