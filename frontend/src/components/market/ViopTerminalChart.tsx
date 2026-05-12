import { memo, useEffect, useMemo, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import type { LogicalRange, Time, UTCTimestamp } from 'lightweight-charts';
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

/**
 * VIOP geçmişi UTCTimestamp (saniye) olarak verilir; lightweight-charts gerçek zaman ekseni üzerine
 * yerleştirip noktalar arası takvim boşluğunu (örn. 1Y'de aylar arası) doğru gösterir. Önceki sürüm
 * date-string ("YYYY-MM-DD") veriyordu: chart bunu BusinessDay gibi sıkı 1‑bar/1‑gün konumlandırıyor,
 * az noktada (1Y'de sparse) aralar orantısız görünüyor ve x ekseninde takvim eşitsizliği kayboluyordu.
 */
function toChartTime(value: string): Time {
    const d = new Date(value);
    const ms = d.getTime();
    if (Number.isNaN(ms)) {
        // Geçersizse epoch döndür: chart noktayı zaten setData'da filtreleyecek.
        return 0 as UTCTimestamp;
    }
    return Math.floor(ms / 1000) as UTCTimestamp;
}

function chartTimeKey(value: Time): string {
    if (typeof value === 'number') {
        return String(value);
    }
    if (typeof value === 'string') {
        return value;
    }
    return JSON.stringify(value);
}

function ViopTerminalChartImpl({ points, ma7, ma21, showMa, loading, timeframeLabel, trendLabel, tokens }: Props) {
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
    // rAF-throttle'lı crosshair handler için cleanup callback'i; chart yıkılırken pending frame iptal edilir.
    const crosshairRafRef = useRef<(() => void) | null>(null);
    // Parent her renderda yeni `tokens` object literal'i veriyor; mount useEffect'i deps'e koyarsak
    // chart sonsuz destroy+create döngüsüne girer ve grafik titrer + ana thread tükenir. Ref ile en
    // güncel tokens'ı tut, mount tek sefer çalışsın.
    const tokensRef = useRef(tokens);
    useEffect(() => {
        tokensRef.current = tokens;
    });
    const [hover, setHover] = useState<ViopPoint | null>(null);
    const chartHeight = 520;
    const sorted = useMemo(() => {
        // Exact timestamp tabanlı dedup: aynı saniyeyi paylaşan iki snapshot bulunmaz; gün dahilindeki
        // intra-day örnekleri kaybetmiyoruz.
        const byTime = new Map<string, ViopPoint>();
        [...points]
            .filter((p) => Number.isFinite(p.price) && p.price > 0)
            .sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime())
            .forEach((p) => {
                const t = toChartTime(p.time);
                if (typeof t === 'number' && t <= 0) return;
                byTime.set(chartTimeKey(t), p);
            });
        return [...byTime.values()];
    }, [points]);
    const limitedData = sorted.length > 0 && sorted.length < 5;

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
                vertLines: { color: 'rgba(71, 85, 105, 0.18)' },
                horzLines: { color: 'rgba(71, 85, 105, 0.18)' },
            },
            rightPriceScale: { borderColor: t.border, scaleMargins: { top: 0.1, bottom: 0.1 } },
            timeScale: {
                borderColor: t.border,
                timeVisible: true,
                secondsVisible: false,
                // 1Y görünümünde noktalar arası takvim mesafesini doğru göstermek için sabit edge kilitlerini
                // ve resize lock'unu kaldırdık: fitContent + UTCTimestamp gerçek tarihlere göre yerleştirir.
                rightOffset: 4,
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

        // Lightweight-charts fareyi piksel piksel takip eder ve saniyede 60+ kez çağırır.
        // Idempotent olsa bile React scheduler her çağrıda dispatch yapar; chart canvas
        // üzerinde fare gezinirken navigasyon click'leri kuyruğa girip işlenmeyebiliyor
        // (VİOP sekmesinden çıkamama bug'ının asıl nedeni). rAF ile frame başına 1 kez
        // çalışacak şekilde sıkıştırıyoruz.
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
                setHover((prev) => {
                    if (prev?.time === row?.time && prev?.price === row?.price) return prev;
                    return row;
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
            if (logicalRangeRef.current) {
                chart.timeScale().setVisibleLogicalRange(logicalRangeRef.current);
            }
        };
        window.addEventListener('resize', onResize);
        return () => {
            window.removeEventListener('resize', onResize);
            crosshairRafRef.current?.();
            chart.remove();
            chartApiRef.current = null;
            priceAreaRef.current = null;
            priceLineRef.current = null;
            ma7Ref.current = null;
            ma21Ref.current = null;
            oiRef.current = null;
            hasInitialFitRef.current = false;
        };
        // Mount/unmount tek sefer: prop değişen tokens/timeframe için ayrı effect'ler aşağıda.
    }, []);

    /** Tokens (tema) değişince chart'ı yeniden kurmak yerine sadece renkleri güncelle. */
    useEffect(() => {
        const chart = chartApiRef.current;
        if (!chart) return;
        chart.applyOptions({
            layout: { background: { color: tokens.bgCard }, textColor: tokens.text },
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
            // display:none -> block geçişinde clientWidth değişmiş olabilir; tekrar uygula.
            width: widthPx,
            timeScale: {
                borderColor: tokensRef.current.border,
                timeVisible: true,
                secondsVisible: false,
                ...tsLay,
                shiftVisibleRangeOnNewBar: false,
            },
        });

        // fitContent yalnızca ilk veride veya timeframe değiştiğinde çağrılır; aksi halde her
        // küçük data refresh'inde view zıplar ve titrer.
        if (!hasInitialFitRef.current || rangeChanged) {
            requestAnimationFrame(() => {
                chart.timeScale().fitContent();
                logicalRangeRef.current = chart.timeScale().getVisibleLogicalRange();
                hasInitialFitRef.current = true;
            });
        }
    }, [loading, sorted, ma7, ma21, showMa, timeframeLabel]);

    // Erken-return YOK: chart div'ini her zaman mount edelim. Aksi halde ilk renderda div bulunmaz,
    // mount useEffect chartRef.current === null görür ve `[]` deps olduğu için bir daha tetiklenmez,
    // veri sonra gelse de chart hiç kurulmaz. Boş/eksik durumlarda chart container'ı `display:none`
    // ile gizlenir; mesajları üstte gösteririz.
    const showChart = !loading && sorted.length >= 2;
    const emptyMessage = loading
        ? 'Grafik yükleniyor...'
        : !sorted.length
            ? 'VİOP veri noktası bulunamadı.'
            : sorted.length < 2
                ? 'Bu kontrat için çizim yapacak yeterli VİOP geçmişi yok (en az 2 nokta gerekli).'
                : null;

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
            {emptyMessage ? (
                <div className="terminal-chart-empty">{emptyMessage}</div>
            ) : null}
            {showChart && limitedData ? (
                <div style={{ color: tokens.textMuted, marginBottom: 8, fontSize: 12 }}>Bu periyotta sınırlı VİOP verisi.</div>
            ) : null}
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

/*
 * React.memo: Parent state (trendPeriod vb.) re-renderlarinda VİOP chart'in titremesini
 * engeller. Prop'lar parent'ta useMemo ile stabil; memo "esit referans" karsilastirmasi yapip
 * gereksiz unmount/mount onler.
 */
export const ViopTerminalChart = memo(ViopTerminalChartImpl);

