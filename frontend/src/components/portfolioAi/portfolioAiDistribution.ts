import type { ManualPortfolioView } from '../../types/manualPortfolio';

export type DistSlice = { name: string; typeKey: string; value: number; pct: number };

const TYPE_LABELS: Record<string, string> = {
    STOCK: 'BIST',
    FUND: 'Fon (ETF)',
    CRYPTO: 'Kripto',
    FX: 'Döviz',
    METAL: 'Kıymetli Maden',
};

export function openDistributionByType(
    positions: ManualPortfolioView[],
    labelFn?: (k: string) => string,
): DistSlice[] {
    const map = new Map<string, number>();
    for (const p of positions) {
        if (String(p.status).toUpperCase() !== 'OPEN') continue;
        const v = Number(p.currentValue ?? 0);
        if (!Number.isFinite(v) || v <= 0) continue;
        const t = String(p.type ?? 'OTHER').toUpperCase();
        map.set(t, (map.get(t) ?? 0) + v);
    }
    const total = [...map.values()].reduce((a, b) => a + b, 0);
    if (total <= 0) return [];
    return [...map.entries()]
        .map(([typeKey, value]) => ({
            typeKey,
            name: labelFn ? labelFn(typeKey) : TYPE_LABELS[typeKey] ?? typeKey,
            value,
            pct: (100 * value) / total,
        }))
        .sort((a, b) => b.value - a.value);
}

export const DIST_COLORS = ['#3b82f6', '#06b6d4', '#eab308', '#a855f7', '#64748b', '#22c55e'];
