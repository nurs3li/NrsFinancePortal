export type HistoricalPriceMatchType = 'EXACT' | 'PREVIOUS_CLOSE' | 'NEXT_AVAILABLE' | 'NOT_FOUND';

export type PositionHistoricalPriceResolve = {
    symbol: string;
    requestedDate: string;
    matchedDate?: string | null;
    price?: number | null;
    source?: string | null;
    matchType: HistoricalPriceMatchType;
    message?: string | null;
};
