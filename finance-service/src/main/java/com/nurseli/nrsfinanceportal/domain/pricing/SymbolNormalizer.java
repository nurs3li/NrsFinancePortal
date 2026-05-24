package com.nurseli.nrsfinanceportal.domain.pricing;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;

/**
 * Sembol normalizasyon yardımcısı; market lookup öncesi format birleştirme.
 */
public class SymbolNormalizer {

    private SymbolNormalizer() {}

    /**
     * AssetType'a göre sembolü market lookup formatına normalize eder.
     */
    public static String normalize(AssetType type, String symbol) {

        return switch (type) {

            case CRYPTO -> {
                // BTC -> BTCUSDT
                if (!symbol.endsWith("USDT")) {
                    yield symbol + "USDT";
                }
                yield symbol;
            }

            case FX -> symbol;      // USDTRY
            case METAL -> symbol;   // XAU, XAG
            case FUND -> symbol;     // ETF: SPY, QQQ, VOO...
            case STOCK -> symbol == null ? "" : symbol.toUpperCase();
            case BIST -> symbol == null ? "" : symbol.trim().toUpperCase();
            case VIOP, BOND -> symbol == null ? "" : symbol.trim().toUpperCase();
            default -> symbol;
        };
    }
}
