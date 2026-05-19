import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { fetchInterestInflationMacroPanel, type InterestInflationMacroPanelResponse } from '../../../services/marketDataService';
import {
    DEPOSIT_TRY_CHART_LOGICAL_KEYS,
    LOAN_CHART_LOGICAL_KEYS,
    mergeCreditSeriesChart,
    mergeDepositTryChart,
    mergeIndexLevelChart,
    selectMacroSeries,
} from '../../../utils/macroPanelSeries';

function isTahvilMacroPanelNote(text: string): boolean {
    const u = text.toLowerCase();
    return (
        u.includes('tahvil') ||
        u.includes('dirty price') ||
        u.includes('listedoranpercentnotytm') ||
        u.includes('ytm değildir') ||
        u.includes('ytm degildir') ||
        u.includes('hasstructuredyielddata') ||
        u.includes('government bond')
    );
}

export function useMacroIntelligenceData() {
    const panelQ = useQuery({
        queryKey: ['market', 'macro', 'interest-inflation-panel'],
        queryFn: ({ signal }) => fetchInterestInflationMacroPanel(signal),
        staleTime: 60_000,
    });

    const panel = panelQ.data;
    const panelLoading = panelQ.isPending;
    const panelError = panelQ.isError;

    const panelInflationFromPanel = useMemo(() => {
        const s = panel?.series;
        if (!s?.length) return false;
        const c = selectMacroSeries(s, 'CPI_TR_INDEX');
        const p = selectMacroSeries(s, 'PPI_TR_INDEX');
        return (c?.observations?.length ?? 0) > 0 || (p?.observations?.length ?? 0) > 0;
    }, [panel?.series]);

    const usePanelLoans = useMemo(() => {
        const m = mergeCreditSeriesChart(panel?.series, [...LOAN_CHART_LOGICAL_KEYS]);
        return m.some(
            (row) =>
                Number.isFinite(Number(row.CONSUMER_TRY)) ||
                Number.isFinite(Number(row.VEHICLE_TRY)) ||
                Number.isFinite(Number(row.HOUSING_TRY)) ||
                Number.isFinite(Number(row.COMMERCIAL_TRY)),
        );
    }, [panel?.series]);

    const usePanelDepositsTry = useMemo(() => {
        const m = mergeDepositTryChart(panel?.series, [...DEPOSIT_TRY_CHART_LOGICAL_KEYS]);
        return m.length > 0;
    }, [panel?.series]);

    const indexChartData = useMemo(() => mergeIndexLevelChart(panel?.series), [panel?.series]);

    const depositTryChart = useMemo(
        () => mergeDepositTryChart(panel?.series, [...DEPOSIT_TRY_CHART_LOGICAL_KEYS]),
        [panel?.series],
    );

    const loanChart = useMemo(
        () => mergeCreditSeriesChart(panel?.series, [...LOAN_CHART_LOGICAL_KEYS]),
        [panel?.series],
    );

    const macroPanelNotes = useMemo(
        () => (panel?.notes ?? []).filter((n) => !isTahvilMacroPanelNote(String(n))),
        [panel?.notes],
    );

    const lastUpdated = panel?.generatedAt ? String(panel.generatedAt).slice(0, 10) : undefined;

    return {
        panel: panelError ? undefined : panel,
        panelLoading,
        panelError,
        derived: panel?.derived,
        panelInflationFromPanel: panelInflationFromPanel && !panelError,
        usePanelLoans: usePanelLoans && !panelError,
        usePanelDepositsTry: usePanelDepositsTry && !panelError,
        indexChartData,
        depositTryChart,
        loanChart,
        macroPanelNotes,
        lastUpdated,
        refetch: panelQ.refetch,
    };
}

export type MacroIntelligencePanel = InterestInflationMacroPanelResponse | undefined;
