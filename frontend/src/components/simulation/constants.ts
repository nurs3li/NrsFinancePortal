export const SIMULATION_USD_DENOMINATED = 'SIMULATION_USD_DENOMINATED';
export const SIMULATION_STORAGE_KEY = 'nrs-finance-portal-simulation-list-v1';
export const SIMULATION_HISTORY_STORAGE_KEY = 'nrs-finance-portal-simulation-history-v1';

export const CHART_PALETTE = ['#FFD700', '#00D4FF', '#39FF14', '#BC13FE'] as const;

/** Varlık seçildiğinde önerilen karşılaştırma sembolleri */
export const COMPARE_SUGGESTIONS: Record<string, { type: import('../../constants/OrderConstants').AssetType; symbol: string }[]> = {
    BTCUSDT: [
        { type: 'CRYPTO', symbol: 'ETHUSDT' },
        { type: 'METAL', symbol: 'XAU_TRY' },
        { type: 'FX', symbol: 'USDTRY' },
        { type: 'STOCK', symbol: 'AAPL' },
    ],
    ETHUSDT: [
        { type: 'CRYPTO', symbol: 'BTCUSDT' },
        { type: 'METAL', symbol: 'XAU_TRY' },
        { type: 'FX', symbol: 'USDTRY' },
    ],
    XAU_TRY: [
        { type: 'FX', symbol: 'USDTRY' },
        { type: 'CRYPTO', symbol: 'BTCUSDT' },
        { type: 'STOCK', symbol: 'AAPL' },
    ],
    USDTRY: [
        { type: 'METAL', symbol: 'XAU_TRY' },
        { type: 'CRYPTO', symbol: 'BTCUSDT' },
        { type: 'STOCK', symbol: 'AAPL' },
    ],
    AAPL: [
        { type: 'STOCK', symbol: 'MSFT' },
        { type: 'CRYPTO', symbol: 'BTCUSDT' },
        { type: 'METAL', symbol: 'XAU_TRY' },
        { type: 'FX', symbol: 'USDTRY' },
    ],
    DEFAULT: [
        { type: 'CRYPTO', symbol: 'BTCUSDT' },
        { type: 'METAL', symbol: 'XAU_TRY' },
        { type: 'FX', symbol: 'USDTRY' },
        { type: 'STOCK', symbol: 'AAPL' },
    ],
};
