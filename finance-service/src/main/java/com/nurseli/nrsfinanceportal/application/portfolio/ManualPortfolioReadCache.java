package com.nurseli.nrsfinanceportal.application.portfolio;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Kullanıcı bazlı kısa TTL önbellek — summary/me/insights aynı DB + market-data yükünü tekrarlamaz.
 */
@Component
public class ManualPortfolioReadCache {

    private static final Duration TTL = Duration.ofSeconds(90);

    private final ConcurrentHashMap<Long, CachedEntry> byUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> loadLocks = new ConcurrentHashMap<>();

    public record ReadBundle(
            List<ManualPortfolioPosition> positions,
            ManualPortfolioSummaryView summary,
            List<ManualPortfolioView> views
    ) {}

    public ReadBundle getOrLoad(Long userId, Supplier<ReadBundle> loader) {
        CachedEntry hit = byUser.get(userId);
        if (hit != null && hit.fresh()) {
            return hit.bundle();
        }
        Object lock = loadLocks.computeIfAbsent(userId, id -> new Object());
        synchronized (lock) {
            hit = byUser.get(userId);
            if (hit != null && hit.fresh()) {
                return hit.bundle();
            }
            ReadBundle loaded = loader.get();
            byUser.put(userId, new CachedEntry(loaded, Instant.now().plus(TTL)));
            return loaded;
        }
    }

    public void invalidate(Long userId) {
        if (userId != null) {
            byUser.remove(userId);
        }
    }

    private record CachedEntry(ReadBundle bundle, Instant expiresAt) {
        boolean fresh() {
            return expiresAt.isAfter(Instant.now());
        }
    }
}
