import { describe, expect, it } from 'vitest';
import {
    addDaysYmd,
    buildDailyCpiIndexMap,
    compoundDepositTryDaily,
    computePurchasingPowerHistoryDays,
    dailyCpiIndexAt,
    enumerateDaysInclusive,
    buildPurchasingPowerSeries,
} from './marketPurchasingPower';

describe('compoundDepositTryDaily', () => {
    const rates = [{ date: '2026-01-01', value: 45 }];

    it('applies annual rate as daily compound, not weekly lump sum', () => {
        const end = compoundDepositTryDaily('2026-04-20', '2026-05-16', 17_395, rates);
        expect(end).toBeGreaterThan(17_395);
        expect(end).toBeLessThan(19_500);
    });

    it('returns principal when end equals anchor', () => {
        expect(compoundDepositTryDaily('2026-04-20', '2026-04-20', 10_000, rates)).toBe(10_000);
    });
});

describe('buildDailyCpiIndexMap', () => {
    const cpi = [
        { date: '2026-04-01', value: 3000 },
        { date: '2026-05-01', value: 3100 },
    ];

    it('increases index each calendar day within a month', () => {
        const map = buildDailyCpiIndexMap(cpi, '2026-04-10', '2026-04-15');
        const d10 = dailyCpiIndexAt(map, '2026-04-10')!;
        const d15 = dailyCpiIndexAt(map, '2026-04-15')!;
        expect(d15).toBeGreaterThan(d10);
    });

    it('extrapolates backward before first monthly observation', () => {
        const map = buildDailyCpiIndexMap(cpi, '2026-03-20', '2026-03-25');
        const d20 = dailyCpiIndexAt(map, '2026-03-20')!;
        const d25 = dailyCpiIndexAt(map, '2026-03-25')!;
        expect(d25).toBeGreaterThan(d20);
    });

    it('melts purchasing power day by day after anchor', () => {
        const map = buildDailyCpiIndexMap(cpi, '2026-04-20', '2026-05-10');
        const anchorCpi = dailyCpiIndexAt(map, '2026-04-20')!;
        const lot = 17_395;
        const real20 = lot * (anchorCpi / dailyCpiIndexAt(map, '2026-04-20')!);
        const real25 = lot * (anchorCpi / dailyCpiIndexAt(map, '2026-04-25')!);
        const real05 = lot * (anchorCpi / dailyCpiIndexAt(map, '2026-05-05')!);
        expect(real20).toBeCloseTo(lot, 0);
        expect(real25).toBeLessThan(lot);
        expect(real05).toBeLessThan(real25);
    });
});

describe('enumerateDaysInclusive', () => {
    it('includes both endpoints', () => {
        const days = enumerateDaysInclusive('2026-04-20', '2026-04-22');
        expect(days).toEqual(['2026-04-20', '2026-04-21', '2026-04-22']);
        expect(addDaysYmd('2026-04-20', 1)).toBe('2026-04-21');
    });
});

describe('buildPurchasingPowerSeries', () => {
    it('produces daily erosion when anchor is before first CPI print', () => {
        const series = buildPurchasingPowerSeries({
            anchorDate: '2026-04-17',
            lotCostTry: 17_395,
            assetUnitPriceByDate: [
                { date: '2026-04-17', value: 100 },
                { date: '2026-05-16', value: 110 },
            ],
            assetInTry: true,
            usdTryByDate: [],
            cpiIndexByDate: [
                { date: '2026-05-01', value: 3100 },
                { date: '2026-06-01', value: 3200 },
            ],
            depositRatePctAnnual: [{ date: '2026-01-01', value: 45 }],
        });
        expect(series.length).toBeGreaterThan(10);
        const first = series[0]!;
        const mid = series[Math.floor(series.length / 2)]!;
        const last = series[series.length - 1]!;
        expect(first.inflationRealTry).toBeCloseTo(17_395, 0);
        expect(mid.inflationRealTry).toBeLessThan(17_395);
        expect(last.inflationRealTry).toBeLessThan(mid.inflationRealTry);
    });
});

describe('computePurchasingPowerHistoryDays', () => {
    it('extends beyond chart range when anchor is older', () => {
        const days = computePurchasingPowerHistoryDays(30, '2024-01-01', '2026-05-16');
        expect(days).toBeGreaterThan(30);
        expect(days).toBeGreaterThanOrEqual(800);
    });

    it('uses at least span from anchor to today', () => {
        expect(computePurchasingPowerHistoryDays(30, '2026-04-01', '2026-05-16')).toBeGreaterThanOrEqual(30);
    });
});
