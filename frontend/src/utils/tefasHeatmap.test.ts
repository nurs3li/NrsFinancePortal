import { describe, expect, it } from 'vitest';
import { tefasHeatmapSortForRange, tefasReturnPctForChartRange } from './tefasHeatmap';

describe('tefasReturnPctForChartRange', () => {
    const row = {
        pctDay: 1,
        pctWeek: 2,
        pctMonth: 3,
        fundReturn3m: 4,
        fundReturn6m: 5,
        pctYear: 6,
        changePercent: 7,
    };

    it('maps 1D to daily return', () => {
        expect(tefasReturnPctForChartRange(row, '1D')).toBe(1);
    });

    it('maps 1W to weekly return', () => {
        expect(tefasReturnPctForChartRange(row, '1W')).toBe(2);
    });

    it('maps 1M to monthly return', () => {
        expect(tefasReturnPctForChartRange(row, '1M')).toBe(3);
    });

    it('maps 3M to 3m return (not 1M)', () => {
        expect(tefasReturnPctForChartRange(row, '3M')).toBe(4);
    });

    it('maps 6M to 6m return (not 1M)', () => {
        expect(tefasReturnPctForChartRange(row, '6M')).toBe(5);
    });

    it('maps 1Y to yearly return', () => {
        expect(tefasReturnPctForChartRange(row, '1Y')).toBe(6);
    });

    it('falls back when horizon missing', () => {
        expect(tefasReturnPctForChartRange({ changePercent: 9 }, '1D')).toBe(9);
        expect(tefasReturnPctForChartRange({ pctMonth: 3 }, '3M')).toBe(3);
    });
});

describe('tefasHeatmapSortForRange', () => {
    it('uses return1d for 1D and return6m for 6M', () => {
        expect(tefasHeatmapSortForRange('1D')).toBe('return1d');
        expect(tefasHeatmapSortForRange('6M')).toBe('return6m');
    });
});
