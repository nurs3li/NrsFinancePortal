import { describe, expect, it } from 'vitest';
import type { SimulationResultItem } from './types';
import { buildSimulationChartData } from './utils';

function makeResult(partial: Partial<SimulationResultItem> & Pick<SimulationResultItem, 'id' | 'assetName' | 'assetType'>): SimulationResultItem {
    return {
        id: partial.id,
        assetName: partial.assetName,
        assetType: partial.assetType,
        displayCurrency: partial.displayCurrency ?? 'TRY',
        unitsBought: partial.unitsBought ?? 1,
        initialAmount: partial.initialAmount ?? 100,
        buyPrice: partial.buyPrice ?? 100,
        buyDate: partial.buyDate ?? '2026-03-06',
        currentPrice: partial.currentPrice ?? 110,
        pnl: partial.pnl ?? 10,
        pnlPct: partial.pnlPct ?? 10,
        currentValue: partial.currentValue ?? 110,
        buyPriceSource: partial.buyPriceSource ?? 'SYSTEM_HISTORY',
        historicalPriceDate: partial.historicalPriceDate ?? '2026-03-06',
        qualityFlag: partial.qualityFlag ?? 'EXACT',
        series: partial.series ?? [],
        visible: partial.visible ?? true,
        message: partial.message ?? '',
        approximationNoticeCode: partial.approximationNoticeCode ?? null,
        scenarioLabel: partial.scenarioLabel,
    };
}

describe('buildSimulationChartData', () => {
    it('carries the last known value across non-trading days after the first point', () => {
        const main = makeResult({
            id: 'main',
            assetName: 'ADAUSDT',
            assetType: 'CRYPTO',
            series: [
                { date: '2026-03-06', priceTry: 100, cumulativeReturnPct: 0 },
                { date: '2026-03-07', priceTry: 101, cumulativeReturnPct: 1 },
                { date: '2026-03-08', priceTry: 102, cumulativeReturnPct: 2 },
                { date: '2026-03-09', priceTry: 103, cumulativeReturnPct: 3 },
            ],
        });
        const compare = makeResult({
            id: 'cmp',
            assetName: 'EEM',
            assetType: 'FUND',
            series: [
                { date: '2026-03-06', priceTry: 100, cumulativeReturnPct: 0 },
                { date: '2026-03-09', priceTry: 104, cumulativeReturnPct: 4 },
            ],
        });

        const rows = buildSimulationChartData([main, compare], 'RETURN_PCT');

        expect(rows).toEqual([
            expect.objectContaining({ date: '2026-03-06', 'CRYPTO-ADAUSDT-main': 0, 'FUND-EEM-cmp': 0 }),
            expect.objectContaining({ date: '2026-03-07', 'CRYPTO-ADAUSDT-main': 1, 'FUND-EEM-cmp': 0 }),
            expect.objectContaining({ date: '2026-03-08', 'CRYPTO-ADAUSDT-main': 2, 'FUND-EEM-cmp': 0 }),
            expect.objectContaining({ date: '2026-03-09', 'CRYPTO-ADAUSDT-main': 3, 'FUND-EEM-cmp': 4 }),
        ]);
    });

    it('does not backfill dates before a series starts', () => {
        const main = makeResult({
            id: 'main',
            assetName: 'ADAUSDT',
            assetType: 'CRYPTO',
            series: [
                { date: '2026-03-06', priceTry: 100, cumulativeReturnPct: 0 },
                { date: '2026-03-07', priceTry: 101, cumulativeReturnPct: 1 },
            ],
        });
        const compare = makeResult({
            id: 'late',
            assetName: 'AAPL',
            assetType: 'STOCK',
            series: [{ date: '2026-03-07', priceTry: 120, cumulativeReturnPct: 0 }],
        });

        const rows = buildSimulationChartData([main, compare], 'RETURN_PCT');

        expect(rows[0]).toEqual(expect.objectContaining({ date: '2026-03-06', 'CRYPTO-ADAUSDT-main': 0 }));
        expect(rows[0]).not.toHaveProperty('STOCK-AAPL-late');
        expect(rows[1]).toEqual(expect.objectContaining({ date: '2026-03-07', 'STOCK-AAPL-late': 0 }));
    });
});
