package com.nurseli.nrsfinanceportal.infrastructure.client.market;

import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.DebtLatestRow;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.ViopLatestRow;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Market-data sık okunan yanıtları için kısa TTL bellek içi önbellek (dashboard / özet uçları).
 */
@Component
public class MarketDataHotCache {

    private static final Duration LATEST_TTL = Duration.ofSeconds(45);
    private static final Duration CPI_TTL = Duration.ofMinutes(5);

    private final Object latestPricingLock = new Object();
    private final Object viopLatestLock = new Object();
    private final Object debtLatestLock = new Object();

    private volatile Entry<LatestPricingSnapshot> latestPricing;
    private volatile Entry<List<ViopLatestRow>> viopLatest;
    private volatile Entry<List<DebtLatestRow>> debtLatest;
    private final ConcurrentHashMap<String, Entry<CpiIndexLookup>> cpiByRange = new ConcurrentHashMap<>();

    public LatestPricingSnapshot latestPricing(Supplier<LatestPricingSnapshot> loader) {
        Entry<LatestPricingSnapshot> hit = latestPricing;
        if (hit != null && hit.fresh()) {
            return hit.value();
        }
        synchronized (latestPricingLock) {
            hit = latestPricing;
            if (hit != null && hit.fresh()) {
                return hit.value();
            }
            LatestPricingSnapshot loaded = loader.get();
            latestPricing = new Entry<>(loaded, Instant.now().plus(LATEST_TTL));
            return loaded;
        }
    }

    public List<ViopLatestRow> viopLatest(Supplier<List<ViopLatestRow>> loader) {
        synchronized (viopLatestLock) {
            return getOrLoad(viopLatest, loader, LATEST_TTL, v -> viopLatest = v);
        }
    }

    public List<DebtLatestRow> debtLatest(Supplier<List<DebtLatestRow>> loader) {
        synchronized (debtLatestLock) {
            return getOrLoad(debtLatest, loader, LATEST_TTL, v -> debtLatest = v);
        }
    }

    public CpiIndexLookup cpiLookup(LocalDate from, LocalDate to, Supplier<CpiIndexLookup> loader) {
        String key = from + "|" + to;
        Entry<CpiIndexLookup> hit = cpiByRange.get(key);
        if (hit != null && hit.fresh()) {
            return hit.value();
        }
        synchronized (cpiByRange) {
            hit = cpiByRange.get(key);
            if (hit != null && hit.fresh()) {
                return hit.value();
            }
            CpiIndexLookup loaded = loader.get();
            cpiByRange.put(key, new Entry<>(loaded, Instant.now().plus(CPI_TTL)));
            return loaded;
        }
    }

    private static <T> T getOrLoad(
            Entry<T> cached,
            Supplier<T> loader,
            Duration ttl,
            java.util.function.Consumer<Entry<T>> store
    ) {
        if (cached != null && cached.fresh()) {
            return cached.value();
        }
        T loaded = loader.get();
        store.accept(new Entry<>(loaded, Instant.now().plus(ttl)));
        return loaded;
    }

    private record Entry<T>(T value, Instant expiresAt) {
        boolean fresh() {
            return expiresAt.isAfter(Instant.now());
        }
    }
}
