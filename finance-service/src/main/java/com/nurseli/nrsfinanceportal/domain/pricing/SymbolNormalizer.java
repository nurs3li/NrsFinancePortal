package com.nurseli.nrsfinanceportal.domain.pricing;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;

public class SymbolNormalizer {

    private SymbolNormalizer() {}

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
            case FUND -> symbol;    // AES, AFT

            default -> symbol;
        };
    }
}
