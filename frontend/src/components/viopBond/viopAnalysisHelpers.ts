import type { ManualViopPosition, ViopPositionSummary } from '../../types/viopPosition';
import type { ViopCategory } from '../../types/viopPosition';

export type ViopPnlBucket = { profit: number; loss: number; neutral: number };

export function findOpenViopPosition(
    positions: ManualViopPosition[],
    symbol: string,
): ManualViopPosition | null {
    const key = symbol.trim().toUpperCase();
    return positions.find((p) => p.symbol.trim().toUpperCase() === key && p.status === 'OPEN') ?? null;
}

export function countViopPnlBuckets(open: ManualViopPosition[]): ViopPnlBucket {
    let profit = 0;
    let loss = 0;
    let neutral = 0;
    for (const p of open) {
        const pnl = Number(p.unrealizedPnl ?? 0);
        if (!Number.isFinite(pnl) || Math.abs(pnl) < 0.005) neutral++;
        else if (pnl > 0) profit++;
        else loss++;
    }
    return { profit, loss, neutral };
}

export function topExposurePositions(open: ManualViopPosition[], limit = 5) {
    return [...open]
        .filter((p) => p.riskExposure != null && Number.isFinite(Number(p.riskExposure)))
        .sort((a, b) => Number(b.riskExposure) - Number(a.riskExposure))
        .slice(0, limit);
}

export function dominantViopCategory(open: ManualViopPosition[]): ViopCategory | null {
    if (!open.length) return null;
    const counts: Record<string, number> = {};
    for (const p of open) {
        counts[p.viopCategory] = (counts[p.viopCategory] ?? 0) + 1;
    }
    const sorted = Object.entries(counts).sort((a, b) => b[1] - a[1]);
    return (sorted[0]?.[0] as ViopCategory) ?? null;
}

export function buildViopRiskReasons(
    open: ManualViopPosition[],
    summary: ViopPositionSummary | undefined,
    riskSummary: {
        top?: ManualViopPosition;
        longPct: number;
        shortPct: number;
        nearest?: ManualViopPosition;
    } | null,
    t: (k: string, d: string) => string,
): string[] {
    const reasons: string[] = [];
    if (riskSummary?.top) {
        reasons.push(
            t('viopBond.riskReasonTopExposure', 'En yüksek maruziyet: {sym}').replace(
                '{sym}',
                riskSummary.top.symbol,
            ),
        );
    }
    if (riskSummary && open.length > 0) {
        reasons.push(
            t('viopBond.riskReasonLongPct', 'Uzun yoğunluğu: %{n}').replace('{n}', String(riskSummary.longPct)),
        );
    }
    const dom = dominantViopCategory(open);
    if (dom === 'COMMODITY') {
        reasons.push(t('viopBond.riskReasonCommodity', 'Emtia maruziyeti yoğun'));
    } else if (dom === 'FX') {
        reasons.push(t('viopBond.riskReasonFx', 'Döviz maruziyeti yoğun'));
    } else if (dom === 'INDEX') {
        reasons.push(t('viopBond.riskReasonIndex', 'Endeks maruziyeti yoğun'));
    }
    if ((summary?.expiringSoonCount ?? 0) > 0) {
        reasons.push(
            t('viopBond.riskReasonViopExpiring', '{n} kontratın vadesi 14 gün içinde').replace(
                '{n}',
                String(summary?.expiringSoonCount ?? 0),
            ),
        );
    } else {
        reasons.push(t('viopBond.riskReasonViopNoExpiring', '14 gün içinde vadesi dolacak kontrat yok'));
    }
    return reasons.slice(0, 4);
}

export function viopCategoryRiskNote(cat: ViopCategory | null, t: (k: string, d: string) => string): string {
    switch (cat) {
        case 'FX':
            return t('viopBond.viopRiskFx', 'Döviz vadeli kontratlar kur hareketine duyarlıdır.');
        case 'INDEX':
            return t('viopBond.viopRiskIndex', 'Endeks vadeli kontratlar genel piyasa oynaklığına duyarlıdır.');
        case 'COMMODITY':
            return t('viopBond.viopRiskCommodity', 'Emtia vadeli kontratlar emtia fiyat riski taşır.');
        case 'EQUITY':
            return t('viopBond.viopRiskEquity', 'Pay vadeli kontratlar ilgili hisse fiyatına duyarlıdır.');
        default:
            return t(
                'viopBond.viopRiskLeverage',
                'Kaldıraç etkisi nedeniyle küçük fiyat hareketleri K/Z’yi büyütebilir.',
            );
    }
}

export function daysToExpiryFromIso(expiryIso: string | null | undefined): number | null {
    if (!expiryIso || !/^\d{4}-\d{2}-\d{2}$/.test(expiryIso)) return null;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const exp = new Date(expiryIso + 'T00:00:00');
    const diff = Math.ceil((exp.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
    return Number.isFinite(diff) ? diff : null;
}
