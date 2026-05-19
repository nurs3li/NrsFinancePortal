export type BondType = 'GOVERNMENT_BOND' | 'TREASURY_BILL' | 'EUROBOND' | 'CORPORATE_BOND';
export type CouponFrequency = 'NONE' | 'ANNUAL' | 'SEMI_ANNUAL' | 'QUARTERLY';
export type BondPositionStatus = 'OPEN' | 'SOLD' | 'DELETED';

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
    buyValue?: number | null;
    currentValue?: number | null;
    pnl?: number | null;
    returnPct?: number | null;
    annualCoupon?: number | null;
    daysToMaturity?: number | null;
    note?: string | null;
};

export type BondPositionSummary = {
    openPositionCount: number;
    totalNominalValue: number;
    totalCurrentValue: number;
    totalPnl: number;
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
