package com.nurseli.nrsfinanceportal.domain.asset;

/** Portföy ve fiyat alarmı varlık türü (FX, CRYPTO, STOCK, BIST, METAL, FUND, VIOP, BOND). */
public enum AssetType {
    FX,        // USD, EUR
    CRYPTO,    // BTC, ETH
    STOCK,     // AAPL, TSLA (ABD, USD kotasyon)
    BIST,      // THYAO, ASELS (TRY kotasyon)
    METAL,     // XAU
    FUND,      // ETF: SPY, QQQ (USD kotasyon)
    VIOP,      // VİOP vadeli kontrat (manuel pozisyon / fiyat alarmı)
    BOND       // Tahvil / eurobond (manuel pozisyon / fiyat alarmı)
}
