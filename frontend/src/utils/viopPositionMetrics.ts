import type { ViopCategory, ViopDirection, ManualViopPosition } from '../types/viopPosition';

export type ViopQuoteCurrency = 'TRY' | 'USD' | 'EUR';

export type ViopFxRates = {
    usdTry: number | null;
    eurTry: number | null;
};

export type ViopPositionMetricsInput = {
    symbol: string;
    underlyingSymbol?: string | null;
    viopCategory?: ViopCategory;
    direction: ViopDirection;
    entryPrice: number;
    currentPrice: number | null;
    contractMultiplier: number;
    contractCount: number;
    initialMargin: number;
};

export type ViopPositionMetrics = {
    quoteCurrency: ViopQuoteCurrency;
    unrealizedPnlNative: number | null;
    unrealizedPnlTry: number | null;
    riskExposureNative: number | null;
    riskExposureTry: number | null;
    marginTry: number | null;
    leverage: number | null;
    marginRatio: number | null;
    pnlToMarginRatio: number | null;
    netFinancialEffect: number | null;
    missingFxRate: boolean;
};

export type ViopPortfolioSummary = {
    openPositionCount: number;
    totalMarginTRY: number;
    totalOpenPnLTRY: number;
    totalRiskExposureTRY: number;
    portfolioLeverage: number | null;
    marginRatio: number | null;
    pnlToMarginRatio: number | null;
    longCount: number;
    shortCount: number;
    hasMissingFxRate: boolean;
};

const SUFFIX_RE = /^F?_?([A-Z0-9]+?)(\d{2})(\d{2})$/i;

export function getViopContractCurrency(
    symbol: string,
    underlyingSymbol?: string | null,
    viopCategory?: ViopCategory,
): ViopQuoteCurrency {
    const sym = norm(symbol);
    const und = norm(underlyingSymbol ?? '');
    const hay = und || sym;

    if (
        hay.includes('XAUUSD') ||
        hay.includes('XAGUSD') ||
        hay.includes('XPTUSD') ||
        hay.includes('XPDUSD')
    ) {
        return 'USD';
    }
    if (hay.endsWith('USD') && !hay.includes('TRY') && !hay.endsWith('USDTRY')) {
        return 'USD';
    }
    if (hay.includes('EURUSD') && !hay.includes('TRY')) {
        return 'USD';
    }
    if (hay.endsWith('EUR') && !hay.includes('TRY') && viopCategory !== 'FX') {
        return 'EUR';
    }
    if (hay.endsWith('TRY') || hay.includes('TRY') || hay.includes('TL')) {
        return 'TRY';
    }
    const m = sym.replace(/^F_/, '').match(SUFFIX_RE);
    if (m) {
        return getViopContractCurrency(m[1], null, viopCategory);
    }
    return 'TRY';
}

export function getViopContractMultiplier(position: {
    contractMultiplier?: number | null;
}): number {
    const m = position.contractMultiplier;
    return m != null && Number.isFinite(m) && m > 0 ? m : 1;
}

function fxRateFor(currency: ViopQuoteCurrency, fx: ViopFxRates): number | null {
    if (currency === 'TRY') return 1;
    if (currency === 'USD') {
        return fx.usdTry != null && fx.usdTry > 0 ? fx.usdTry : null;
    }
    const eur = fx.eurTry != null && fx.eurTry > 0 ? fx.eurTry : fx.usdTry;
    return eur != null && eur > 0 ? eur : null;
}

function toTry(native: number, currency: ViopQuoteCurrency, fx: ViopFxRates): { tryVal: number | null; missing: boolean } {
    if (!Number.isFinite(native)) return { tryVal: null, missing: false };
    if (currency === 'TRY') return { tryVal: native, missing: false };
    const rate = fxRateFor(currency, fx);
    if (rate == null) return { tryVal: null, missing: true };
    return { tryVal: native * rate, missing: false };
}

function ratio(num: number | null, den: number | null): number | null {
    if (num == null || den == null || !(den > 0)) return null;
    return num / den;
}

export function calculateViopPositionMetrics(
    input: ViopPositionMetricsInput,
    fxRates: ViopFxRates,
): ViopPositionMetrics {
    const quote = getViopContractCurrency(input.symbol, input.underlyingSymbol, input.viopCategory);
    const marginTry = toTry(
        Number.isFinite(input.initialMargin) ? input.initialMargin : 0,
        'TRY',
        fxRates,
    ).tryVal;

    if (
        input.currentPrice == null ||
        !Number.isFinite(input.currentPrice) ||
        input.currentPrice <= 0 ||
        !Number.isFinite(input.entryPrice) ||
        input.entryPrice <= 0 ||
        input.contractCount <= 0 ||
        input.contractMultiplier <= 0
    ) {
        return {
            quoteCurrency: quote,
            unrealizedPnlNative: null,
            unrealizedPnlTry: null,
            riskExposureNative: null,
            riskExposureTry: null,
            marginTry,
            leverage: null,
            marginRatio: null,
            pnlToMarginRatio: null,
            netFinancialEffect: marginTry,
            missingFxRate: quote !== 'TRY' && fxRateFor(quote, fxRates) == null,
        };
    }

    const diff =
        input.direction === 'LONG'
            ? input.currentPrice - input.entryPrice
            : input.entryPrice - input.currentPrice;
    const pnlNative = diff * input.contractMultiplier * input.contractCount;
    const riskNative = input.currentPrice * input.contractMultiplier * input.contractCount;

    const pnlConv = toTry(pnlNative, quote, fxRates);
    const riskConv = toTry(riskNative, quote, fxRates);
    const missingFx = pnlConv.missing || riskConv.missing;

    const leverage = ratio(riskConv.tryVal, marginTry);
    const marginRatio = ratio(marginTry, riskConv.tryVal);
    const pnlToMargin = ratio(pnlConv.tryVal, marginTry);
    const net =
        marginTry != null && pnlConv.tryVal != null ? marginTry + pnlConv.tryVal : marginTry;

    return {
        quoteCurrency: quote,
        unrealizedPnlNative: pnlNative,
        unrealizedPnlTry: pnlConv.tryVal,
        riskExposureNative: riskNative,
        riskExposureTry: riskConv.tryVal,
        marginTry,
        leverage,
        marginRatio,
        pnlToMarginRatio: pnlToMargin,
        netFinancialEffect: net,
        missingFxRate: missingFx,
    };
}

export function calculateViopSummary(
    positions: ManualViopPosition[],
    fxRates: ViopFxRates,
): ViopPortfolioSummary {
    const open = positions.filter((p) => p.status === 'OPEN');
    let totalMarginTRY = 0;
    let totalOpenPnLTRY = 0;
    let totalRiskExposureTRY = 0;
    let longCount = 0;
    let shortCount = 0;
    let hasMissingFxRate = false;

    for (const p of open) {
        if (p.direction === 'LONG') longCount++;
        else shortCount++;

        const m = calculateViopPositionMetrics(
            {
                symbol: p.symbol,
                underlyingSymbol: p.underlyingSymbol,
                viopCategory: p.viopCategory,
                direction: p.direction,
                entryPrice: Number(p.entryPrice),
                currentPrice: p.currentPrice != null ? Number(p.currentPrice) : null,
                contractMultiplier: getViopContractMultiplier(p),
                contractCount: Number(p.contractCount),
                initialMargin: Number(p.initialMargin ?? 0),
            },
            fxRates,
        );

        if (m.marginTry != null) totalMarginTRY += m.marginTry;
        if (m.unrealizedPnlTry != null) totalOpenPnLTRY += m.unrealizedPnlTry;
        if (m.riskExposureTry != null) totalRiskExposureTRY += m.riskExposureTry;
        if (m.missingFxRate) hasMissingFxRate = true;
    }

    return {
        openPositionCount: open.length,
        totalMarginTRY,
        totalOpenPnLTRY,
        totalRiskExposureTRY,
        portfolioLeverage: ratio(totalRiskExposureTRY, totalMarginTRY),
        marginRatio: ratio(totalMarginTRY, totalRiskExposureTRY),
        pnlToMarginRatio: ratio(totalOpenPnLTRY, totalMarginTRY),
        longCount,
        shortCount,
        hasMissingFxRate,
    };
}

export function findSimilarOpenViopPosition(
    positions: ManualViopPosition[],
    payload: {
        symbol: string;
        direction: ViopDirection;
        entryPrice: number;
        entryDate: string;
        expiryDate?: string | null;
    },
): ManualViopPosition | null {
    const sym = norm(payload.symbol);
    return (
        positions.find(
            (p) =>
                p.status === 'OPEN' &&
                norm(p.symbol) === sym &&
                p.direction === payload.direction &&
                p.entryDate === payload.entryDate &&
                Math.abs(Number(p.entryPrice) - payload.entryPrice) < 1e-6 &&
                (payload.expiryDate == null ||
                    p.expiryDate == null ||
                    p.expiryDate === payload.expiryDate),
        ) ?? null
    );
}

export function mergeViopPositionQuantities(
    existing: ManualViopPosition,
    addCount: number,
    addEntryPrice: number,
): { contractCount: number; entryPrice: number } {
    const oldCount = Number(existing.contractCount);
    const oldEntry = Number(existing.entryPrice);
    const newCount = oldCount + addCount;
    const weightedEntry =
        newCount > 0 ? (oldEntry * oldCount + addEntryPrice * addCount) / newCount : addEntryPrice;
    return { contractCount: newCount, entryPrice: weightedEntry };
}

function norm(s: string): string {
    return String(s ?? '')
        .trim()
        .replace(/\s+/g, '')
        .toUpperCase();
}
