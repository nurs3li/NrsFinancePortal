import { describe, expect, it } from 'vitest';
import { tefasReturnPctForChartRange } from './tefasHeatmap';
import type { ChartRangeId } from '../components/market/heatmapRange';

describe('tefasReturnPctForChartRange', () => {
    const row = {
        pctDay: 2,
        pctWeek: 5,
        pctMonth: 8,
        pctYear: 12,
        changePercent: 15,
    };

    it('maps 1M to 1m return', () => {
        expect(tefasReturnPctForChartRange(row, '1M')).toBe(2);
    });

    it('maps 1Y to 1y return', () => {
        expect(tefasReturnPctForChartRange(row, '1Y')).toBe(12);
    });

    it('falls back for unknown range', () => {
        expect(tefasReturnPctForChartRange(row, '6M' as ChartRangeId)).toBe(8);
    });
});
