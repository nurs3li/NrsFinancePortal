import type { CouponFrequency } from '../../types/bondPosition';
import type { ViopDirection } from '../../types/viopPosition';
import {
    calculateViopPositionMetrics,
    type ViopFxRates,
    type ViopPositionMetricsInput,
} from '../../utils/viopPositionMetrics';
import { couponPeriodMonths, holdingMonthsBetween } from './bondCouponInference';

export type ViopLiveCalcInput = ViopPositionMetricsInput & {
    fxRates?: ViopFxRates;
};

export type ViopLiveCalcResult = {
    unrealizedPnl: number | null;
    riskExposure: number | null;
    netFinancialEffect: number | null;
};

export function computeViopLive(input: ViopLiveCalcInput): ViopLiveCalcResult {
    const fx: ViopFxRates = input.fxRates ?? { usdTry: null, eurTry: null };
    const m = calculateViopPositionMetrics(input, fx);
    return {
        unrealizedPnl: m.unrealizedPnlTry,
        riskExposure: m.riskExposureTry,
        netFinancialEffect: m.netFinancialEffect,
    };
}

export type BondPositionMetricsInput = {
    nominalValue: number;
    buyPrice: number;
    currentPrice: number | null;
    buyDate: string;
    couponRate?: number | null;
    couponFrequency?: CouponFrequency;
    asOfDate?: string;
};

export type BondPositionMetricsResult = {
    buyValue: number | null;
    currentValue: number | null;
    pricePnl: number | null;
    annualCoupon: number | null;
    periodicCoupon: number | null;
    completedCouponPeriods: number;
    collectedCoupon: number;
    estimatedAccruedCoupon: number | null;
    totalReturn: number | null;
    totalReturnPercent: number | null;
};

export function computeBondPositionMetrics(input: BondPositionMetricsInput): BondPositionMetricsResult {
    const { nominalValue, buyPrice, currentPrice, buyDate, couponRate, couponFrequency = 'NONE', asOfDate } =
        input;
    const empty: BondPositionMetricsResult = {
        buyValue: null,
        currentValue: null,
        pricePnl: null,
        annualCoupon: null,
        periodicCoupon: null,
        completedCouponPeriods: 0,
        collectedCoupon: 0,
        estimatedAccruedCoupon: null,
        totalReturn: null,
        totalReturnPercent: null,
    };
    if (!Number.isFinite(nominalValue) || nominalValue <= 0 || !Number.isFinite(buyPrice) || buyPrice <= 0) {
        return empty;
    }
    const buyValue = (nominalValue * buyPrice) / 100;
    const currentValue =
        currentPrice != null && Number.isFinite(currentPrice) && currentPrice > 0
            ? (nominalValue * currentPrice) / 100
            : null;
    const pricePnl = currentValue != null ? currentValue - buyValue : null;

    const rate = couponRate != null && Number.isFinite(couponRate) && couponRate > 0 ? couponRate : 0;
    const freq =
        rate > 0 && (couponFrequency === 'NONE' || !couponFrequency) ? 'SEMI_ANNUAL' : couponFrequency;
    const periodMonths = couponPeriodMonths(freq);
    const annualCoupon = rate > 0 && freq !== 'NONE' ? (nominalValue * rate) / 100 : null;
    let periodicCoupon: number | null = null;
    let completedCouponPeriods = 0;
    let collectedCoupon = 0;
    let estimatedAccruedCoupon: number | null = null;

    if (annualCoupon != null && periodMonths > 0) {
        const paymentsPerYear = 12 / periodMonths;
        periodicCoupon = annualCoupon / paymentsPerYear;
        const holdingMonths = holdingMonthsBetween(buyDate, asOfDate);
        if (holdingMonths != null) {
            completedCouponPeriods = Math.floor(holdingMonths / periodMonths);
            collectedCoupon = completedCouponPeriods * periodicCoupon;
            const remainderMonths = holdingMonths - completedCouponPeriods * periodMonths;
            if (remainderMonths > 0) {
                estimatedAccruedCoupon = periodicCoupon * (remainderMonths / periodMonths);
            }
        }
    }

    const totalReturn =
        pricePnl != null ? pricePnl + collectedCoupon : collectedCoupon > 0 ? collectedCoupon : null;
    const totalReturnPercent =
        totalReturn != null && buyValue > 0 ? (totalReturn / buyValue) * 100 : null;

    return {
        buyValue,
        currentValue,
        pricePnl,
        annualCoupon,
        periodicCoupon,
        completedCouponPeriods,
        collectedCoupon,
        estimatedAccruedCoupon,
        totalReturn,
        totalReturnPercent,
    };
}

export type BondLiveCalcInput = {
    nominalValue: number;
    buyPrice: number;
    currentPrice: number | null;
    couponRate?: number | null;
    couponFrequency?: CouponFrequency;
    buyDate?: string;
};

export type BondLiveCalcResult = BondPositionMetricsResult & {
    /** @deprecated use pricePnl */
    pnl: number | null;
    /** @deprecated use totalReturnPercent */
    returnPct: number | null;
};

export function computeBondLive(input: BondLiveCalcInput): BondLiveCalcResult {
    const m = computeBondPositionMetrics({
        nominalValue: input.nominalValue,
        buyPrice: input.buyPrice,
        currentPrice: input.currentPrice,
        buyDate: input.buyDate ?? new Date().toISOString().slice(0, 10),
        couponRate: input.couponRate,
        couponFrequency: input.couponFrequency ?? (input.couponRate && input.couponRate > 0 ? 'SEMI_ANNUAL' : 'NONE'),
    });
    return {
        ...m,
        pnl: m.pricePnl,
        returnPct: m.totalReturnPercent,
    };
}

export type ViopCloseReason = 'MANUAL_CLOSE' | 'TAKE_PROFIT' | 'STOP_LOSS' | 'EXPIRY';

export type ViopCloseCalcInput = {
    direction: ViopDirection;
    entryPrice: number;
    closePrice: number;
    contractMultiplier: number;
    contractCount: number;
    initialMargin: number;
    fee?: number;
};

export type ViopCloseCalcResult = {
    entryValue: number | null;
    closeValue: number | null;
    grossPnl: number | null;
    fee: number;
    netPnl: number | null;
    returnPercent: number | null;
    margin: number | null;
    riskExposure: number | null;
};

export function computeViopClose(input: ViopCloseCalcInput): ViopCloseCalcResult {
    const {
        direction,
        entryPrice,
        closePrice,
        contractMultiplier,
        contractCount,
        initialMargin,
        fee = 0,
    } = input;
    const safeFee = Number.isFinite(fee) && fee > 0 ? fee : 0;
    if (
        !Number.isFinite(entryPrice) ||
        entryPrice <= 0 ||
        !Number.isFinite(closePrice) ||
        closePrice <= 0 ||
        contractCount <= 0 ||
        contractMultiplier <= 0
    ) {
        return {
            entryValue: null,
            closeValue: null,
            grossPnl: null,
            fee: safeFee,
            netPnl: null,
            returnPercent: null,
            margin: Number.isFinite(initialMargin) ? initialMargin : null,
            riskExposure: null,
        };
    }
    const entryValue = entryPrice * contractMultiplier * contractCount;
    const closeValue = closePrice * contractMultiplier * contractCount;
    const diff = direction === 'LONG' ? closePrice - entryPrice : entryPrice - closePrice;
    const grossPnl = diff * contractMultiplier * contractCount;
    const netPnl = grossPnl - safeFee;
    const margin = Number.isFinite(initialMargin) ? initialMargin : 0;
    const returnPercent = margin > 0 ? (netPnl / margin) * 100 : 0;
    const riskExposure = closePrice * contractMultiplier * contractCount;
    return {
        entryValue,
        closeValue,
        grossPnl,
        fee: safeFee,
        netPnl,
        returnPercent,
        margin: margin > 0 ? margin : null,
        riskExposure,
    };
}

export type BondCloseType = 'SALE' | 'REDEMPTION';

export type BondCloseCalcInput = {
    nominalValue: number;
    buyPrice: number;
    closePrice: number;
    collectedCouponAmount?: number;
    fee?: number;
};

export type BondCloseCalcResult = {
    buyValue: number | null;
    closeValue: number | null;
    couponIncome: number;
    fee: number;
    totalPnl: number | null;
    returnPercent: number | null;
};

export function computeBondClose(input: BondCloseCalcInput): BondCloseCalcResult {
    const { nominalValue, buyPrice, closePrice, collectedCouponAmount = 0, fee = 0 } = input;
    const couponIncome = Number.isFinite(collectedCouponAmount) && collectedCouponAmount > 0 ? collectedCouponAmount : 0;
    const safeFee = Number.isFinite(fee) && fee > 0 ? fee : 0;
    if (
        !Number.isFinite(nominalValue) ||
        nominalValue <= 0 ||
        !Number.isFinite(buyPrice) ||
        buyPrice <= 0 ||
        !Number.isFinite(closePrice) ||
        closePrice <= 0
    ) {
        return {
            buyValue: null,
            closeValue: null,
            couponIncome,
            fee: safeFee,
            totalPnl: null,
            returnPercent: null,
        };
    }
    const buyValue = (nominalValue * buyPrice) / 100;
    const closeValue = (nominalValue * closePrice) / 100;
    const totalPnl = closeValue + couponIncome - buyValue - safeFee;
    const returnPercent = buyValue > 0 ? (totalPnl / buyValue) * 100 : 0;
    return { buyValue, closeValue, couponIncome, fee: safeFee, totalPnl, returnPercent };
}

export function holdingDaysBetween(buyDate: string, closeDate: string): number | null {
    if (!buyDate || !closeDate) return null;
    const start = new Date(`${buyDate}T12:00:00`);
    const end = new Date(`${closeDate}T12:00:00`);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return null;
    const ms = end.getTime() - start.getTime();
    return Math.max(0, Math.round(ms / 86_400_000));
}
