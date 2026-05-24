import { describe, expect, it } from 'vitest';
import { calculateViopPositionMetrics, getViopContractCurrency } from './viopPositionMetrics';

const FX = { usdTry: 34, eurTry: 37 };

describe('calculateViopPositionMetrics', () => {
    it('LONG profit', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'F_XAUUSD1026',
                direction: 'LONG',
                entryPrice: 4902,
                currentPrice: 5000,
                contractMultiplier: 1,
                contractCount: 3,
                initialMargin: 1231,
            },
            FX,
        );
        expect(m.unrealizedPnlNative).toBeCloseTo(294, 4);
        expect(m.unrealizedPnlTry).toBeCloseTo(294 * 34, 0);
    });

    it('LONG loss', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'F_XAUUSD1026',
                direction: 'LONG',
                entryPrice: 4902,
                currentPrice: 4800,
                contractMultiplier: 1,
                contractCount: 3,
                initialMargin: 1000,
            },
            FX,
        );
        expect(m.unrealizedPnlNative).toBeCloseTo(-306, 4);
    });

    it('SHORT profit', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'F_ASELS0726',
                viopCategory: 'EQUITY',
                direction: 'SHORT',
                entryPrice: 461.5,
                currentPrice: 450,
                contractMultiplier: 1,
                contractCount: 5,
                initialMargin: 5000,
            },
            FX,
        );
        expect(m.unrealizedPnlTry).toBeCloseTo(57.5, 2);
    });

    it('SHORT loss', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'F_ASELS0726',
                direction: 'SHORT',
                entryPrice: 461.5,
                currentPrice: 470,
                contractMultiplier: 1,
                contractCount: 5,
                initialMargin: 5000,
            },
            FX,
        );
        expect(m.unrealizedPnlTry).toBeCloseTo(-42.5, 2);
    });

    it('USD exposure converts to TRY', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'XAUUSD1026',
                direction: 'LONG',
                entryPrice: 4902,
                currentPrice: 4902,
                contractMultiplier: 1,
                contractCount: 3,
                initialMargin: 1000,
            },
            FX,
        );
        expect(m.riskExposureNative).toBeCloseTo(14706, 0);
        expect(m.riskExposureTry).toBeCloseTo(14706 * 34, 0);
        expect(getViopContractCurrency('XAUUSD1026')).toBe('USD');
    });

    it('missing FX does not fake TRY totals', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'XAUUSD1026',
                direction: 'LONG',
                entryPrice: 100,
                currentPrice: 110,
                contractMultiplier: 1,
                contractCount: 1,
                initialMargin: 500,
            },
            { usdTry: null, eurTry: null },
        );
        expect(m.riskExposureTry).toBeNull();
        expect(m.missingFxRate).toBe(true);
    });

    it('leverage = exposure / margin', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'F_THYAO0726',
                direction: 'LONG',
                entryPrice: 100,
                currentPrice: 100,
                contractMultiplier: 10,
                contractCount: 2,
                initialMargin: 1000,
            },
            FX,
        );
        expect(m.riskExposureTry).toBe(2000);
        expect(m.leverage).toBeCloseTo(2, 4);
    });
});
