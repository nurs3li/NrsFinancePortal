/**
 * US / BIST ayrımı ve ısı haritası için overview & equity/latest satırlarına eklenebilir alanlar.
 * Sunucu göndermese de uyumluluk için hepsi opsiyonel.
 */
export type EquityMarketMetadata = {
    name?: string;
    marketRegion?: string;
    exchange?: string;
    currency?: string;
    sector?: string;
    delayMinutes?: number;
};

export type LatestPriceRow = {
    symbol?: string;
    /** marketdata doğrudan; finance overview FX için `buy` / `sell` kullanır */
    buyPrice?: number;
    sellPrice?: number;
    buy?: number;
    sell?: number;
    price?: number;
    source?: string;
    timestamp?: string;
    asOf?: string;
    qualityFlag?: 'EXACT' | 'PREVIOUS_DAY' | 'FALLBACK' | 'MISSING' | string;
    status?: string;
    message?: string;
} & EquityMarketMetadata;

export type MarketDashboard = {
    latest: {
        doviz: Record<string, LatestPriceRow>;
        metals: Record<string, LatestPriceRow>;
        crypto: Record<string, LatestPriceRow>;
        funds: Record<string, LatestPriceRow>;
        stocks: Record<string, LatestPriceRow>;
        timestamp?: string;
    };
    sparklines: { assetClass: string; symbol: string; closes: number[] }[];
    heatmapTiles: {
        sector: string;
        industry?: string | null;
        symbol: string;
        assetClass: string;
        changePercent: number;
        layoutWeight: number;
        mode?: string;
        changeHorizon?: string;
        weightMode?: string;
        marketCapSource?: string | null;
        marketCapAsOf?: string | null;
    }[];
    heatmapMeta?: {
        equityMode?: string;
        equityChangeHorizon?: string;
        equityWeightMode?: string;
        multiAssetMode?: string;
        multiAssetChangeHorizon?: string;
        multiAssetWeightMode?: string;
    };
    volatility: { assetClass: string; symbol: string; dailyVolatility: number }[];
    computedAt: string;
};

export type TabId = 'doviz' | 'crypto' | 'metals' | 'funds' | 'equity';
