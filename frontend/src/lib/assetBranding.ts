import type { TabId } from '../components/market/marketTypes';

/** Dashboard ve Market API ile uyumlu piyasa türü. */
export type MarketKind = 'FX' | 'METALS' | 'CRYPTO' | 'FUNDS' | 'EQUITY';

/** Dashboard ile aynı isim (geriye uyumluluk). */
export type MarketType = MarketKind;

export function tabIdToMarketKind(tab: TabId): MarketKind {
    switch (tab) {
        case 'doviz':
            return 'FX';
        case 'metals':
            return 'METALS';
        case 'crypto':
            return 'CRYPTO';
        case 'funds':
            return 'FUNDS';
        case 'equity':
            return 'EQUITY';
        default:
            return 'FX';
    }
}

const dynamicLogoMap: Record<string, string> = {
    BTC: 'https://cryptoicons.org/api/icon/btc/24',
    ETH: 'https://cryptoicons.org/api/icon/eth/24',
    BNB: 'https://cryptoicons.org/api/icon/bnb/24',
    SOL: 'https://cryptoicons.org/api/icon/sol/24',
    ADA: 'https://cryptoicons.org/api/icon/ada/24',
    XRP: 'https://cryptoicons.org/api/icon/xrp/24',
    AVAX: 'https://cryptoicons.org/api/icon/avax/24',
    DOT: 'https://cryptoicons.org/api/icon/dot/24',
    ATOM: 'https://cryptoicons.org/api/icon/atom/24',
    LINK: 'https://cryptoicons.org/api/icon/link/24',
    TRX: 'https://cryptoicons.org/api/icon/trx/24',
    LTC: 'https://cryptoicons.org/api/icon/ltc/24',
    XLM: 'https://cryptoicons.org/api/icon/xlm/24',
    NEAR: 'https://cryptoicons.org/api/icon/near/24',
    ARB: 'https://cryptoicons.org/api/icon/arb/24',
    OP: 'https://cryptoicons.org/api/icon/op/24',
    USD: 'https://flagcdn.com/w20/us.png',
    EUR: 'https://flagcdn.com/w20/eu.png',
    TRY: 'https://flagcdn.com/w20/tr.png',
    GBP: 'https://flagcdn.com/w20/gb.png',
    ALTIN: 'https://img.icons8.com/color/48/gold-bars.png',
};

export function formatAssetLabel(symbol: string, marketType: MarketKind): string {
    if (symbol === 'XAU_TRY') return 'ALTIN (ONS)';
    if (marketType === 'FX' && symbol.length === 6 && symbol.endsWith('TRY')) {
        return `${symbol.slice(0, 3)}/${symbol.slice(3)}`;
    }
    if (marketType === 'CRYPTO' && symbol.endsWith('USDT')) {
        return `${symbol.slice(0, -4)}/USD`;
    }
    return symbol.replace('_', '/');
}

export function getLogoKey(symbol: string, marketType: MarketKind): string {
    if (symbol.includes('XAU') || symbol.includes('ALTIN')) return 'ALTIN';
    if (marketType === 'CRYPTO' && symbol.endsWith('USDT')) {
        return symbol.replace('USDT', '');
    }
    if (marketType === 'FX' && symbol.length >= 3) {
        return symbol.slice(0, 3);
    }
    return symbol.toUpperCase();
}

/**
 * VİOP/Bond kontrat kodlarini (F_USDTRY0526, EREGL0626, ISIN'ler vb.) dis CDN'e gondermek
 * %100 404 ureriyor. Bu da AssetLogo onError -> setState fırtınasına yol acip ana thread'i
 * tikiyordu (sayfa gecislerinin gerceklesmeme nedeni). Heuristik: salt-harf ve 1-5 karakter
 * disindaki anahtarlari "ticker degil" sayip kisa devre yapiyoruz.
 */
function isLikelyEquityTicker(key: string): boolean {
    if (!key || key.length < 1 || key.length > 5) return false;
    if (!/^[A-Z]+$/.test(key)) return false;
    return true;
}

export function getDynamicLogoUrl(symbol: string, marketType: MarketKind): string | null {
    const logoKey = getLogoKey(symbol, marketType);
    if (dynamicLogoMap[logoKey]) return dynamicLogoMap[logoKey];
    if (marketType === 'EQUITY' || marketType === 'FUNDS') {
        if (!isLikelyEquityTicker(logoKey)) return null;
        return `https://financialmodelingprep.com/image-stock/${logoKey}.png`;
    }
    return null;
}
