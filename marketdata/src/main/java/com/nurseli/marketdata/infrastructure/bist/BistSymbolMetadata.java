package com.nurseli.marketdata.infrastructure.bist;

/**
 * BIST hisse katalog kaydı — yalnızca metadata; fiyat alanları yok.
 */
public record BistSymbolMetadata(
        String symbol,
        String isYatirimSymbol,
        String yahooSymbol,
        String displayName,
        String sector,
        String exchange,
        String currency,
        String assetType,
        String country
) {
}
