import { describe, expect, it } from 'vitest';
import { buildPreciousMetalTryComparison, resolvePreciousMetalComparisonUnit } from './preciousMetalComparison';

describe('resolvePreciousMetalComparisonUnit', () => {
    it('uses gram for XAU_TRY', () => {
        const u = resolvePreciousMetalComparisonUnit('XAU_TRY');
        expect(u.unitLabel).toBe('1 gram');
        expect(u.assetInTry).toBe(true);
    });

    it('uses ounce for XPD_USD_OZ', () => {
        const u = resolvePreciousMetalComparisonUnit('XPD_USD_OZ');
        expect(u.unitLabel).toBe('1 ons');
        expect(u.assetInTry).toBe(false);
    });
});

describe('buildPreciousMetalTryComparison', () => {
    it('converts ounce USD × USDTRY per day', () => {
        const r = buildPreciousMetalTryComparison({
            symbol: 'XPD_USD_OZ',
            anchorDate: '2026-05-01',
            assetUnitPriceByDate: [
                { date: '2026-05-01', value: 1000 },
                { date: '2026-05-10', value: 1100 },
            ],
            usdTryByDate: [
                { date: '2026-05-01', value: 40 },
                { date: '2026-05-10', value: 41 },
            ],
            cpiIndexByDate: [
                { date: '2026-05-01', value: 3000 },
                { date: '2026-06-01', value: 3100 },
            ],
            depositRatePctAnnual: [{ date: '2026-01-01', value: 45 }],
        });
        expect(r).not.toBeNull();
        expect(r!.initialAssetValueTRY).toBe(40_000);
        expect(r!.currentAssetValueTRY).toBeCloseTo(45_100, 0);
        expect(r!.series.length).toBeGreaterThan(1);
        expect(r!.series[0]!.inflationAdjustedValueTRY).toBeCloseTo(40_000, 0);
    });

    it('reports missing FX for ounce without usd try', () => {
        const r = buildPreciousMetalTryComparison({
            symbol: 'XAU_USD_OZ',
            anchorDate: '2026-05-01',
            assetUnitPriceByDate: [{ date: '2026-05-01', value: 2000 }],
            usdTryByDate: [],
            cpiIndexByDate: [{ date: '2026-05-01', value: 3000 }],
            depositRatePctAnnual: [],
        });
        expect(r!.missingUsdTry).toBe(true);
    });
});
