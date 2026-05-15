import { financeClient, marketClient } from '../api/client';

export * from './bistEquityApi';
import type { LatestPriceRow } from '../components/market/marketTypes';
import type { AssetClass, AssetType } from '../constants/OrderConstants';
import { classifyDebtInstrument, classifyViopContract } from '../constants/OrderConstants';
import { getBistLatest } from './bistEquityApi';

type MarketOverview = {
    doviz?: Record<string, { buyPrice?: number; sellPrice?: number; source?: string }>;
    metals?: Record<string, { buyPrice?: number; source?: string }>;
    crypto?: Record<string, { buyPrice?: number; source?: string }>;
    funds?: Record<string, { buyPrice?: number; source?: string }>;
    /** Hisse: ABD/BIST toggle için `LatestPriceRow` metadata (opsiyonel) */
    stocks?: Record<string, LatestPriceRow>;
    timestamp?: string;
};

type ViopContract = { contractCode: string; underlying: string; expiry: string; type: string };
type DebtInstrument = { isin: string; name: string; issuer: string; maturityDate: string };

export async function fetchSpotSymbolsByAssetClass(assetClass: AssetClass): Promise<string[]> {
    const overviewRes = await financeClient.get<MarketOverview>('/api/market/overview');
    const overview = ((overviewRes.data as any)?.data ?? overviewRes.data) as MarketOverview;
    const map = assetClass === 'SPOT_EQUITY'
        ? overview?.stocks
        : assetClass === 'SPOT_CRYPTO'
            ? overview?.crypto
            : assetClass === 'SPOT_FX'
                ? overview?.doviz
                : overview?.metals;
    return Object.keys(map ?? {}).filter((k) => (map as any)?.[k] != null);
}

export async function fetchViopSymbolsByClass(assetClass: AssetClass): Promise<string[]> {
    const res = await marketClient.get<ViopContract[]>('/api/market/viop/contracts');
    const rows = (res.data ?? []).map((x) => String(x.contractCode ?? '').toUpperCase()).filter(Boolean);
    return rows.filter((symbol) => classifyViopContract(symbol) === assetClass);
}

export async function fetchDebtSymbolsByClass(assetClass: AssetClass): Promise<string[]> {
    const res = await marketClient.get<DebtInstrument[]>('/api/market/debt/catalog');
    const rows = res.data ?? [];
    return rows
        .filter((x) => classifyDebtInstrument(x.name, x.issuer, x.isin) === assetClass)
        .map((x) => String(x.isin ?? '').toUpperCase())
        .filter(Boolean);
}

/** Hisse latest; BIST için `marketRegion=TR` veya `exchange=BIST` query eklenebilir. */
export function equityLatestPath(marketRegion?: string | null, exchange?: string | null): string {
    const q = new URLSearchParams();
    if (marketRegion) q.set('marketRegion', marketRegion);
    if (exchange) q.set('exchange', exchange);
    const s = q.toString();
    return s ? `/api/market/equity/latest?${s}` : '/api/market/equity/latest';
}

export async function fetchSimulationSymbolsByType(type: AssetType): Promise<string[]> {
    if (type === 'BIST') {
        const rows = await getBistLatest();
        return rows
            .map((r) => String(r.symbol ?? '').trim().toUpperCase())
            .filter(Boolean)
            .sort((a, b) => a.localeCompare(b, 'tr-TR'));
    }
    const endpoint =
        type === 'FX'
            ? '/api/market/doviz/latest'
            : type === 'CRYPTO'
                ? '/api/market/crypto/latest'
                : type === 'METAL'
                    ? '/api/market/metals/latest'
                    : type === 'FUND'
                        ? '/api/market/funds/latest'
                        : '/api/market/equity/latest';
    const res = await marketClient.get<Record<string, unknown>>(endpoint);
    const rows = Object.entries(res.data ?? {});
    return rows
        .filter(([, value]) => {
            if (!value || typeof value !== 'object') return false;
            const row = value as Record<string, unknown>;
            if (String(row.status ?? '').toUpperCase() === 'NO_DATA') return false;
            const buy = Number(row.buyPrice ?? 0);
            const sell = Number(row.sellPrice ?? 0);
            return buy > 0 || sell > 0;
        })
        .map(([key]) => String(key ?? '').toUpperCase())
        .filter(Boolean)
        .sort((a, b) => a.localeCompare(b, 'tr-TR'));
}

/** GET /api/market/macro/policy-rate-tr — aylık TCMB politika faizi (EVDS). */
export type PolicyRateTrMacroResponse = {
    logicalIndicatorCode: string;
    seriesCode: string;
    valuePercent: number;
    latestDate: string;
    frequency: string;
    unit: string;
    displayLabel: string;
};

export type DepositRateLatestRow = {
    seriesCode: string;
    logicalIndicatorCode?: string | null;
    category: string;
    source: string;
    frequency: string;
    unit: string;
    flowType: string;
    currency: string;
    term: string;
    observationDate: string;
    ratePercent: number;
};

export async function fetchDepositRatesLatest(signal?: AbortSignal): Promise<DepositRateLatestRow[] | null> {
    try {
        const { data } = await marketClient.get<DepositRateLatestRow[]>('/api/market/macro/deposit-rates/latest', {
            signal,
        });
        return Array.isArray(data) ? data : null;
    } catch {
        return null;
    }
}

export async function fetchPolicyRateTrMacro(signal?: AbortSignal): Promise<PolicyRateTrMacroResponse | null> {
    try {
        const { data } = await marketClient.get<PolicyRateTrMacroResponse>('/api/market/macro/policy-rate-tr', {
            signal,
        });
        return data ?? null;
    } catch {
        return null;
    }
}

/** GET /api/market/fx/effective-rates — TCMB/EVDS günlük döviz + efektif gösterge kurları (mevcut doviz/latest’ten ayrı; heatmap’i değiştirmez). */
export type FxEffectiveRateRow = {
    currency: string;
    date: string;
    fxBuying: number | null;
    fxSelling: number | null;
    cashBuying: number | null;
    cashSelling: number | null;
    fxSpread: number | null;
    cashSpread: number | null;
    cashVsFxBuyingDiff: number | null;
    cashVsFxSellingDiff: number | null;
};

export type FxEffectiveRateResponse = {
    generatedAt: string;
    source: string;
    frequency: string;
    rates: FxEffectiveRateRow[];
    notes: string[];
};

export async function fetchFxEffectiveRates(signal?: AbortSignal): Promise<FxEffectiveRateResponse | null> {
    try {
        const { data } = await marketClient.get<FxEffectiveRateResponse>('/api/market/fx/effective-rates', { signal });
        return data ?? null;
    } catch {
        return null;
    }
}

/** GET /api/market/macro/interest-inflation-panel — normalize makro snapshot (EVDS + tahvil özet). */
export type NormalizedMacroObservation = { date: string; value: number };
export type NormalizedMacroSeries = {
    code: string;
    label: string;
    category: string;
    frequency: string;
    unit: string;
    source: string;
    observations: NormalizedMacroObservation[];
    /** Backend EVDS mantıksal anahtar (örn. CPI_TR_INDEX, LOAN_RATE_CONSUMER_TRY_WEEKLY). */
    logicalKey?: string | null;
    /** Döviz mevduat serileri için USD / EUR. */
    currency?: string | null;
    /** Vade: 1M, 3M, 6M, 1Y. */
    tenor?: string | null;
    /** Örn. FLOW — EVDS akım yüzdesi. */
    dataType?: string | null;
};
export type MacroPanelDerivedMetrics = {
    cpiMoM: number | null;
    cpiYoY: number | null;
    ppiMoM: number | null;
    ppiYoY: number | null;
    realPolicyRate: number | null;
    realDepositRate: number | null;
    consumerLoanMinusPolicyRate: number | null;
    consumerLoanMinusDepositRate: number | null;
};
export type MacroPanelBondSummary = {
    isin: string;
    indicativeDirtyPriceTry: number | null;
    listedOranPercentNotYtm: number | null;
    daysToMaturity: number | null;
    category: string;
    dirtyPriceUnit: string;
    isYield: boolean;
    metricType: string;
    hasStructuredYieldData: boolean;
};
export type InterestInflationMacroPanelResponse = {
    generatedAt: string;
    series: NormalizedMacroSeries[];
    derived: MacroPanelDerivedMetrics;
    governmentBonds: MacroPanelBondSummary[];
    notes: string[];
};

export async function fetchInterestInflationMacroPanel(
    signal?: AbortSignal
): Promise<InterestInflationMacroPanelResponse> {
    const { data } = await marketClient.get<InterestInflationMacroPanelResponse>(
        '/api/market/macro/interest-inflation-panel',
        { signal }
    );
    if (!data) {
        throw new Error('interest-inflation-panel: empty response');
    }
    return data;
}

/** GET /api/market/macro/inflation/latest — TÜFE + Yİ-ÜFE (EVDS, aylık endeks türevli %). */
export type InflationIndicatorSnapshot = {
    category: string;
    indicatorType: string;
    source: string;
    frequency: string;
    unit: string;
    baseYear: number | null;
    country: string;
    seriesCode: string;
    indexMonth: string;
    indexValue: number;
    monthlyChangePercent: number | null;
    annualChangePercent: number | null;
    note: string | null;
};

export type InflationLatestResponse = {
    cpi: InflationIndicatorSnapshot | null;
    ppi: InflationIndicatorSnapshot | null;
    methodologyNote: string;
    sourceNote?: string | null;
};

export type InflationCompareRow = {
    month: string;
    cpiMonthlyChangePercent: number | null;
    cpiAnnualChangePercent: number | null;
    ppiMonthlyChangePercent: number | null;
    ppiAnnualChangePercent: number | null;
};

export type InflationCompareResponse = {
    rows: InflationCompareRow[];
};

export async function fetchInflationLatest(signal?: AbortSignal): Promise<InflationLatestResponse | null> {
    try {
        const { data } = await marketClient.get<InflationLatestResponse>('/api/market/macro/inflation/latest', {
            signal,
        });
        return data ?? null;
    } catch {
        return null;
    }
}

export async function fetchInflationCompare(
    fromYm: string,
    toYm: string,
    signal?: AbortSignal
): Promise<InflationCompareResponse | null> {
    try {
        const { data } = await marketClient.get<InflationCompareResponse>('/api/market/macro/inflation/compare', {
            params: { from: fromYm, to: toYm },
            signal,
        });
        return data ?? null;
    } catch {
        return null;
    }
}
