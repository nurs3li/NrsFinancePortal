import { describe, expect, it } from 'vitest';
import { enrichBistInstrumentHorizons } from './bistInstrumentHorizons';

describe('enrichBistInstrumentHorizons', () => {
    it('fills week/month/year from spark when API horizons are null', () => {
        const spark = [100, 101, 102, 103, 104, 105, 106, 110];
        const row = enrichBistInstrumentHorizons(
            {
                symbol: 'THYAO',
                sparkline: spark,
                pctDay: 2,
                pctWeek: null,
                pctMonth: null,
                pctYear: null,
                changePercent: 2,
            },
            spark,
        );
        expect(row.pctDay).toBe(2);
        expect(row.pctWeek).not.toBeNull();
        expect(row.pctMonth).not.toBeNull();
        expect(row.pctYear).not.toBeNull();
        expect(Number.isFinite(row.pctWeek)).toBe(true);
    });

    it('keeps API horizons when spark is too short', () => {
        const row = enrichBistInstrumentHorizons({
            symbol: 'X',
            sparkline: [100],
            pctDay: 1,
            pctWeek: 5,
            pctMonth: 10,
            pctYear: 20,
            changePercent: 1,
        });
        expect(row.pctWeek).toBe(5);
        expect(row.pctMonth).toBe(10);
        expect(row.pctYear).toBe(20);
    });
});
