package com.nurseli.marketdata.domain.price;

import java.util.Map;
import java.util.Set;

public class CryptoSymbolMapping {

    public static final Map<String, String> SYMBOL_TO_ID =
            Map.ofEntries(
                    Map.entry("BTCUSDT", "bitcoin"),
                    Map.entry("ETHUSDT", "ethereum"),
                    Map.entry("BNBUSDT", "binancecoin"),
                    Map.entry("SOLUSDT", "solana"),
                    Map.entry("ADAUSDT", "cardano"),
                    Map.entry("XRPUSDT", "ripple"),
                    Map.entry("AVAXUSDT", "avalanche-2"),
                    Map.entry("DOTUSDT", "polkadot"),
                    Map.entry("ATOMUSDT", "cosmos"),
                    Map.entry("LINKUSDT", "chainlink"),
                    Map.entry("MATICUSDT", "matic-network")
            );

    public static Set<String> supportedSymbols() {
        return SYMBOL_TO_ID.keySet();
    }

    public static String idsAsCsv() {
        return String.join(",", SYMBOL_TO_ID.values());
    }
}