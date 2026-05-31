import type { QueryClient } from '@tanstack/react-query';

import { manualPortfolioKeys } from '../queries/manualPortfolioKeys';

import type { ManualPortfolioPage, ManualPortfolioView, ManualSummary } from '../types/manualPortfolio';



const MANUAL_PORTFOLIO_PAGE_STORAGE = 'nrs.manual-portfolio.page.v1';



type SummaryMode = 'add' | 'remove' | 'update';



function nz(n: number | null | undefined): number {

    return Number.isFinite(Number(n)) ? Number(n) : 0;

}



function viewContribution(view: ManualPortfolioView) {

    const isOpen = String(view.status).toUpperCase() === 'OPEN';

    const buyCost = nz(view.buyCost ?? view.nominalCost);

    const currentValue = nz(view.currentValue);

    const unrealized = nz(view.unrealizedProfit);

    const realized = nz(view.realizedProfit ?? view.nominalProfit);

    const missed = nz(view.missedProfit);

    const holdToday = nz(view.holdValueToday);

    const nominalDelta = nz(view.totalProfit ?? (isOpen ? unrealized : realized));

    return { isOpen, buyCost, currentValue, unrealized, realized, missed, holdToday, nominalDelta };

}



export function recomputeSummaryFromViews(positions: ManualPortfolioView[]): ManualSummary {

    let openPositions = 0;

    let soldPositions = 0;

    let totalInvested = 0;

    let currentOpenValue = 0;

    let unrealizedProfit = 0;

    let realizedProfit = 0;

    let holdValueTodayForSold = 0;

    let missedProfit = 0;

    let totalNominalProfit = 0;

    let bestPositionSymbol: string | null = null;

    let bestPositionReturnPct: number | null = null;

    let biggestMissedOpportunitySymbol: string | null = null;

    let biggestMissedProfit: number | null = null;



    for (const view of positions) {

        const c = viewContribution(view);

        totalInvested += c.buyCost;

        totalNominalProfit += c.nominalDelta;

        missedProfit += c.missed;

        if (c.isOpen) {

            openPositions += 1;

            currentOpenValue += c.currentValue;

            unrealizedProfit += c.unrealized;

            const ret = nz(view.unrealizedReturnPct ?? view.totalReturnPct);

            if (bestPositionReturnPct == null || ret > bestPositionReturnPct) {

                bestPositionReturnPct = ret;

                bestPositionSymbol = view.symbol;

            }

        } else {

            soldPositions += 1;

            realizedProfit += c.realized;

            holdValueTodayForSold += c.holdToday;

            if (biggestMissedProfit == null || c.missed > biggestMissedProfit) {

                biggestMissedProfit = c.missed;

                biggestMissedOpportunitySymbol = view.symbol;

            }

        }

    }



    const totalNominalReturnPct =

        totalInvested > 0 ? (totalNominalProfit / totalInvested) * 100 : 0;



    return {

        totalPositions: positions.length,

        openPositions,

        soldPositions,

        totalInvested,

        currentOpenValue,

        realizedProfit,

        unrealizedProfit,

        holdValueTodayForSold,

        missedProfit,

        totalNominalProfit,

        totalNominalReturnPct,

        bestPositionSymbol,

        bestPositionReturnPct,

        biggestMissedOpportunitySymbol,

        biggestMissedProfit,

    };

}



export function bumpSummaryForView(

    prev: ManualSummary,

    view: ManualPortfolioView,

    mode: 'add' | 'remove',

): ManualSummary {

    const sign = mode === 'add' ? 1 : -1;

    const c = viewContribution(view);

    return {

        ...prev,

        totalPositions: prev.totalPositions + sign,

        openPositions: prev.openPositions + sign * (c.isOpen ? 1 : 0),

        soldPositions: prev.soldPositions + sign * (c.isOpen ? 0 : 1),

        totalInvested: prev.totalInvested + sign * c.buyCost,

        currentOpenValue: prev.currentOpenValue + sign * (c.isOpen ? c.currentValue : 0),

        unrealizedProfit: prev.unrealizedProfit + sign * (c.isOpen ? c.unrealized : 0),

        realizedProfit: prev.realizedProfit + sign * (c.isOpen ? 0 : c.realized),

        holdValueTodayForSold: prev.holdValueTodayForSold + sign * (c.isOpen ? 0 : c.holdToday),

        missedProfit: prev.missedProfit + sign * c.missed,

        totalNominalProfit: prev.totalNominalProfit + sign * c.nominalDelta,

    };

}



function readSummaryFromCaches(qc: QueryClient): ManualSummary | undefined {

    return (

        qc.getQueryData<ManualSummary>(manualPortfolioKeys.summary()) ??

        qc.getQueryData<ManualPortfolioPage>(manualPortfolioKeys.page())?.summary

    );

}



function readPositionsFromCaches(qc: QueryClient): ManualPortfolioView[] {

    return (

        qc.getQueryData<ManualPortfolioView[]>(manualPortfolioKeys.positions()) ??

        qc.getQueryData<ManualPortfolioPage>(manualPortfolioKeys.page())?.positions ??

        []

    );

}



function writeSummaryToCaches(qc: QueryClient, summary: ManualSummary) {

    qc.setQueryData(manualPortfolioKeys.summary(), summary);

    qc.setQueryData<ManualPortfolioPage>(manualPortfolioKeys.page(), (old) =>

        old ? { ...old, summary } : old,

    );

}



export function patchPositionsInAllCaches(

    qc: QueryClient,

    updater: (positions: ManualPortfolioView[]) => ManualPortfolioView[],

) {

    qc.setQueryData<ManualPortfolioView[]>(manualPortfolioKeys.positions(), (old) => updater(old ?? []));

    qc.setQueryData<ManualPortfolioPage>(manualPortfolioKeys.page(), (old) =>

        old ? { ...old, positions: updater(old.positions ?? []) } : old,

    );

}



export function bumpSummaryInAllCaches(

    qc: QueryClient,

    view: ManualPortfolioView,

    mode: SummaryMode,

    previousView?: ManualPortfolioView,

) {

    const prev = readSummaryFromCaches(qc);

    if (!prev) return;



    let next = prev;

    if (mode === 'update' && previousView) {

        next = bumpSummaryForView(next, previousView, 'remove');

        next = bumpSummaryForView(next, view, 'add');

    } else if (mode === 'add') {

        next = bumpSummaryForView(next, view, 'add');

    } else if (mode === 'remove') {

        next = bumpSummaryForView(next, view, 'remove');

    }

    writeSummaryToCaches(qc, recomputeSummaryFromViews(readPositionsFromCaches(qc)));

}



function syncSummaryFromPositions(qc: QueryClient) {

    writeSummaryToCaches(qc, recomputeSummaryFromViews(readPositionsFromCaches(qc)));

}



export function syncStoredManualPortfolioPage(qc: QueryClient) {

    const page = qc.getQueryData<ManualPortfolioPage>(manualPortfolioKeys.page());

    if (!page?.positions || !page.summary) return;

    try {

        sessionStorage.setItem(

            MANUAL_PORTFOLIO_PAGE_STORAGE,

            JSON.stringify({

                positions: page.positions,

                summary: page.summary,

                meta: page.meta,

            }),

        );

    } catch {

        /* ignore quota */

    }

}



function markChartWarmupPending(qc: QueryClient) {

    qc.setQueryData<ManualPortfolioPage>(manualPortfolioKeys.page(), (old) =>

        old

            ? {

                  ...old,

                  meta: { ...old.meta, warmStatus: 'PENDING' },

              }

            : old,

    );

}



export function applyManualPortfolioMutation(

    qc: QueryClient,

    view: ManualPortfolioView,

    mode: SummaryMode,

    previousView?: ManualPortfolioView,

) {

    if (mode === 'remove') {

        patchPositionsInAllCaches(qc, (list) => list.filter((p) => p.id !== view.id));

    } else {

        patchPositionsInAllCaches(qc, (list) => {

            const idx = list.findIndex((p) => p.id === view.id);

            if (idx >= 0) {

                const next = [...list];

                next[idx] = view;

                return next;

            }

            return [...list, view];

        });

    }



    if (mode === 'update' && previousView) {

        bumpSummaryInAllCaches(qc, view, mode, previousView);

    } else if (mode === 'add' && readSummaryFromCaches(qc)) {

        bumpSummaryInAllCaches(qc, view, mode, previousView);

    } else if (mode === 'remove' && readSummaryFromCaches(qc)) {

        bumpSummaryInAllCaches(qc, view, mode, previousView);

    } else {

        syncSummaryFromPositions(qc);

    }



    markChartWarmupPending(qc);

    syncStoredManualPortfolioPage(qc);

}



export function applyManualPortfolioDeleteById(qc: QueryClient, id: number) {

    patchPositionsInAllCaches(qc, (list) => list.filter((p) => p.id !== id));

    syncSummaryFromPositions(qc);

    markChartWarmupPending(qc);

    syncStoredManualPortfolioPage(qc);

}



/** PENDING page/me cevabını optimistic cache ile birleştirir (stale snapshot koruması). */

export function mergePendingPageWithOptimisticCache(

    serverPage: ManualPortfolioPage,

    optimisticPage: ManualPortfolioPage | undefined,

): ManualPortfolioPage {

    if (serverPage.meta?.warmStatus === 'READY' || !optimisticPage) {

        return serverPage;

    }

    if (optimisticPage.meta?.warmStatus !== 'PENDING') {

        return serverPage;

    }



    const optIds = new Set(optimisticPage.positions.map((p) => p.id));

    const serverStale =

        serverPage.positions.length !== optimisticPage.positions.length ||

        serverPage.positions.some((p) => !optIds.has(p.id));



    if (!serverStale) {

        return serverPage;

    }



    return {

        ...serverPage,

        positions: optimisticPage.positions,

        summary: optimisticPage.summary,

    };

}


