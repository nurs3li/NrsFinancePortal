package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.nurseli.nrsfinanceportal.application.portfolio.HistoricalManualPriceResolverService;
import com.nurseli.nrsfinanceportal.application.portfolio.HistoricalManualPriceResolverService.ManualChartPoint;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualPortfolioPriceTreeLoader {

    public record SymbolKey(AssetType type, String symbol) {
        public String cacheKey() {
            return type.name() + "|" + symbol.trim().toUpperCase();
        }
    }

    private final HistoricalManualPriceResolverService priceResolver;
    private final ManualSymbolDailyCloseStore dailyCloseStore;
    private final ExecutorService executor = Executors.newFixedThreadPool(6);

    public Map<String, NavigableMap<LocalDate, BigDecimal>> loadFromDb(
            Long userId,
            Set<SymbolKey> keys,
            LocalDate histFrom,
            LocalDate end
    ) {
        Map<String, NavigableMap<LocalDate, BigDecimal>> out = new HashMap<>();
        for (SymbolKey key : keys) {
            NavigableMap<LocalDate, BigDecimal> tree =
                    dailyCloseStore.loadRange(userId, key.type(), key.symbol(), histFrom, end);
            out.put(key.cacheKey(), new TreeMap<>(tree));
        }
        return out;
    }

    public Map<String, NavigableMap<LocalDate, BigDecimal>> loadFromMdsParallel(
            Long userId,
            Set<SymbolKey> keys,
            LocalDate histFrom,
            LocalDate end,
            LatestPricingSnapshot pricingSnapshot,
            boolean persistToDb
    ) {
        Map<String, NavigableMap<LocalDate, BigDecimal>> out = new HashMap<>();
        if (keys.isEmpty()) {
            return out;
        }
        LatestPricingSnapshot snap = pricingSnapshot;
        CompletableFuture<?>[] tasks = keys.stream()
                .map(key -> CompletableFuture.runAsync(() -> {
                    TreeMap<LocalDate, BigDecimal> tree = new TreeMap<>();
                    try {
                        List<ManualChartPoint> pts = priceResolver.loadDailyCloseSeriesTry(
                                key.type(), key.symbol(), histFrom, end, snap);
                        for (ManualChartPoint pt : pts) {
                            if (pt.priceTry() != null && pt.priceTry().signum() > 0) {
                                tree.put(pt.date(), pt.priceTry());
                            }
                        }
                        if (persistToDb && userId != null && !pts.isEmpty()) {
                            dailyCloseStore.upsertSeries(userId, key.type(), key.symbol(), pts);
                        }
                    } catch (RuntimeException ex) {
                        log.warn("[MANUAL_PRICE_TREE] MDS load failed type={} symbol={}: {}",
                                key.type(), key.symbol(), ex.toString());
                    }
                    synchronized (out) {
                        out.put(key.cacheKey(), tree);
                    }
                }, executor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(tasks).join();
        return out;
    }

    public static String priceTreeKey(AssetType type, String symbol) {
        return type.name() + "|" + (symbol == null ? "" : symbol.trim().toUpperCase());
    }
}
