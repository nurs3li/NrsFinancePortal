package com.nurseli.nrsfinanceportal.service.viop;

import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ViopMarketPriceResolver {

    private final MarketDataClient marketDataClient;

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

    public static String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
