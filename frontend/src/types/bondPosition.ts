export type BondType = 'GOVERNMENT_BOND' | 'TREASURY_BILL' | 'EUROBOND' | 'CORPORATE_BOND';
export type CouponFrequency = 'NONE' | 'ANNUAL' | 'SEMI_ANNUAL' | 'QUARTERLY';
export type BondPositionStatus = 'OPEN' | 'SOLD' | 'DELETED';
export type BondCloseType = 'SALE' | 'REDEMPTION';

export type ManualBondPosition = {
    id: number;
    symbol: string;
    displayName?: string | null;
    bondType: BondType;
    currency: string;
    nominalValue: number;
    buyPrice: number;
    buyDate: string;
    currentPrice?: number | null;
    maturityDate?: string | null;
    couponRate?: number | null;
    couponFrequency?: CouponFrequency | null;
    status: BondPositionStatus;
    sellPrice?: number | null;
    sellDate?: string | null;
    closeType?: BondCloseType | null;
    closeFee?: number | null;
    collectedCouponAmount?: number | null;
    realizedPnl?: number | null;
    realizedReturnPercent?: number | null;
    buyValue?: number | null;
    currentValue?: number | null;
    pnl?: number | null;
    pricePnl?: number | null;
    returnPct?: number | null;
    annualCoupon?: number | null;
    periodicCoupon?: number | null;
    completedCouponPeriods?: number | null;
    collectedCoupon?: number | null;
    estimatedAccruedCoupon?: number | null;
    totalReturn?: number | null;
    totalReturnPercent?: number | null;
    daysToMaturity?: number | null;
    note?: string | null;
};

export type BondPositionSummary = {
    openPositionCount: number;
    totalNominalValue: number;
    totalCurrentValue: number;
    totalPnl: number;
    totalPricePnl?: number | null;
    totalCollectedCoupon?: number | null;
    averageReturnPct?: number | null;
    annualCouponEstimate?: number | null;
    expiringSoonCount: number;
    currencyBreakdown: Record<string, number>;
    incompleteDataCount: number;
};

export type ManualBondPositionCreatePayload = {
    symbol: string;
    displayName?: string;
    bondType: BondType;
    currency: string;
    nominalValue: number;
    buyPrice: number;
    buyDate: string;
    currentPrice?: number;
    maturityDate?: string;
    couponRate?: number;
    couponFrequency?: CouponFrequency;
    note?: string;
};

export type ManualBondPositionSellPayload = {
    sellPrice: number;
    sellDate: string;
    closeType?: BondCloseType;
    collectedCouponAmount?: number;
    fee?: number;
    note?: string;
};

export type ViopBondCombinedSummary = {
    totalFinancialEffect: number;
    totalRiskExposure: number;
    totalExpiringSoon: number;
    activeAlertCount?: number | null;
    classicPortfolioValue?: number | null;
    bondCurrentValue: number;
    viopNetEffect: number;
    viopTotalInitialMargin: number;
    viopTotalUnrealizedPnl: number;
};
