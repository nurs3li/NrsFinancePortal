import type { Time } from 'lightweight-charts';

export function chartTimeKey(value: Time): string {
    if (typeof value === 'number') {
        return new Date(value * 1000).toISOString();
    }
    return String(value);
}

type LinePointInput = { time: string; value: number };

/**
 * lightweight-charts setData: zaman artan sırada ve tekrarsız olmalı.
 * Farklı API timestamp'leri aynı chart Time'a düşebilir; son değer korunur.
 */
export function normalizeChartLineSeries(
    points: LinePointInput[],
    toChartTime: (s: string) => Time,
): { time: Time; value: number }[] {
    const byTime = new Map<string, { time: Time; value: number }>();
    [...points]
        .filter((p) => Number.isFinite(p.value))
        .sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime())
        .forEach((p) => {
            const t = toChartTime(p.time);
            byTime.set(chartTimeKey(t), { time: t, value: p.value });
        });
    return [...byTime.values()];
}
