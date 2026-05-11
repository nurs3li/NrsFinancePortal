export type TerminalChartRange = '1D' | '1W' | '1M' | '1Y';

export function parseTerminalChartRange(label: string | undefined): TerminalChartRange {
    if (label === '1D' || label === '1W' || label === '1M' || label === '1Y') return label;
    return '1M';
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
    const rightOffset = fewBars ? 6 : range === '1Y' || range === '1M' ? 3 : 5;
    return { barSpacing, minBarSpacing, rightOffset };
}
