import { describe, expect, it } from 'vitest';
import { bumpSummaryForView } from './manualPortfolioCachePatch';
import type { ManualPortfolioView, ManualSummary } from '../types/manualPortfolio';

const emptySummary = (): ManualSummary => ({
    totalPositions: 0,
    openPositions: 0,
    soldPositions: 0,
    totalInvested: 0,
    currentOpenValue: 0,
    realizedProfit: 0,
    unrealizedProfit: 0,
    holdValueTodayForSold: 0,
    missedProfit: 0,
    totalNominalProfit: 0,
    totalNominalReturnPct: 0,
    bestPositionSymbol: null,
    bestPositionReturnPct: null,
    biggestMissedOpportunitySymbol: null,
    biggestMissedProfit: null,
});

const openView = (overrides: Partial<ManualPortfolioView> = {}): ManualPortfolioView =>
    ({
        id: 1,
        type: 'BIST',
        symbol: 'THYAO',
        quantity: 10,
        buyDate: '2024-06-01',
        buyPrice: 100,
        status: 'OPEN',
        buyCost: 1000,
        currentValue: 1100,
        unrealizedProfit: 100,
        totalProfit: 100,
        ...overrides,
    }) as ManualPortfolioView;

describe('bumpSummaryForView', () => {
    it('adds open position totals on add', () => {
        const next = bumpSummaryForView(emptySummary(), openView(), 'add');
        expect(next.totalPositions).toBe(1);
        expect(next.openPositions).toBe(1);
        expect(next.totalInvested).toBe(1000);
        expect(next.currentOpenValue).toBe(1100);
        expect(next.unrealizedProfit).toBe(100);
        expect(next.totalNominalProfit).toBe(100);
    });

    it('reverts totals on remove', () => {
        const seeded = bumpSummaryForView(emptySummary(), openView(), 'add');
        const next = bumpSummaryForView(seeded, openView(), 'remove');
        expect(next.totalPositions).toBe(0);
        expect(next.totalInvested).toBe(0);
        expect(next.currentOpenValue).toBe(0);
    });
});
