package com.nurseli.marketdata.infrastructure.bist;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Sabit BIST hisse kataloğu (20 sembol). Okuma/yazma dışında değişmez.
 */
@Component
public class BistSymbolCatalog {

    private final List<BistSymbolMetadata> all;
    private final Map<String, BistSymbolMetadata> byCanonicalSymbol;

    public BistSymbolCatalog() {
        List<BistSymbolMetadata> built = List.of(
                m("THYAO", "Türk Hava Yolları", "Ulaştırma"),
                m("ASELS", "ASELSAN", "Savunma"),
                m("KCHOL", "Koç Holding", "Holding"),
                m("SAHOL", "Sabancı Holding", "Holding"),
                m("GARAN", "Garanti BBVA", "Bankacılık"),
                m("AKBNK", "Akbank", "Bankacılık"),
                m("ISCTR", "İş Bankası (C)", "Bankacılık"),
                m("YKBNK", "Yapı Kredi Bankası", "Bankacılık"),
                m("EREGL", "Ereğli Demir ve Çelik", "Çelik"),
                m("TUPRS", "Tüpraş", "Petrol"),
                m("BIMAS", "BİM", "Perakende"),
                m("MGROS", "Migros", "Perakende"),
                m("SISE", "Şişecam", "Kimya / Cam"),
                m("FROTO", "Ford Otosan", "Otomotiv"),
                m("TOASO", "Tofaş", "Otomotiv"),
                m("TCELL", "Turkcell", "Telekomünikasyon"),
                m("ENKAI", "Enka İnşaat", "İnşaat"),
                m("PETKM", "Petkim", "Petrokimya"),
                m("KOZAL", "Koza Altın", "Madencilik"),
                m("PGSUS", "Pegasus", "Ulaştırma")
        );
        this.all = List.copyOf(built);
        Map<String, BistSymbolMetadata> map = new HashMap<>(built.size() * 2);
        for (BistSymbolMetadata row : built) {
            map.put(row.symbol(), row);
        }
        this.byCanonicalSymbol = Map.copyOf(map);
    }

    private static BistSymbolMetadata m(String symbol, String displayName, String sector) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        return new BistSymbolMetadata(
                sym,
                sym,
                sym + ".IS",
                displayName,
                sector,
                "BIST",
                "TRY",
                "EQUITY",
                "TR"
        );
    }

    public List<BistSymbolMetadata> getAll() {
        return all;
    }

    public Optional<BistSymbolMetadata> findBySymbol(String symbol) {
        String key = canonicalKey(symbol);
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCanonicalSymbol.get(key));
    }

    public boolean isSupported(String symbol) {
        return findBySymbol(symbol).isPresent();
    }

    /**
     * @throws IllegalArgumentException sembol katalogda yoksa
     */
    public String toIsYatirimSymbol(String symbol) {
        return findBySymbol(symbol)
                .map(BistSymbolMetadata::isYatirimSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported BIST symbol: " + symbol));
    }

    /**
     * @throws IllegalArgumentException sembol katalogda yoksa
     */
    public String toYahooSymbol(String symbol) {
        return findBySymbol(symbol)
                .map(BistSymbolMetadata::yahooSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported BIST symbol: " + symbol));
    }

    /**
     * Boş / null için null. Aksi halde trim + ROOT upper-case; {@code *.IS} soneki kaldırılıp ana sembolle eşleştirilir.
     */
    static String canonicalKey(String symbol) {
        if (symbol == null) {
            return null;
        }
        String s = symbol.trim();
        if (s.isEmpty()) {
            return null;
        }
        s = s.toUpperCase(Locale.ROOT);
        if (s.endsWith(".IS")) {
            s = s.substring(0, s.length() - ".IS".length());
        }
        return s.isEmpty() ? null : s;
    }
}
