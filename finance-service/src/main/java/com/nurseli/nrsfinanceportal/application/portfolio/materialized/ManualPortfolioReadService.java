package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.nurseli.nrsfinanceportal.api.dto.*;
import com.nurseli.nrsfinanceportal.application.ManualPortfolioService;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioInsightsService;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioReadSnapshot;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioWarmStatus;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualPortfolioReadService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    private final ManualPortfolioPositionRepository positionRepository;
    private final ManualPortfolioFingerprintService fingerprintService;
    private final ManualPortfolioMaterializedStore materializedStore;
    private final ManualPortfolioGapFillService gapFillService;
    private final ManualPortfolioWarmupService warmupService;
    private final ManualPortfolioService manualPortfolioService;
    private final ManualPortfolioInsightsService insightsService;

    @Transactional(readOnly = true)
    public List<ManualPortfolioView> getViews(Long userId) {
        return resolveReadPayload(userId).views();
    }

    @Transactional(readOnly = true)
    public ManualPortfolioSummaryView getSummary(Long userId) {
        return resolveReadPayload(userId).summary();
    }

    @Transactional(readOnly = true)
    public ManualPortfolioInsightsResponse getInsights(Long userId) {
        ManualPortfolioMaterializedStore.ReadSnapshotPayload payload = resolveReadPayload(userId);
        if (payload.insights() != null) {
            return payload.insights();
        }
        List<ManualPortfolioPosition> positions = positionRepository.findByUserIdOrderByBuyDateAsc(userId);
        return insightsService.buildInsightsForPositions(positions);
    }

    @Transactional(readOnly = true)
    public List<ManualPortfolioTimeseriesPointDto> getTimeseries(Long userId, LocalDate from, LocalDate to) {
        List<ManualPortfolioPosition> positions = positionRepository.findByUserIdOrderByBuyDateAsc(userId);
        if (positions.isEmpty()) {
            return List.of();
        }
        String fingerprint = fingerprintService.compute(positions);
        Optional<String> presetKey = presetKey(from, to, positions);
        if (presetKey.isPresent()) {
            Optional<List<ManualPortfolioTimeseriesPointDto>> cached =
                    materializedStore.findTimeseries(userId, presetKey.get(), fingerprint);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        gapFillService.gapFillIfNeeded(userId, positions);
        return manualPortfolioService.buildTimeseriesUsingDbPrices(positions, positions, from, to, userId);
    }

    @Transactional(readOnly = true)
    public ManualPortfolioPageResponse getPageBundle(Long userId) {
        long gapFillMs = 0L;
        List<ManualPortfolioPosition> positions = positionRepository.findByUserIdOrderByBuyDateAsc(userId);
        String fingerprint = fingerprintService.compute(positions);

        Optional<ManualPortfolioReadSnapshot> snapshotOpt = materializedStore.findReadSnapshot(userId);
        if (snapshotOpt.isEmpty()
                || snapshotOpt.get().getWarmStatus() != ManualPortfolioWarmStatus.READY
                || !fingerprint.equals(snapshotOpt.get().getPositionsFingerprint())) {
            warmupService.scheduleWarmup(userId);
            return legacyPageFallback(userId, positions, gapFillMs, "PENDING");
        }

        gapFillService.scheduleGapFillIfNeeded(userId);
        ManualPortfolioReadSnapshot snapshot = materializedStore.findReadSnapshot(userId).orElse(snapshotOpt.get());
        ManualPortfolioMaterializedStore.ReadSnapshotPayload payload = safeReadPayload(userId, snapshot, positions);

        LocalDate today = LocalDate.now(TZ);
        LocalDate earliestBuy = positions.stream()
                .map(ManualPortfolioPosition::getBuyDate)
                .filter(d -> d != null)
                .min(LocalDate::compareTo)
                .orElse(today);
        LocalDate sixMonthStartRaw = today.minusMonths(6);
        final LocalDate sixMonthStart = sixMonthStartRaw.isBefore(earliestBuy) ? earliestBuy : sixMonthStartRaw;

        List<ManualPortfolioTimeseriesPointDto> ts6M = materializedStore
                .findTimeseries(userId, ManualPortfolioMaterializedStore.SERIES_VALUE_6M, fingerprint)
                .orElseGet(() -> manualPortfolioService.buildTimeseriesUsingDbPrices(
                        positions, positions, sixMonthStart, today, userId));

        return new ManualPortfolioPageResponse(
                payload.views(),
                payload.summary(),
                payload.insights(),
                new ManualPortfolioPageResponse.ManualPortfolioPageTimeseries(
                        "6M",
                        sixMonthStart.toString(),
                        today.toString(),
                        ts6M
                ),
                new ManualPortfolioPageResponse.ManualPortfolioPageMeta(
                        payload.warmedAt(),
                        payload.marketPricingAt(),
                        gapFillMs,
                        payload.warmStatus().name()
                )
        );
    }

    private ManualPortfolioMaterializedStore.ReadSnapshotPayload resolveReadPayload(Long userId) {
        List<ManualPortfolioPosition> positions = positionRepository.findByUserIdOrderByBuyDateAsc(userId);
        String fingerprint = fingerprintService.compute(positions);
        Optional<ManualPortfolioReadSnapshot> snapshotOpt = materializedStore.findReadSnapshot(userId);
        if (snapshotOpt.isPresent()
                && snapshotOpt.get().getWarmStatus() == ManualPortfolioWarmStatus.READY
                && fingerprint.equals(snapshotOpt.get().getPositionsFingerprint())) {
            gapFillService.scheduleGapFillIfNeeded(userId);
            ManualPortfolioReadSnapshot snapshot = materializedStore.findReadSnapshot(userId).orElse(snapshotOpt.get());
            return safeReadPayload(userId, snapshot, positions);
        }
        warmupService.scheduleWarmup(userId);
        return legacyPayload(userId, positions);
    }

    private ManualPortfolioMaterializedStore.ReadSnapshotPayload safeReadPayload(
            Long userId,
            ManualPortfolioReadSnapshot snapshot,
            List<ManualPortfolioPosition> positions
    ) {
        try {
            return materializedStore.readPayload(snapshot);
        } catch (IllegalStateException ex) {
            log.warn("[MANUAL_READ] snapshot deserialize failed user={}, fallback: {}", userId, ex.getMessage());
            warmupService.scheduleWarmup(userId);
            return legacyPayload(userId, positions);
        }
    }

    private ManualPortfolioMaterializedStore.ReadSnapshotPayload legacyPayload(
            Long userId,
            List<ManualPortfolioPosition> positions
    ) {
        ManualPortfolioService.ReadBundle bundle = manualPortfolioService.buildReadBundleForUser(userId);
        return new ManualPortfolioMaterializedStore.ReadSnapshotPayload(
                bundle.views(),
                bundle.summary(),
                null,
                null,
                ManualPortfolioWarmStatus.PENDING,
                fingerprintService.compute(positions),
                null
        );
    }

    private ManualPortfolioPageResponse legacyPageFallback(
            Long userId,
            List<ManualPortfolioPosition> positions,
            long gapFillMs,
            String warmStatus
    ) {
        ManualPortfolioService.ReadBundle bundle = manualPortfolioService.buildReadBundleForUser(userId);
        LocalDate today = LocalDate.now(TZ);
        LocalDate sixMonthStart = today.minusMonths(6);
        ManualPortfolioInsightsResponse insights = null;
        List<ManualPortfolioTimeseriesPointDto> ts = positions.isEmpty()
                ? List.of()
                : materializedStore
                        .findTimeseries(userId, ManualPortfolioMaterializedStore.SERIES_VALUE_6M, fingerprintService.compute(positions))
                        .orElseGet(() -> manualPortfolioService.buildTimeseriesUsingDbPrices(
                                positions, positions, sixMonthStart, today, userId));
        return new ManualPortfolioPageResponse(
                bundle.views(),
                bundle.summary(),
                insights,
                new ManualPortfolioPageResponse.ManualPortfolioPageTimeseries(
                        "6M",
                        sixMonthStart.toString(),
                        today.toString(),
                        ts
                ),
                new ManualPortfolioPageResponse.ManualPortfolioPageMeta(
                        null,
                        null,
                        gapFillMs,
                        warmStatus
                )
        );
    }

    private Optional<String> presetKey(LocalDate from, LocalDate to, List<ManualPortfolioPosition> positions) {
        LocalDate today = LocalDate.now(TZ);
        if (!to.equals(today)) {
            return Optional.empty();
        }
        LocalDate sixMonthStart = today.minusMonths(6);
        LocalDate earliestBuy = positions.stream()
                .map(ManualPortfolioPosition::getBuyDate)
                .filter(d -> d != null)
                .min(LocalDate::compareTo)
                .orElse(today);
        if (from.equals(sixMonthStart) || (from.isBefore(sixMonthStart) && sixMonthStart.equals(earliestBuy))) {
            return Optional.of(ManualPortfolioMaterializedStore.SERIES_VALUE_6M);
        }
        if (from.equals(earliestBuy)) {
            return Optional.of(ManualPortfolioMaterializedStore.SERIES_VALUE_ALL);
        }
        return Optional.empty();
    }
}
