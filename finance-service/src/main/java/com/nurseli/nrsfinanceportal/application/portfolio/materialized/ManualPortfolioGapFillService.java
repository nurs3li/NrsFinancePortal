package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioInsightsResponse;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.application.ManualPortfolioService;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioViewAssembler;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioWarmStatus;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualPortfolioGapFillService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final int GAP_FILL_MAX_DAYS = 3;

    private final ManualSymbolDailyCloseStore dailyCloseStore;
    private final ManualPortfolioPriceTreeLoader priceTreeLoader;
    private final MarketDataClient marketDataClient;
    private final ManualPortfolioMaterializedStore materializedStore;
    private final ManualPortfolioFingerprintService fingerprintService;
    private final ManualPortfolioViewAssembler viewAssembler;
    private final ManualPortfolioService manualPortfolioService;
    private final ManualPortfolioPositionRepository positionRepository;

    private final ExecutorService gapFillExecutor = Executors.newFixedThreadPool(2);
    private final Set<Long> gapFillInFlight = ConcurrentHashMap.newKeySet();

    /**
     * Okuma yolunu bloklamadan eksik günlük kapanışları arka planda tamamlar.
     */
    public void scheduleGapFillIfNeeded(Long userId) {
        if (userId == null || !gapFillInFlight.add(userId)) {
            return;
        }
        gapFillExecutor.execute(() -> {
            try {
                List<ManualPortfolioPosition> positions = positionRepository.findByUserIdOrderByBuyDateAsc(userId);
                gapFillIfNeeded(userId, positions);
            } finally {
                gapFillInFlight.remove(userId);
            }
        });
    }

    /** Integration testlerinde arka plan gap-fill'in bitmesini beklemek için. */
    public void awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (gapFillInFlight.isEmpty()) {
                Thread.sleep(100);
                if (gapFillInFlight.isEmpty()) {
                    return;
                }
            }
            Thread.sleep(25);
        }
    }

    public long gapFillIfNeeded(Long userId, List<ManualPortfolioPosition> positions) {
        if (userId == null || positions == null || positions.isEmpty()) {
            return 0L;
        }
        long started = System.nanoTime();
        LocalDate today = LocalDate.now(TZ);
        LocalDate minFillFrom = today.minusDays(GAP_FILL_MAX_DAYS);
        Set<ManualPortfolioPriceTreeLoader.SymbolKey> staleKeys = new HashSet<>();
        for (ManualPortfolioPosition p : positions) {
            if (p.getType() == null || p.getSymbol() == null || p.getSymbol().isBlank()) {
                continue;
            }
            if (p.getStatus() == ManualPositionStatus.SOLD && p.getSellDate() != null && p.getSellDate().isBefore(minFillFrom)) {
                continue;
            }
            LocalDate latest = dailyCloseStore.findLatestDate(userId, p.getType(), p.getSymbol())
                    .orElse(minFillFrom.minusDays(1));
            if (latest.isBefore(today.minusDays(1))) {
                staleKeys.add(new ManualPortfolioPriceTreeLoader.SymbolKey(p.getType(), p.getSymbol().trim()));
            }
        }
        if (staleKeys.isEmpty()) {
            return 0L;
        }
        LatestPricingSnapshot pricing = marketDataClient.loadLatestPricing();
        priceTreeLoader.loadFromMdsParallel(userId, staleKeys, minFillFrom, today, pricing, true);
        refreshSnapshotPricing(userId, positions, pricing);
        long tookMs = (System.nanoTime() - started) / 1_000_000L;
        log.info("[MANUAL_GAPFILL] user={} symbols={} tookMs={}", userId, staleKeys.size(), tookMs);
        return tookMs;
    }

    private void refreshSnapshotPricing(
            Long userId,
            List<ManualPortfolioPosition> positions,
            LatestPricingSnapshot pricing
    ) {
        String fingerprint = fingerprintService.compute(positions);
        materializedStore.findReadSnapshot(userId)
                .filter(row -> row.getWarmStatus() == ManualPortfolioWarmStatus.READY
                        && fingerprint.equals(row.getPositionsFingerprint()))
                .ifPresent(row -> {
                    List<ManualPortfolioView> views = viewAssembler.toViews(positions);
                    ManualPortfolioSummaryView summary = manualPortfolioService.computeSummaryFor(positions, pricing);
                    ManualPortfolioInsightsResponse insights =
                            materializedStore.readPayload(row).insights();
                    materializedStore.refreshReadSnapshotPricing(
                            userId,
                            fingerprint,
                            views,
                            summary,
                            insights,
                            Instant.now()
                    );
                });
    }
}
