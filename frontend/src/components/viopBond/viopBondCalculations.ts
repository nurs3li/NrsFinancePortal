import type { ViopDirection } from '../../types/viopPosition';

export type ViopLiveCalcInput = {
    direction: ViopDirection;
    entryPrice: number;
    currentPrice: number | null;
    contractMultiplier: number;
    contractCount: number;
    initialMargin: number;
};

export type ViopLiveCalcResult = {
    unrealizedPnl: number | null;
    riskExposure: number | null;
    netFinancialEffect: number | null;
};

export function computeViopLive(input: ViopLiveCalcInput): ViopLiveCalcResult {
    const { direction, entryPrice, currentPrice, contractMultiplier, contractCount, initialMargin } = input;
    if (currentPrice == null || !Number.isFinite(currentPrice) || currentPrice <= 0) {
        return { unrealizedPnl: null, riskExposure: null, netFinancialEffect: null };
    }
    if (!Number.isFinite(entryPrice) || entryPrice <= 0 || contractCount <= 0 || contractMultiplier <= 0) {
        return { unrealizedPnl: null, riskExposure: null, netFinancialEffect: null };
    }
    const diff = direction === 'LONG' ? currentPrice - entryPrice : entryPrice - currentPrice;
    const pnl = diff * contractMultiplier * contractCount;
    const risk = currentPrice * contractMultiplier * contractCount;
    const margin = Number.isFinite(initialMargin) ? initialMargin : 0;
    return { unrealizedPnl: pnl, riskExposure: risk, netFinancialEffect: margin + pnl };
}

export type BondLiveCalcInput = {
    nominalValue: number;
    buyPrice: number;
    currentPrice: number | null;
    couponRate?: number | null;
};

export type BondLiveCalcResult = {
    buyValue: number | null;
    currentValue: number | null;
    pnl: number | null;
    returnPct: number | null;
    annualCoupon: number | null;
};

export function computeBondLive(input: BondLiveCalcInput): BondLiveCalcResult {
    const { nominalValue, buyPrice, currentPrice, couponRate } = input;
    if (!Number.isFinite(nominalValue) || nominalValue <= 0 || !Number.isFinite(buyPrice) || buyPrice <= 0) {
        return { buyValue: null, currentValue: null, pnl: null, returnPct: null, annualCoupon: null };
    }
    const buyValue = (nominalValue * buyPrice) / 100;
    const currentValue =
        currentPrice != null && Number.isFinite(currentPrice) && currentPrice > 0
            ? (nominalValue * currentPrice) / 100
            : null;
    const pnl = currentValue != null ? currentValue - buyValue : null;
    const returnPct = pnl != null && buyValue > 0 ? (pnl / buyValue) * 100 : null;
    const annualCoupon =
        couponRate != null && Number.isFinite(couponRate) && couponRate >= 0
            ? (nominalValue * couponRate) / 100
            : null;
    return { buyValue, currentValue, pnl, returnPct, annualCoupon };
}
