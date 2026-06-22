export const SIMULATION_USD_DENOMINATED = 'SIMULATION_USD_DENOMINATED';
export const SIMULATION_HISTORY_PREPARING = 'SIMULATION_HISTORY_PREPARING';
export const SIMULATION_STORAGE_KEY = 'nrs-finance-portal-simulation-list-v1';

/** Karanlık mod — fosforlu tonların yumuşatılmış, okunaklı hali */
export const CHART_PALETTE_DARK = ['#C9A227', '#5B9BD5', '#45B883', '#9585C4'] as const;

/** Aydınlık mod — beyaz zemin üzerinde net ayrışan seriler */
export const CHART_PALETTE_LIGHT = ['#1E40AF', '#0F766E', '#166534', '#5B21B6', '#BE123C', '#B45309'] as const;

/** @deprecated chartPaletteForTheme kullanın */
export const CHART_PALETTE = CHART_PALETTE_DARK;

export function chartPaletteForTheme(theme: 'light' | 'dark'): readonly string[] {
    return theme === 'light' ? CHART_PALETTE_LIGHT : CHART_PALETTE_DARK;
}

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
