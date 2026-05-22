import type { BondType, CouponFrequency } from '../../types/bondPosition';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';

export type CouponType = 'ZERO_COUPON' | 'COUPONED' | 'UNKNOWN';
export type BondPriceType = 'CLEAN' | 'DIRTY' | 'INDICATIVE' | 'UNKNOWN';

export type BondCouponDefaults = {
    couponRate: number | null;
    couponFrequency: CouponFrequency;
    couponType: CouponType;
};

export function couponFrequencyFromPerYear(perYear: number | null | undefined): CouponFrequency | null {
    if (perYear == null || !Number.isFinite(perYear)) return null;
    if (perYear >= 4) return 'QUARTERLY';
    if (perYear >= 2) return 'SEMI_ANNUAL';
    if (perYear >= 1) return 'ANNUAL';
    return 'NONE';
}

/** EVDS kupon faiz oranı varsa kuponlu + 6 ayda bir; açık kuponsuz metadata yoksa Kuponsuz seçme. */
export function inferBondCouponDefaults(input: {
    couponRate?: number | null;
    couponFrequencyPerYear?: number | null;
    couponFrequency?: CouponFrequency | null;
    bondType?: BondType;
    isZeroCoupon?: boolean;
}): BondCouponDefaults {
    const rate =
        input.couponRate != null && Number.isFinite(Number(input.couponRate)) && Number(input.couponRate) > 0
            ? Number(input.couponRate)
            : null;

    if (rate != null) {
        const fromMeta = couponFrequencyFromPerYear(input.couponFrequencyPerYear);
        let freq: CouponFrequency =
            fromMeta && fromMeta !== 'NONE'
                ? fromMeta
                : 'SEMI_ANNUAL';
        if (input.couponFrequency && input.couponFrequency !== 'NONE') {
            freq = input.couponFrequency;
        }
        return { couponRate: rate, couponFrequency: freq, couponType: 'COUPONED' };
    }

    if (input.isZeroCoupon === true || input.bondType === 'TREASURY_BILL') {
        return { couponRate: 0, couponFrequency: 'NONE', couponType: 'ZERO_COUPON' };
    }

    if (input.couponFrequency && input.couponFrequency !== 'NONE') {
        return { couponRate: null, couponFrequency: input.couponFrequency, couponType: 'UNKNOWN' };
    }

    return { couponRate: null, couponFrequency: 'NONE', couponType: 'UNKNOWN' };
}

export function inferBondCouponFromInstrument(
    instrument: TerminalListInstrumentVm | null,
    editCouponFrequency?: CouponFrequency | null,
    editCouponRate?: number | null,
): BondCouponDefaults {
    if (editCouponRate != null || editCouponFrequency != null) {
        return inferBondCouponDefaults({
            couponRate: editCouponRate,
            couponFrequency: editCouponFrequency ?? undefined,
            couponFrequencyPerYear: instrument?.couponFrequencyPerYear,
        });
    }
    return inferBondCouponDefaults({
        couponRate: instrument?.couponRate,
        couponFrequencyPerYear: instrument?.couponFrequencyPerYear,
        bondType: instrument ? undefined : undefined,
    });
}

export function couponPeriodMonths(freq: CouponFrequency): number {
    switch (freq) {
        case 'ANNUAL':
            return 12;
        case 'SEMI_ANNUAL':
            return 6;
        case 'QUARTERLY':
            return 3;
        case 'NONE':
        default:
            return 0;
    }
}

export function holdingMonthsBetween(buyDate: string, asOfDate?: string): number | null {
    if (!buyDate) return null;
    const end = asOfDate ? new Date(`${asOfDate}T12:00:00`) : new Date();
    const start = new Date(`${buyDate}T12:00:00`);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return null;
    const days = Math.max(0, Math.round((end.getTime() - start.getTime()) / 86_400_000));
    return days / 30.4375;
}
