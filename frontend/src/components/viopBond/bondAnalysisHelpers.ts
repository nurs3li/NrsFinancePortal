import type { BondType, ManualBondPosition, ViopBondCombinedSummary } from '../../types/bondPosition';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import { bondTypeFromInstrument } from './viopBondMarket';
import { bondTypeLabel } from './bondPositionLabels';

export type BondTypeFilter = 'ALL' | BondType;

export type MaturityBucket = '0-1Y' | '1-3Y' | '3Y+';

export function bondTypeFilterLabel(type: BondTypeFilter, t: (k: string, d: string) => string): string {
    if (type === 'ALL') return t('viopBond.filterAll', 'Tümü');
    return bondTypeLabel(type, t);
}

export function displayBondType(
    symbol: string,
    displayName: string | undefined,
    bondType: BondType | undefined,
    t: (k: string, d: string) => string,
): string {
    if (bondType) return bondTypeLabel(bondType, t);
    return bondTypeLabel(bondTypeFromInstrument(symbol, displayName), t);
}

export function maturityBucket(days: number | null | undefined): MaturityBucket | null {
    if (days == null || !Number.isFinite(days)) return null;
    if (days <= 365) return '0-1Y';
    if (days <= 365 * 3) return '1-3Y';
    return '3Y+';
}

export function maturityBucketLabel(bucket: MaturityBucket, t: (k: string, d: string) => string): string {
    switch (bucket) {
        case '0-1Y':
            return t('viopBond.maturityShort', '0–1 yıl');
        case '1-3Y':
            return t('viopBond.maturityMid', '1–3 yıl');
        case '3Y+':
        default:
            return t('viopBond.maturityLong', '3 yıl+');
    }
}

export function bondRiskTag(
    days: number | null | undefined,
    currency: string,
    t: (k: string, d: string) => string,
): string {
    const bucket = maturityBucket(days);
    const parts: string[] = [];
    if (currency && currency !== 'TRY') {
        parts.push(t('viopBond.riskFx', 'Kur riski'));
    } else if (bucket === '0-1Y') {
        parts.push(t('viopBond.riskShortMaturity', 'Kısa vade'));
    } else if (bucket === '3Y+') {
        parts.push(t('viopBond.riskLongMaturity', 'Uzun vade / faiz hassasiyeti'));
    } else if (bucket === '1-3Y') {
        parts.push(t('viopBond.riskMidMaturity', 'Orta vade'));
    }
    return parts.length ? parts.join(' · ') : t('viopBond.riskBalanced', 'Dengeli');
}

/** @deprecated Tahvil reel getiri backend `periodRealReturnPercent` kullanır. */
export function approxRealReturnPct(nominalReturnPct: number | null | undefined, cpiYoY: number | null): number | null {
    if (nominalReturnPct == null || !Number.isFinite(nominalReturnPct)) return null;
    if (cpiYoY == null || !Number.isFinite(cpiYoY)) return null;
    return nominalReturnPct - cpiYoY;
}

export function bondPeriodRealReturnPercent(position: {
    periodRealReturnAvailable?: boolean | null;
    periodRealReturnPercent?: number | null;
}): number | null {
    if (position.periodRealReturnAvailable !== true) return null;
    const v = position.periodRealReturnPercent;
    return v != null && Number.isFinite(Number(v)) ? Number(v) : null;
}

export function buildCombinedRiskReasons(
    summary: ViopBondCombinedSummary | undefined,
    riskStatus: string,
    openBondCount: number,
    openViopCount: number,
    t: (k: string, d: string) => string,
): string[] {
    const reasons: string[] = [];
    const effect = summary?.totalFinancialEffect ?? 0;
    const exposure = summary?.totalRiskExposure ?? 0;
    if (effect > 0 && exposure / effect > 1.5) {
        reasons.push(t('viopBond.riskReasonViopHigh', 'VİOP maruziyeti toplam değere göre yüksek'));
    } else if (effect > 0 && exposure / effect > 0.6) {
        reasons.push(t('viopBond.riskReasonViopMid', 'VİOP maruziyeti orta seviyede'));
    }
    if ((summary?.totalExpiringSoon ?? 0) > 0) {
        reasons.push(
            t('viopBond.riskReasonExpiring', '{n} ürünün vadesi yaklaşıyor').replace(
                '{n}',
                String(summary?.totalExpiringSoon ?? 0),
            ),
        );
    } else {
        reasons.push(t('viopBond.riskReasonNoExpiring', '30 gün içinde vadesi dolacak tahvil yok'));
    }
    if (openBondCount === 0) {
        reasons.push(t('viopBond.riskReasonNoBond', 'Açık tahvil pozisyonu yok'));
    } else {
        reasons.push(
            t('viopBond.riskReasonBondOpen', '{n} açık tahvil pozisyonu').replace('{n}', String(openBondCount)),
        );
    }
    if (openViopCount > 0) {
        reasons.push(
            t('viopBond.riskReasonViopOpen', '{n} açık VİOP pozisyonu').replace('{n}', String(openViopCount)),
        );
    }
    if (reasons.length === 0) {
        reasons.push(t('viopBond.riskReasonDefault', 'Maruziyet / finansal etki oranına göre: {status}').replace('{status}', riskStatus));
    }
    return reasons.slice(0, 4);
}

export function filterMarketByBondType(
    rows: TerminalListInstrumentVm[],
    filter: BondTypeFilter,
): TerminalListInstrumentVm[] {
    if (filter === 'ALL') return rows;
    return rows.filter((r) => bondTypeFromInstrument(r.symbol, r.displayName) === filter);
}

export function countMaturityDistribution(open: ManualBondPosition[]): Record<MaturityBucket, number> {
    const out: Record<MaturityBucket, number> = { '0-1Y': 0, '1-3Y': 0, '3Y+': 0 };
    for (const p of open) {
        const b = maturityBucket(p.daysToMaturity);
        if (b) out[b] += 1;
    }
    return out;
}

export function findPositionForSymbol(positions: ManualBondPosition[], symbol: string): ManualBondPosition | null {
    const key = symbol.trim().toUpperCase();
    return positions.find((p) => p.symbol.trim().toUpperCase() === key && p.status === 'OPEN') ?? null;
}
