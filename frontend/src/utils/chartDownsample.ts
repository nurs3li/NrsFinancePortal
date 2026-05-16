/** Grafik çizimi için seriyi en fazla maxPoints noktaya indirger (ilk/son + eşit aralık). */
export function downsampleByDate<T extends { date: string }>(
    points: T[],
    maxPoints: number,
    pinDates: string[] = [],
): T[] {
    if (points.length <= maxPoints) return points;

    const pin = new Set(pinDates.map((d) => d.slice(0, 10)).filter(Boolean));
    const picked = new Map<string, T>();

    picked.set(points[0]!.date, points[0]!);
    picked.set(points[points.length - 1]!.date, points[points.length - 1]!);

    const slots = Math.max(2, maxPoints) - 2;
    const step = (points.length - 1) / (slots + 1);
    for (let i = 1; i <= slots; i++) {
        const idx = Math.min(points.length - 1, Math.round(i * step));
        const p = points[idx]!;
        picked.set(p.date, p);
    }

    for (const p of points) {
        if (pin.has(p.date)) picked.set(p.date, p);
    }

    return [...picked.values()].sort((a, b) => a.date.localeCompare(b.date));
}
