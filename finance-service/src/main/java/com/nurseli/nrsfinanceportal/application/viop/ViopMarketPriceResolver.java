package com.nurseli.nrsfinanceportal.application.viop;

import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * finance-service VIOP piyasa fiyat çözümleyici — market-data'dan VIOP kontrat son fiyatlarını yükler ve çözer.
 */
@RequiredArgsConstructor
@Component

public class ViopMarketPriceResolver {

    private final MarketDataClient marketDataClient;

    /**
     * {@code loadLatestPricesBySymbol} — Tüm VIOP kontratları için sembol→fiyat cache map'i yükler.
     */
    public Map<String, BigDecimal> loadLatestPricesBySymbol() {
        Map<String, BigDecimal> out = new HashMap<>();
        try {
    List<MarketDataClient.ViopLatestRow> rows = marketDataClient.getViopLatestRows();
            if (rows == null) {
                return out;
            }
            for (MarketDataClient.ViopLatestRow row : rows) {
                if (row == null || row.contractCode() == null || row.price() == null) {
                    continue;
                }
                if (row.price().signum() <= 0) {
                    continue;
                }
                out.put(normalize(row.contractCode()), row.price());
            }
        } catch (RuntimeException ignored) {
            // market-data unavailable
        }
        return out;
    }

    /**
     * {@code resolve} — Cache'ten sembol fiyatını döner; yoksa market-data'dan tekil sorgular.
     */
    public BigDecimal resolve(String symbol, Map<String, BigDecimal> cache) {
        if (symbol == null || symbol.isBlank()) {
            return null;
    }
        String key = normalize(symbol);
        if (cache != null && cache.containsKey(key)) {
            return cache.get(key);
        }
        return null;
    }

    /**
     * {@code normalize} — VIOP kontrat sembolünü standart büyük harf formata normalize eder.
     */
    public static String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
    }
