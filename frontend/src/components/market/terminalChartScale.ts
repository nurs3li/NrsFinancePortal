export type TerminalChartRange = '1D' | '1W' | '1M' | '3M' | '6M' | '1Y' | '2Y';

/** `chartTfLabel` / UI kısa etiket → iç aralık anahtarı */
export function parseTerminalChartRange(label: string | undefined): TerminalChartRange {
    if (!label) return '1M';
    const k = label.trim();
    const map: Record<string, TerminalChartRange> = {
        '1D': '1D',
        '1G': '1D',
        '1W': '1W',
        '1H': '1W',
        '1M': '1M',
        '1A': '1M',
        '3M': '3M',
        '3A': '3M',
        '6M': '6M',
        '6A': '6M',
        '1Y': '1Y',
        '2Y': '2Y',
    };
    return map[k] ?? '1M';
}

/**
 * lightweight-charts: sabit barSpacing ile tüm mumlar görünür alana sığmayabilir; kütüphane
 * görünümü sağa kaydırıp yalnızca son dönemi gösterebilir. İlk yüklemede tüm seriyi göstermek için
 * barSpacing'i konteyner genişliği ve nokta sayısına göre seçeriz; kullanıcı tekerlek / pinch ile yakınlaştırabilir.
 *
 * Üst sınır eskiden 14 px'di; az noktada (1Y'de sparse VIOP verisi) noktalar grafiğin sol tarafına
 * sıkışıyordu. Şimdi geniş aralıkta nokta başına 48 px'e izin verip kalan boşluğu chart genişliğine
 * yayıyoruz; çok noktada yine sıkı tutuyoruz.
 */
export function computeTerminalTimeScaleLayout(
    chartWidthPx: number,
    barCount: number,
    range: TerminalChartRange
): { barSpacing: number; minBarSpacing: number; rightOffset: number } {
    const safeW = Math.max(100, chartWidthPx);
    const n = Math.max(2, barCount);
    const usable = Math.max(40, safeW - 60);
    let barSpacing = usable / n;
    barSpacing = Math.max(1.25, Math.min(48, barSpacing));
    const minBarSpacing = Math.max(0.5, Math.min(8, barSpacing * 0.4));
    const fewBars = n <= 6;
    const longHorizon =
        range === '1Y' || range === '2Y' || range === '6M' || range === '3M' || range === '1M' || range === '1W';
    const rightOffset = fewBars ? 6 : longHorizon ? 3 : 5;
    return { barSpacing, minBarSpacing, rightOffset };
}
