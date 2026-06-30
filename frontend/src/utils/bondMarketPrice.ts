/** DİBS fiyatları 100 nominal üzerinden; EVDS TP_* serileri bazen kupon benzeri düşük değer döndürür. */
export const MIN_PLAUSIBLE_BOND_MARKET_PRICE = 50;

export function isPlausibleBondMarketPrice(price: number | null | undefined): boolean {
    return price != null && Number.isFinite(price) && price >= MIN_PLAUSIBLE_BOND_MARKET_PRICE;
}

/** EVDS/market fiyatı güvenilir değilse 0 döner (manuel giriş tetiklenir). */
export function sanitizeBondMarketPrice(price: number | null | undefined): number {
    return isPlausibleBondMarketPrice(price) ? price! : 0;
}
