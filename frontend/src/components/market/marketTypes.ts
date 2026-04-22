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
    status?: string;
    message?: string;
};

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
        symbol: string;
        assetClass: string;
        changePercent: number;
        layoutWeight: number;
    }[];
    volatility: { assetClass: string; symbol: string; dailyVolatility: number }[];
    computedAt: string;
};

export type TabId = 'doviz' | 'crypto' | 'metals' | 'funds' | 'equity';
