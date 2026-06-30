import { describe, expect, it } from 'vitest';
import {
    calculateViopPositionMetrics,
    getViopContractCurrency,
    resolveContractMultiplier,
} from './viopPositionMetrics';

const FX = { usdTry: 34, eurTry: 37 };

describe('resolveContractMultiplier', () => {
    it('equity -> 100', () => {
        expect(resolveContractMultiplier('F_AKBNK0726', 'EQUITY', 'AKBNK')).toBe(100);
        expect(resolveContractMultiplier('F_SISE0726', 'EQUITY', 'SISE')).toBe(100);
    });
    it('index -> 10', () => {
        expect(resolveContractMultiplier('F_XU0301226', 'INDEX', 'XU030')).toBe(10);
    });
    it('fx -> 1000', () => {
        expect(resolveContractMultiplier('F_USDTRY0726', 'FX', 'USDTRY')).toBe(1000);
        expect(resolveContractMultiplier('F_EURTRY0726', 'FX', 'EURTRY')).toBe(1000);
    });
    it('XAUUSD / XAUTRY / XAUTRYM -> 1', () => {
        expect(resolveContractMultiplier('F_XAUUSD1026', 'COMMODITY', 'XAUUSD')).toBe(1);
        expect(resolveContractMultiplier('F_XAUTRY1026', 'COMMODITY', 'XAUTRY')).toBe(1);
        expect(resolveContractMultiplier('F_XAUTRYM1026', 'COMMODITY', 'XAUTRYM')).toBe(1);
    });
    it('unknown -> 1', () => {
        expect(resolveContractMultiplier('F_UNKNOWN1026')).toBe(1);
    });
});

describe('calculateViopPositionMetrics', () => {
    it('equity long example (SISE)', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'F_SISE0726',
                underlyingSymbol: 'SISE',
                viopCategory: 'EQUITY',
                direction: 'LONG',
                entryPrice: 48.21,
                currentPrice: 46.57,
                contractMultiplier: 1, // yok sayılır, resolver=100
                contractCount: 70,
                initialMargin: 600,
            },
            FX,
        );
        expect(m.riskExposureTry).toBeCloseTo(325990, 0);
        expect(m.unrealizedPnlTry).toBeCloseTo(-11480, 0);
        // toplam teminat = 600 * 70 = 42000
        expect(m.marginTry).toBeCloseTo(42000, 0);
    });

    it('equity short example (AKBNK)', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'F_AKBNK0726',
                underlyingSymbol: 'AKBNK',
                viopCategory: 'EQUITY',
                direction: 'SHORT',
                entryPrice: 67.52,
                currentPrice: 79.6,
                contractMultiplier: 1,
                contractCount: 100,
                initialMargin: 974.11,
            },
            FX,
        );
        expect(m.riskExposureTry).toBeCloseTo(796000, 0);
        expect(m.unrealizedPnlTry).toBeCloseTo(-120800, 0);
    });

    it('index short example (XU030)', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'F_XU0301226',
                underlyingSymbol: 'XU030',
                viopCategory: 'INDEX',
                direction: 'SHORT',
                entryPrice: 19755,
                currentPrice: 19008,
                contractMultiplier: 1,
                contractCount: 10,
                initialMargin: 21420,
            },
            FX,
        );
        expect(m.riskExposureTry).toBeCloseTo(1900800, 0);
        expect(m.unrealizedPnlTry).toBeCloseTo(74700, 0);
    });

    it('ounce gold (XAUUSD) converts to TRY with multiplier 1', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'F_XAUUSD1026',
                underlyingSymbol: 'XAUUSD',
                viopCategory: 'COMMODITY',
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
        expect(getViopContractCurrency('F_XAUUSD1026', 'XAUUSD')).toBe('USD');
    });

    it('leverage = exposure / total margin', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'F_THYAO0726',
                underlyingSymbol: 'THYAO',
                viopCategory: 'EQUITY',
                direction: 'LONG',
                entryPrice: 100,
                currentPrice: 100,
                contractMultiplier: 1,
                contractCount: 2,
                initialMargin: 1000,
            },
            FX,
        );
        // exposure = 100 * 100 * 2 = 20000; total margin = 1000 * 2 = 2000
        expect(m.riskExposureTry).toBeCloseTo(20000, 0);
        expect(m.marginTry).toBeCloseTo(2000, 0);
        expect(m.leverage).toBeCloseTo(10, 4);
    });

    it('missing FX does not fake TRY totals', () => {
        const m = calculateViopPositionMetrics(
            {
                symbol: 'F_XAUUSD1026',
                underlyingSymbol: 'XAUUSD',
                viopCategory: 'COMMODITY',
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
});
