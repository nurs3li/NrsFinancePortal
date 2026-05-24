import { describe, expect, it } from 'vitest';
import { computeBondPositionMetrics } from './viopBondCalculations';

describe('computeBondPositionMetrics', () => {
    it('applies nominal × price/100 for value and price P/L', () => {
        const m = computeBondPositionMetrics({
            nominalValue: 50_000,
            buyPrice: 100,
            currentPrice: 72.99,
            buyDate: '2024-06-01',
            couponRate: 0,
            couponFrequency: 'NONE',
            asOfDate: '2026-05-19',
        });
        expect(m.buyValue).toBeCloseTo(50_000, 2);
        expect(m.currentValue).toBeCloseTo(36_495, 2);
        expect(m.pricePnl).toBeCloseTo(-13_505, 2);
    });

    it('semi-annual coupon: annual = nominal×rate/100, periodic = annual/2', () => {
        const m = computeBondPositionMetrics({
            nominalValue: 10_000,
            buyPrice: 100,
            currentPrice: 100,
            buyDate: '2025-08-01',
            couponRate: 17.2,
            couponFrequency: 'SEMI_ANNUAL',
            asOfDate: '2026-05-01',
        });
        expect(m.annualCoupon).toBeCloseTo(1_720, 2);
        expect(m.periodicCoupon).toBeCloseTo(860, 2);
        expect(m.completedCouponPeriods).toBe(1);
        expect(m.collectedCoupon).toBeCloseTo(860, 2);
        expect(m.totalReturn).toBeCloseTo(860, 2);
    });

    it('total return = price P/L + collected coupon', () => {
        const m = computeBondPositionMetrics({
            nominalValue: 10_000,
            buyPrice: 95,
            currentPrice: 100,
            buyDate: '2025-01-01',
            couponRate: 10,
            couponFrequency: 'SEMI_ANNUAL',
            asOfDate: '2026-01-01',
        });
        expect(m.pricePnl).toBeCloseTo(500, 2);
        expect(m.collectedCoupon).toBeGreaterThan(0);
        expect(m.totalReturn).toBeCloseTo((m.pricePnl ?? 0) + m.collectedCoupon, 2);
    });

    it('defaults coupon frequency to semi-annual when rate > 0 and frequency NONE', () => {
        const m = computeBondPositionMetrics({
            nominalValue: 10_000,
            buyPrice: 100,
            currentPrice: 100,
            buyDate: '2025-08-01',
            couponRate: 17.2,
            couponFrequency: 'NONE',
            asOfDate: '2026-05-01',
        });
        expect(m.annualCoupon).toBeCloseTo(1_720, 2);
        expect(m.collectedCoupon).toBeCloseTo(860, 2);
    });
});
