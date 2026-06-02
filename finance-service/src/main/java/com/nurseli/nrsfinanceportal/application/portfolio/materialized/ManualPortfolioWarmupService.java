package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioInsightsResponse;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioTimeseriesPointDto;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioService;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class ManualPortfolioWarmupService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final BigDecimal SANITY_RATIO_MIN = new BigDecimal("0.5");
    private static final BigDecimal SANITY_RATIO_MAX = new BigDecimal("1.5");

    private record WarmupRequest(WarmupTrigger trigger, Long positionId) {}

    private record TimeseriesBuildResult(
            List<ManualPortfolioTimeseriesPointDto> points,
            String mode,
            LocalDate mergeFrom
    ) {}

    private final ManualPortfolioPositionRepository positionRepository;
    private final ManualPortfolioFingerprintService fingerprintService;
    private final ManualPortfolioMaterializedStore materializedStore;
    private final ManualPortfolioViewAssembler viewAssembler;
    private final ManualPortfolioInsightsService insightsService;
    private final MarketDataClient marketDataClient;
    private final ManualPortfolioService manualPortfolioService;
    private final ManualPortfolioPriceTreeLoader priceTreeLoader;
    private final ManualPortfolioTimeseriesIncrementalService incrementalService;
    private final ManualSymbolDailyCloseStore dailyCloseStore;

    private final ExecutorService warmupExecutor = Executors.newFixedThreadPool(2);
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Long, Object> userWarmLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, WarmupRequest> pendingRequests = new ConcurrentHashMap<>();

    public ManualPortfolioWarmupService(
            ManualPortfolioPositionRepository positionRepository,
            ManualPortfolioFingerprintService fingerprintService,
            ManualPortfolioMaterializedStore materializedStore,
            ManualPortfolioViewAssembler viewAssembler,
            ManualPortfolioInsightsService insightsService,
            MarketDataClient marketDataClient,
            @Lazy ManualPortfolioService manualPortfolioService,
            ManualPortfolioPriceTreeLoader priceTreeLoader,
            ManualPortfolioTimeseriesIncrementalService incrementalService,
            ManualSymbolDailyCloseStore dailyCloseStore
    ) {
        this.positionRepository = positionRepository;
        this.fingerprintService = fingerprintService;
        this.materializedStore = materializedStore;
        this.viewAssembler = viewAssembler;
        this.insightsService = insightsService;
        this.marketDataClient = marketDataClient;
        this.manualPortfolioService = manualPortfolioService;
        this.priceTreeLoader = priceTreeLoader;
        this.incrementalService = incrementalService;
        this.dailyCloseStore = dailyCloseStore;
    }

    public boolean isInFlight(Long userId) {
        return userId != null && inFlight.contains(userId);
    }

    public void scheduleWarmup(Long userId) {
        scheduleWarmup(userId, WarmupTrigger.FULL, null);
    }

    public void scheduleWarmup(Long userId, WarmupTrigger trigger, Long positionId) {
        if (userId == null) {
            return;
        }
        pendingRequests.put(userId, new WarmupRequest(
                trigger != null ? trigger : WarmupTrigger.FULL,
                positionId
        ));
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
        WarmupRequest request = pendingRequests.remove(userId);
        if (request == null) {
            request = new WarmupRequest(WarmupTrigger.FULL, null);
        }

        List<ManualPortfolioPosition> positions = positionRepository.findByUserIdOrderByBuyDateAsc(userId);
        String fingerprint = fingerprintService.compute(positions);

        if (positions.isEmpty()) {
            materializedStore.deleteTimeseriesForUser(userId);
            dailyCloseStore.deleteForUser(userId);
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

        materializedStore.markPending(userId, fingerprint);

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
            LocalDate oneYearStart = today.minusYears(1);
            if (oneYearStart.isBefore(earliestBuy)) {
                oneYearStart = earliestBuy;
            }

            String tsMode = "full";
            LocalDate mergeFrom = null;
            List<ManualPortfolioTimeseriesPointDto> ts1Y;

            try {
                priceTreeLoader.loadMissingDailyCloses(userId, positions, today, pricing, oneYearStart);

                Optional<List<ManualPortfolioTimeseriesPointDto>> previous = resolveMergeSource(
                        userId,
                        request,
                        positions
                );

                TimeseriesBuildResult tsResult = buildTimeseries1Y(
                        userId,
                        request,
                        positions,
                        previous,
                        oneYearStart,
                        today
                );
                tsResult = ensureTimeseriesSanity(userId, positions, summary, tsResult, oneYearStart, today);
                ts1Y = tsResult.points();
                tsMode = tsResult.mode();
                mergeFrom = tsResult.mergeFrom();

                materializedStore.saveTimeseriesSnapshot(
                        userId,
                        ManualPortfolioMaterializedStore.SERIES_VALUE_1Y,
                        oneYearStart,
                        today,
                        fingerprint,
                        ts1Y
                );

                final LocalDate allStart = earliestBuy;
                final String fp = fingerprint;
                CompletableFuture.runAsync(
                        () -> buildAllTimeseriesAsync(userId, positions, fp, allStart, today),
                        warmupExecutor
                );
            } catch (Exception tsEx) {
                log.warn("[MANUAL_WARMUP] user={} timeseries phase failed: {}", userId, tsEx.getMessage(), tsEx);
            }

            materializedStore.saveReadSnapshot(userId, fingerprint, views, summary, insights, pricingAt);

            log.info(
                    "[MANUAL_WARMUP] user={} positions={} mode={} mergeFrom={} tookMs={}",
                    userId,
                    positions.size(),
                    tsMode,
                    mergeFrom,
                    System.currentTimeMillis() - started
            );
        } catch (Exception ex) {
            log.warn("[MANUAL_WARMUP] user={} failed: {}", userId, ex.getMessage(), ex);
            materializedStore.markFailed(userId, fingerprint, ex.getMessage());
        }
    }

    private Optional<List<ManualPortfolioTimeseriesPointDto>> resolveMergeSource(
            Long userId,
            WarmupRequest request,
            List<ManualPortfolioPosition> positions
    ) {
        if (request.trigger() != WarmupTrigger.ADD || request.positionId() == null) {
            return Optional.empty();
        }
        String expectedPreviousFingerprint = fingerprintService.computeExcluding(positions, request.positionId());
        return materializedStore.findTimeseriesForMerge(
                userId,
                ManualPortfolioMaterializedStore.SERIES_VALUE_1Y,
                expectedPreviousFingerprint
        );
    }

    private TimeseriesBuildResult buildTimeseries1Y(
            Long userId,
            WarmupRequest request,
            List<ManualPortfolioPosition> positions,
            Optional<List<ManualPortfolioTimeseriesPointDto>> previous,
            LocalDate oneYearStart,
            LocalDate today
    ) {
        if (request.trigger() == WarmupTrigger.ADD
                && request.positionId() != null
                && previous.isPresent()
                && !previous.get().isEmpty()) {
            ManualPortfolioPosition added = resolveAddedPosition(positions, request.positionId());
            if (added != null && incrementalService.canIncrementalAdd(previous.get(), added)) {
                try {
                    List<ManualPortfolioTimeseriesPointDto> delta = manualPortfolioService
                            .buildTimeseriesForPositionsSubset(
                                    positions,
                                    List.of(added),
                                    oneYearStart,
                                    today,
                                    userId
                            );
                    LocalDate mergeFrom = incrementalService.computeMergeFrom(oneYearStart, added);
                    List<ManualPortfolioTimeseriesPointDto> merged =
                            incrementalService.mergeAdd(previous.get(), delta, mergeFrom);
                    return new TimeseriesBuildResult(merged, "incremental", mergeFrom);
                } catch (Exception ex) {
                    log.warn(
                            "[MANUAL_WARMUP] user={} incremental merge failed, fallback full: {}",
                            userId,
                            ex.getMessage()
                    );
                }
            }
        }
        List<ManualPortfolioTimeseriesPointDto> full = manualPortfolioService.buildTimeseriesUsingDbPrices(
                positions, positions, oneYearStart, today, userId);
        return new TimeseriesBuildResult(full, "full", null);
    }

    private TimeseriesBuildResult ensureTimeseriesSanity(
            Long userId,
            List<ManualPortfolioPosition> positions,
            ManualPortfolioSummaryView summary,
            TimeseriesBuildResult built,
            LocalDate oneYearStart,
            LocalDate today
    ) {
        if (!needsSanityFallback(built.points(), summary)) {
            return built;
        }
        BigDecimal openValue = summary.getCurrentOpenValue();
        BigDecimal lastTs = lastMarketValue(built.points());
        BigDecimal ratio = lastTs.divide(openValue, 8, RoundingMode.HALF_UP);
        log.warn(
                "[MANUAL_WARMUP] user={} sanity_fail mode={} lastTs={} openValue={} ratio={} fallback=full",
                userId,
                built.mode(),
                lastTs,
                openValue,
                ratio
        );
        List<ManualPortfolioTimeseriesPointDto> full = manualPortfolioService.buildTimeseriesUsingDbPrices(
                positions, positions, oneYearStart, today, userId);
        return new TimeseriesBuildResult(full, "full", null);
    }

    private static boolean needsSanityFallback(
            List<ManualPortfolioTimeseriesPointDto> points,
            ManualPortfolioSummaryView summary
    ) {
        if (points == null || points.isEmpty() || summary == null) {
            return false;
        }
        BigDecimal openValue = summary.getCurrentOpenValue();
        if (openValue == null || openValue.signum() <= 0) {
            return false;
        }
        BigDecimal lastTs = lastMarketValue(points);
        if (lastTs == null || lastTs.signum() <= 0) {
            return false;
        }
        BigDecimal ratio = lastTs.divide(openValue, 8, RoundingMode.HALF_UP);
        return ratio.compareTo(SANITY_RATIO_MIN) < 0 || ratio.compareTo(SANITY_RATIO_MAX) > 0;
    }

    private static BigDecimal lastMarketValue(List<ManualPortfolioTimeseriesPointDto> points) {
        return points.stream()
                .filter(p -> p.date() != null && p.marketValueTry() != null && p.marketValueTry().signum() > 0)
                .max(Comparator.comparing(ManualPortfolioTimeseriesPointDto::date))
                .map(ManualPortfolioTimeseriesPointDto::marketValueTry)
                .orElse(null);
    }

    private static ManualPortfolioPosition resolveAddedPosition(
            List<ManualPortfolioPosition> positions,
            Long positionId
    ) {
        if (positionId == null) {
            return null;
        }
        return positions.stream()
                .filter(p -> positionId.equals(p.getId()))
                .findFirst()
                .orElse(null);
    }

    private void buildAllTimeseriesAsync(
            Long userId,
            List<ManualPortfolioPosition> positions,
            String fingerprint,
            LocalDate earliestBuy,
            LocalDate today
    ) {
        Object lock = userWarmLocks.computeIfAbsent(userId, ignored -> new Object());
        synchronized (lock) {
            try {
                List<ManualPortfolioTimeseriesPointDto> tsAll =
                        manualPortfolioService.buildTimeseriesUsingDbPrices(
                                positions, positions, earliestBuy, today, userId);
                materializedStore.saveTimeseriesSnapshot(
                        userId,
                        ManualPortfolioMaterializedStore.SERIES_VALUE_ALL,
                        earliestBuy,
                        today,
                        fingerprint,
                        tsAll
                );
            } catch (Exception ex) {
                log.warn("[MANUAL_WARMUP] user={} ALL timeseries async failed: {}", userId, ex.getMessage(), ex);
            }
        }
    }
}
