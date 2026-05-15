package com.nurseli.marketdata.domain.metal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * USD/ons kıymetli maden sembolleri — İş Yatırım IndexHistoricalAll ({@code endeks}) eşlemesi.
 */
public final class PreciousMetalUsdCatalog {

    public static final String SOURCE = "IS_YATIRIM";

    public record Entry(
            String canonicalSymbol,
            String providerSymbol,
            String displayName
    ) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("XAU_USD_OZ", "XAUUSD", "Altın Ons"),
            new Entry("XAG_USD_OZ", "XAGUSD", "Gümüş Ons"),
            new Entry("XPT_USD_OZ", "XPTUSD", "Platin Ons"),
            new Entry("XPD_USD_OZ", "XPDUSD", "Paladyum Ons")
    );

    private static final Map<String, Entry> BY_CANONICAL = new LinkedHashMap<>();

    static {
        for (Entry e : ENTRIES) {
            BY_CANONICAL.put(e.canonicalSymbol(), e);
        }
    }

    private PreciousMetalUsdCatalog() {}

    public static List<Entry> all() {
        return ENTRIES;
    }

    public static Set<String> canonicalSymbols() {
        return BY_CANONICAL.keySet();
    }

    public static Entry byCanonicalOrNull(String canonicalUpper) {
        if (canonicalUpper == null) {
            return null;
        }
        return BY_CANONICAL.get(canonicalUpper.trim().toUpperCase());
    }

    public static boolean isUsdOunceMetal(String symbolUpper) {
        return symbolUpper != null && BY_CANONICAL.containsKey(symbolUpper.trim().toUpperCase());
    }
}
