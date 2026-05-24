import { getPreciousMetalDisplayMeta, isUsdPerOunceMetalSymbol } from '../constants/preciousMetalsUsd';

function normMetalSymbol(symbol: string): string {
    return String(symbol ?? '')
        .trim()
        .replace(/\s+/g, '')
        .toUpperCase();
}
import {
    buildPurchasingPowerSeries,
    computePurchasingPowerSnapshot,
    lastValueAtOrBefore,
    type DateValue,
    type PurchasingPowerPoint,
} from './marketPurchasingPower';

export type PreciousMetalQuoteCurrency = 'TRY' | 'USD';

export type PreciousMetalComparisonUnit = {
    unitLabel: string;
    unitLabelShort: string;
    assetInTry: boolean;
    quoteCurrency: PreciousMetalQuoteCurrency;
};

export type AssetInflationDepositComparisonPoint = {
    date: string;
    assetValueTRY: number;
    inflationAdjustedValueTRY: number;
    depositValueTRY: number;
};

export type AssetInflationDepositComparison = {
    selectedDate: string;
    currentDate: string;
    unit: PreciousMetalComparisonUnit;
    initialAssetValueTRY: number;
    currentAssetValueTRY: number;
    nominalPnL: number;
    inflationRequiredValueTRY: number;
    realPnLDiff: number;
    depositScenarioValueTRY: number;
    depositRelativePnL: number;
    assetVsDepositDiff: number;
    series: AssetInflationDepositComparisonPoint[];
    missingUsdTry: boolean;
    missingCpi: boolean;
    depositUsesHistoricalRates: boolean;
};

export function resolvePreciousMetalComparisonUnit(symbol: string): PreciousMetalComparisonUnit {
    const key = normMetalSymbol(symbol);
    if (key === 'XAU_TRY' || key === 'ALTIN_TRY') {
        return {
            unitLabel: '1 gram',
            unitLabelShort: 'gram',
            assetInTry: true,
            quoteCurrency: 'TRY',
        };
    }
    if (isUsdPerOunceMetalSymbol(key)) {
        return {
            unitLabel: '1 ons',
            unitLabelShort: 'ons',
            assetInTry: false,
            quoteCurrency: 'USD',
        };
    }
    const meta = getPreciousMetalDisplayMeta(key);
    const inTry = meta?.unitLabel?.includes('TRY') ?? false;
    return {
        unitLabel: inTry ? '1 gram' : '1 ons',
        unitLabelShort: inTry ? 'gram' : 'ons',
        assetInTry: inTry,
        quoteCurrency: inTry ? 'TRY' : 'USD',
    };
}

export type BuildPreciousMetalTryComparisonParams = {
    symbol: string;
    anchorDate: string;
    assetUnitPriceByDate: DateValue[];
    usdTryByDate: DateValue[];
    cpiIndexByDate: DateValue[];
    depositRatePctAnnual: DateValue[];
};

/**
 * Kıymetli madenler: gram (TRY) veya ons (USD×USDTRY) için tek kaynak karşılaştırma.
 */
export function buildPreciousMetalTryComparison(
    params: BuildPreciousMetalTryComparisonParams,
): AssetInflationDepositComparison | null {
    const anchor = params.anchorDate.slice(0, 10);
    if (!anchor) return null;

    const unit = resolvePreciousMetalComparisonUnit(params.symbol);

    if (!unit.assetInTry) {
        const fxAtAnchor = lastValueAtOrBefore(params.usdTryByDate, anchor);
        if (fxAtAnchor == null) {
            return {
                selectedDate: anchor,
                currentDate: anchor,
                unit,
                initialAssetValueTRY: 0,
                currentAssetValueTRY: 0,
                nominalPnL: 0,
                inflationRequiredValueTRY: 0,
                realPnLDiff: 0,
                depositScenarioValueTRY: 0,
                depositRelativePnL: 0,
                assetVsDepositDiff: 0,
                series: [],
                missingUsdTry: true,
                missingCpi: false,
                depositUsesHistoricalRates: false,
            };
        }
    }

    const initialTry = unit.assetInTry
        ? lastValueAtOrBefore(params.assetUnitPriceByDate, anchor)
        : (() => {
              const u = lastValueAtOrBefore(params.assetUnitPriceByDate, anchor);
              const r = lastValueAtOrBefore(params.usdTryByDate, anchor);
              return u != null && r != null ? u * r : null;
          })();

    if (initialTry == null || !(initialTry > 0)) return null;

    const hasCpi = params.cpiIndexByDate.some((o) => o.value > 0);
    if (!hasCpi) {
        return {
            selectedDate: anchor,
            currentDate: anchor,
            unit,
            initialAssetValueTRY: 0,
            currentAssetValueTRY: 0,
            nominalPnL: 0,
            inflationRequiredValueTRY: 0,
            realPnLDiff: 0,
            depositScenarioValueTRY: 0,
            depositRelativePnL: 0,
            assetVsDepositDiff: 0,
            series: [],
            missingUsdTry: false,
            missingCpi: true,
            depositUsesHistoricalRates: params.depositRatePctAnnual.length > 0,
        };
    }

    const rawSeries: PurchasingPowerPoint[] = buildPurchasingPowerSeries({
        anchorDate: anchor,
        lotCostTry: initialTry,
        assetUnitPriceByDate: params.assetUnitPriceByDate,
        assetInTry: unit.assetInTry,
        usdTryByDate: params.usdTryByDate,
        cpiIndexByDate: params.cpiIndexByDate,
        depositRatePctAnnual: params.depositRatePctAnnual,
    });

    if (!rawSeries.length) return null;

    const snap = computePurchasingPowerSnapshot(rawSeries, initialTry, anchor);
    if (!snap) return null;

    const series: AssetInflationDepositComparisonPoint[] = rawSeries.map((p) => ({
        date: p.date,
        assetValueTRY: p.assetTry,
        inflationAdjustedValueTRY: p.inflationHurdleTry,
        depositValueTRY: p.depositTry,
    }));

    const lastDate = series[series.length - 1]!.date;
    const nominalPnL = snap.assetTryToday - snap.lotCostTry;
    const realPnLDiff = snap.assetTryToday - snap.inflationBreakEvenToday;

    return {
        selectedDate: anchor,
        currentDate: lastDate,
        unit,
        initialAssetValueTRY: snap.lotCostTry,
        currentAssetValueTRY: snap.assetTryToday,
        nominalPnL,
        inflationRequiredValueTRY: snap.inflationBreakEvenToday,
        realPnLDiff,
        depositScenarioValueTRY: snap.depositTryToday,
        depositRelativePnL: snap.assetTryToday - snap.depositTryToday,
        assetVsDepositDiff: snap.assetVsDepositDiff,
        series,
        missingUsdTry: false,
        missingCpi: false,
        depositUsesHistoricalRates: params.depositRatePctAnnual.length > 0,
    };
}
