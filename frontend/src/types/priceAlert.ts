export type PriceAlertAssetType = 'FX' | 'CRYPTO' | 'STOCK' | 'BIST' | 'METAL' | 'FUND' | 'VIOP' | 'BOND';

export type PriceAlertConditionType =
    | 'PRICE_GTE'
    | 'PRICE_LTE'
    | 'CHANGE_PCT_GTE'
    | 'CHANGE_PCT_LTE';

export type PriceAlertChangeWindow = 'DAILY' | 'HOURS_24';

export type PriceAlertChannels = 'IN_APP' | 'EMAIL' | 'BOTH';

export type PriceAlertStatus = 'ACTIVE' | 'TRIGGERED' | 'DISABLED';

export type PriceAlert = {
    id: number;
    assetType: PriceAlertAssetType;
    symbol: string;
    conditionType: PriceAlertConditionType;
    threshold: number;
    changeWindow: PriceAlertChangeWindow | null;
    channels: PriceAlertChannels;
    status: PriceAlertStatus;
    repeatAlert: boolean;
    cooldownHours: number;
    lastTriggeredAt: string | null;
    createdAt: string;
};

export type PriceAlertCreatePayload = {
    assetType: PriceAlertAssetType;
    symbol: string;
    conditionType: PriceAlertConditionType;
    threshold: number;
    changeWindow?: PriceAlertChangeWindow | null;
    channels?: PriceAlertChannels;
    repeatAlert?: boolean;
    cooldownHours?: number;
};
