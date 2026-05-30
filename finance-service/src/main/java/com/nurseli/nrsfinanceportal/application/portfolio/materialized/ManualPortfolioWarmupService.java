package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioInsightsResponse;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioTimeseriesPointDto;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.application.ManualPortfolioService;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioInsightsService;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioViewAssembler;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioPositionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class ManualPortfolioWarmupService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    private final ManualPortfolioPositionRepository positionRepository;
    private final ManualPortfolioFingerprintService fingerprintService;
    private final ManualPortfolioMaterializedStore materializedStore;
    private final ManualPortfolioViewAssembler viewAssembler;
    private final ManualPortfolioInsightsService insightsService;
    private final MarketDataClient marketDataClient;
    private final ManualPortfolioService manualPortfolioService;
    private final ManualPortfolioPriceTreeLoader priceTreeLoader;

    private final ExecutorService warmupExecutor = Executors.newFixedThreadPool(2);
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Long, Object> userWarmLocks = new ConcurrentHashMap<>();

    public ManualPortfolioWarmupService(
            ManualPortfolioPositionRepository positionRepository,
            ManualPortfolioFingerprintService fingerprintService,
            ManualPortfolioMaterializedStore materializedStore,
            ManualPortfolioViewAssembler viewAssembler,
            ManualPortfolioInsightsService insightsService,
            MarketDataClient marketDataClient,
            @Lazy ManualPortfolioService manualPortfolioService,
            ManualPortfolioPriceTreeLoader priceTreeLoader
    ) {
        this.positionRepository = positionRepository;
        this.fingerprintService = fingerprintService;
        this.materializedStore = materializedStore;
        this.viewAssembler = viewAssembler;
        this.insightsService = insightsService;
        this.marketDataClient = marketDataClient;
        this.manualPortfolioService = manualPortfolioService;
        this.priceTreeLoader = priceTreeLoader;
    }

    public void scheduleWarmup(Long userId) {
        if (userId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueueWarmup(userId);
                }
            });
            return;
        }
        enqueueWarmup(userId);
    }

    private void enqueueWarmup(Long userId) {
        if (!inFlight.add(userId)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                warmUser(userId);
            } finally {
                inFlight.remove(userId);
            }
        }, warmupExecutor);
    }

    public void warmUser(Long userId) {
        if (userId == null) {
            return;
        }
        Object lock = userWarmLocks.computeIfAbsent(userId, ignored -> new Object());
        synchronized (lock) {
            doWarmUser(userId);
        }
    }

    /** Integration testlerinde arka plan warmup'ının bitmesini beklemek için. */
    public void awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (inFlight.isEmpty()) {
                Thread.sleep(100);
                if (inFlight.isEmpty()) {
                    return;
                }
            }
            Thread.sleep(25);
        }
    }

    private void doWarmUser(Long userId) {
        long started = System.currentTimeMillis();
        List<ManualPortfolioPosition> positions = positionRepository.findByUserIdOrderByBuyDateAsc(userId);
        String fingerprint = fingerprintService.compute(positions);
        materializedStore.markPending(userId, fingerprint);
        if (positions.isEmpty()) {
            materializedStore.saveReadSnapshot(
                    userId,
                    fingerprint,
                    List.of(),
                    manualPortfolioService.emptySummary(),
                    insightsService.buildInsightsForPositions(List.of()),
                    Instant.now()
            );
            return;
        }
        try {
            LatestPricingSnapshot pricing = marketDataClient.loadLatestPricing();
            Instant pricingAt = Instant.now();

            List<ManualPortfolioView> views = viewAssembler.toViews(positions);
            ManualPortfolioSummaryView summary = manualPortfolioService.computeSummaryFor(positions, pricing);
            ManualPortfolioInsightsResponse insights = insightsService.buildInsightsForPositions(positions);

            LocalDate today = LocalDate.now(TZ);
            LocalDate earliestBuy = positions.stream()
                    .map(ManualPortfolioPosition::getBuyDate)
                    .filter(d -> d != null)
                    .min(LocalDate::compareTo)
                    .orElse(today);
            LocalDate histFrom = earliestBuy.minusDays(14);

            Set<ManualPortfolioPriceTreeLoader.SymbolKey> keys =
                    manualPortfolioService.collectSymbolKeysForTimeseries(positions, earliestBuy, today);
            priceTreeLoader.loadFromMdsParallel(userId, keys, histFrom, today, pricing, true);

            LocalDate sixMonthStart = today.minusMonths(6);
            if (sixMonthStart.isBefore(earliestBuy)) {
                sixMonthStart = earliestBuy;
            }
            List<ManualPortfolioTimeseriesPointDto> ts6M =
                    manualPortfolioService.buildTimeseriesUsingDbPrices(positions, positions, sixMonthStart, today, userId);
            materializedStore.saveTimeseriesSnapshot(
                    userId,
                    ManualPortfolioMaterializedStore.SERIES_VALUE_6M,
                    sixMonthStart,
                    today,
                    fingerprint,
                    ts6M
            );

            List<ManualPortfolioTimeseriesPointDto> tsAll =
                    manualPortfolioService.buildTimeseriesUsingDbPrices(positions, positions, earliestBuy, today, userId);
            materializedStore.saveTimeseriesSnapshot(
                    userId,
                    ManualPortfolioMaterializedStore.SERIES_VALUE_ALL,
                    earliestBuy,
                    today,
                    fingerprint,
                    tsAll
            );

            materializedStore.saveReadSnapshot(userId, fingerprint, views, summary, insights, pricingAt);
            log.info("[MANUAL_WARMUP] user={} positions={} tookMs={}", userId, positions.size(),
                    System.currentTimeMillis() - started);
        } catch (Exception ex) {
            log.warn("[MANUAL_WARMUP] user={} failed: {}", userId, ex.getMessage(), ex);
            materializedStore.markFailed(userId, fingerprint, ex.getMessage());
        }
    }
}
