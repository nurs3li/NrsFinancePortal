import type { AssetType } from '../../constants/OrderConstants';

export type SimulationPerformancePoint = {
    date: string;
    priceTry: number;
    cumulativeReturnPct: number;
};

export type SimDisplayCurrency = 'TRY' | 'USD';

export type SimulationResponse = {
    status?: 'READY' | 'PREPARING' | string;
    retryAfterSeconds?: number | null;
    type: string;
    symbol: string;
    buyDate: string | null;
    displayCurrency?: SimDisplayCurrency | string;
    inputAmountTry: number;
    historicalPriceTry: number;
    currentPriceTry: number;
    unitsBought: number;
    currentValueTry: number;
    pnlTry: number;
    pnlPct: number;
    buyPriceSource: 'SYSTEM_HISTORY' | 'USER_INPUT' | string;
    historicalPriceDate: string | null;
    qualityFlag?: 'EXACT' | 'EXACT_HOURLY' | 'PREVIOUS_DAY' | 'FALLBACK' | 'MISSING' | string;
    performanceSeries: SimulationPerformancePoint[];
    message: string;
    approximationNoticeCode?: string | null;
};

export type SimulationResultItem = {
    id: string;
    assetName: string;
    assetType: AssetType;
    /** Tutar ve tüm parasal alanlar bu birimde (TRY veya USD). */
    displayCurrency: SimDisplayCurrency;
    /** Backend unitsBought — pozisyon adedi (hisse, USD, GBP vb.). */
    unitsBought: number;
    initialAmount: number;
    buyPrice: number;
    buyDate: string;
    currentPrice: number;
    pnl: number;
    pnlPct: number;
    currentValue: number;
    buyPriceSource: string;
    historicalPriceDate: string;
    qualityFlag: string;
    series: SimulationPerformancePoint[];
    visible: boolean;
    message: string;
    approximationNoticeCode?: string | null;
    /** Opsiyonel senaryo adı (ör. telefon harcaması) */
    scenarioLabel?: string;
};

export type SortMode = 'LATEST' | 'PNL_DESC' | 'PNL_ASC' | 'PNL_PCT_DESC' | 'PNL_PCT_ASC' | 'NAME_ASC';
export type BuyPriceMode = 'SYSTEM' | 'MANUAL';
export type ChartMetricMode = 'RETURN_PCT' | 'VALUE_TRY' | 'PNL_TRY' | 'NORMALIZE';

export type SimulationSummaryStats = {
    count: number;
    totalInitial: number;
    totalCurrent: number;
    totalPnl: number;
    avgReturnPct: number;
    best: SimulationResultItem | null;
    worst: SimulationResultItem | null;
};

/** Karşılaştırmaya eklenecek taslak varlık (henüz simüle edilmedi) */
export type CompareDraftItem = {
    id: string;
    assetType: AssetType;
    symbol: string;
};

export type SimulationHistoryEntry = {
    id: string;
    savedAt: string;
    label: string;
    /** Kayıt anındaki simülasyon tutarı para birimi */
    amountCurrency: SimDisplayCurrency;
    items: SimulationResultItem[];
};
