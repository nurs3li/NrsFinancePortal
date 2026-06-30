import { describe, expect, it } from 'vitest';
import {
    isPlausibleBondMarketPrice,
    MIN_PLAUSIBLE_BOND_MARKET_PRICE,
    sanitizeBondMarketPrice,
} from './bondMarketPrice';

describe('bondMarketPrice', () => {
    it('accepts typical DİBS prices per 100 nominal', () => {
        expect(isPlausibleBondMarketPrice(95)).toBe(true);
        expect(isPlausibleBondMarketPrice(100)).toBe(true);
        expect(isPlausibleBondMarketPrice(107.5)).toBe(true);
        expect(isPlausibleBondMarketPrice(MIN_PLAUSIBLE_BOND_MARKET_PRICE)).toBe(true);
    });

    it('rejects EVDS-like low values (coupon/yield confusion)', () => {
        expect(isPlausibleBondMarketPrice(7.29)).toBe(false);
        expect(isPlausibleBondMarketPrice(18.31)).toBe(false);
        expect(isPlausibleBondMarketPrice(49.99)).toBe(false);
    });

    it('sanitizeBondMarketPrice returns 0 for implausible values', () => {
        expect(sanitizeBondMarketPrice(102)).toBe(102);
        expect(sanitizeBondMarketPrice(7.22)).toBe(0);
        expect(sanitizeBondMarketPrice(null)).toBe(0);
    });
});
