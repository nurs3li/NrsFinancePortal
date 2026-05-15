import { describe, expect, it } from 'vitest';
import { formatBorrowingCostPercent } from './macroRatesApi';

describe('formatBorrowingCostPercent', () => {
    it('formats finite percent without scaling down', () => {
        expect(formatBorrowingCostPercent(61.55, 'en-US')).toBe('61.55%');
    });

    it('returns em dash for null', () => {
        expect(formatBorrowingCostPercent(null)).toBe('—');
    });
});
