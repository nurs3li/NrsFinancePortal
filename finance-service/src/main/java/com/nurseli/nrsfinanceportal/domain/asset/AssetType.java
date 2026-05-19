package com.nurseli.nrsfinanceportal.domain.asset;

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
