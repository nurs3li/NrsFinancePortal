import { describe, expect, it } from 'vitest';
import {
    formatViopUnderlyingDisplay,
    parseViopContractSuffix,
    resolveViopExpiry,
} from './viopContractMeta';

describe('parseViopContractSuffix', () => {
    it('parses index and equity symbols', () => {
        expect(parseViopContractSuffix('F_XU0301226')).toEqual({
            underlying: 'XU030',
            month: 12,
            year: 2026,
        });
        expect(parseViopContractSuffix('EREGL0726')).toEqual({
            underlying: 'EREGL',
            month: 7,
            year: 2026,
        });
        expect(parseViopContractSuffix('USDTRY1226')).toEqual({
            underlying: 'USDTRY',
            month: 12,
            year: 2026,
        });
    });
});

describe('resolveViopExpiry', () => {
    it('shows Turkish month label and short form', () => {
        const r = resolveViopExpiry('F_XU0301226', 'tr-TR');
        expect(r?.displayShort).toBe('12/2026');
        expect(r?.displayLong).toBe('Aralık 2026');
        expect(r?.expiryDate).toMatch(/^2026-12-\d{2}$/);
    });
});

describe('formatViopUnderlyingDisplay', () => {
    it('maps known underlyings', () => {
        expect(formatViopUnderlyingDisplay('F_XU0301226')).toBe('XU030 / BIST 30');
        expect(formatViopUnderlyingDisplay('F_EREGL0726')).toBe('EREGL Pay Vadeli');
        expect(formatViopUnderlyingDisplay('F_USDTRY1226')).toBe('USD/TRY');
    });
});
