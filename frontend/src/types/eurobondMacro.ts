/** TCMB EVDS Genel Yönetim Eurobondları — makro özet (tekil enstrüman değil). */
export type EurobondMacroDistribution = {
    usdSharePct?: number | null;
    eurSharePct?: number | null;
    jpySharePct?: number | null;
    remainingLongSharePct?: number | null;
    remainingShortSharePct?: number | null;
};

export type EurobondMacroOverview = {
    available: boolean;
    frequencyLabel: string;
    unitLabel: string;
    sourceLabel: string;
    asOfDate?: string | null;
    marketValue?: number | null;
    bookValue?: number | null;
    totalDistribution?: number | null;
    remainingShort?: number | null;
    remainingLong?: number | null;
    originalShort?: number | null;
    originalLong?: number | null;
    usdIssues?: number | null;
    eurIssues?: number | null;
    jpyIssues?: number | null;
    distribution: EurobondMacroDistribution;
};

export type EurobondMacroSeriesPoint = {
    date: string;
    value: number;
};

export type EurobondMacroHistorySeries = {
    seriesCode: string;
    label: string;
    seriesKey: string;
    points: EurobondMacroSeriesPoint[];
};

export type EurobondMacroBatchHistory = {
    available: boolean;
    frequencyLabel: string;
    unitLabel: string;
    sourceLabel: string;
    series: Record<string, EurobondMacroHistorySeries>;
};
