package com.nurseli.nrsfinanceportal.application.portfolio.materialized;



import com.nurseli.nrsfinanceportal.api.dto.*;

import com.nurseli.nrsfinanceportal.application.ManualPortfolioService;

import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioInsightsService;

import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioViewAssembler;

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

    private final ManualPortfolioViewAssembler viewAssembler;



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

        Optional<List<ManualPortfolioTimeseriesPointDto>> sliced =

                sliceTimeseriesFromCached(userId, fingerprint, from, to);

        if (sliced.isPresent()) {

            return sliced.get();

        }

        warmupService.scheduleWarmup(userId);

        gapFillService.scheduleGapFillIfNeeded(userId);

        return List.of();

    }



    @Transactional(readOnly = true)

    public ManualPortfolioPageResponse getPageBundle(Long userId) {

        long gapFillMs = 0L;

        List<ManualPortfolioPosition> positions = positionRepository.findByUserIdOrderByBuyDateAsc(userId);

        String fingerprint = fingerprintService.compute(positions);



        Optional<ManualPortfolioReadSnapshot> snapshotOpt = materializedStore.findReadSnapshot(userId);

        if (snapshotOpt.isPresent()

                && snapshotOpt.get().getWarmStatus() == ManualPortfolioWarmStatus.READY

                && fingerprint.equals(snapshotOpt.get().getPositionsFingerprint())) {

            return materializedPageResponse(userId, positions, fingerprint, snapshotOpt.get(), gapFillMs);

        }



        warmupService.scheduleWarmup(userId);

        return fastPendingPageResponse(userId, positions, gapFillMs, snapshotOpt);

    }



    private ManualPortfolioPageResponse materializedPageResponse(

            Long userId,

            List<ManualPortfolioPosition> positions,

            String fingerprint,

            ManualPortfolioReadSnapshot snapshot,

            long gapFillMs

    ) {

        gapFillService.scheduleGapFillIfNeeded(userId);

        ManualPortfolioMaterializedStore.ReadSnapshotPayload payload = safeReadPayload(userId, snapshot, positions);



        LocalDate today = LocalDate.now(TZ);

        LocalDate earliestBuy = positions.stream()

                .map(ManualPortfolioPosition::getBuyDate)

                .filter(d -> d != null)

                .min(LocalDate::compareTo)

                .orElse(today);

        LocalDate oneYearStartRaw = today.minusYears(1);

        final LocalDate oneYearStart = oneYearStartRaw.isBefore(earliestBuy) ? earliestBuy : oneYearStartRaw;



        List<ManualPortfolioTimeseriesPointDto> ts1Y = materializedStore

                .findTimeseries(userId, ManualPortfolioMaterializedStore.SERIES_VALUE_1Y, fingerprint)

                .orElse(List.of());

        if (ts1Y.isEmpty()) {

            ts1Y = materializedStore

                    .findTimeseries(userId, ManualPortfolioMaterializedStore.SERIES_VALUE_6M, fingerprint)

                    .orElse(List.of());

        }

        if (ts1Y.isEmpty() && !warmupService.isInFlight(userId)) {

            warmupService.scheduleWarmup(userId);

        }



        return new ManualPortfolioPageResponse(

                payload.views(),

                payload.summary(),

                payload.insights(),

                new ManualPortfolioPageResponse.ManualPortfolioPageTimeseries(

                        "1Y",

                        oneYearStart.toString(),

                        today.toString(),

                        ts1Y

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

        return fastPendingPayload(userId, positions, snapshotOpt);

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

            return fastPendingPayload(userId, positions, Optional.of(snapshot));

        }

    }



    private ManualPortfolioMaterializedStore.ReadSnapshotPayload fastPendingPayload(

            Long userId,

            List<ManualPortfolioPosition> positions,

            Optional<ManualPortfolioReadSnapshot> snapshotOpt

    ) {

        String fingerprint = fingerprintService.compute(positions);

        FastPendingData data = resolveFastPendingData(userId, positions, snapshotOpt, fingerprint);

        return new ManualPortfolioMaterializedStore.ReadSnapshotPayload(

                data.views(),

                data.summary(),

                data.insights(),

                null,

                ManualPortfolioWarmStatus.PENDING,

                fingerprint,

                null

        );

    }



    private ManualPortfolioPageResponse fastPendingPageResponse(

            Long userId,

            List<ManualPortfolioPosition> positions,

            long gapFillMs,

            Optional<ManualPortfolioReadSnapshot> snapshotOpt

    ) {

        String fingerprint = fingerprintService.compute(positions);

        FastPendingData data = resolveFastPendingData(userId, positions, snapshotOpt, fingerprint);

        LocalDate today = LocalDate.now(TZ);

        LocalDate oneYearStart = today.minusYears(1);

        return new ManualPortfolioPageResponse(

                data.views(),

                data.summary(),

                data.insights(),

                new ManualPortfolioPageResponse.ManualPortfolioPageTimeseries(

                        "1Y",

                        oneYearStart.toString(),

                        today.toString(),

                        List.of()

                ),

                new ManualPortfolioPageResponse.ManualPortfolioPageMeta(

                        null,

                        null,

                        gapFillMs,

                        "PENDING"

                )

        );

    }



    private FastPendingData resolveFastPendingData(

            Long userId,

            List<ManualPortfolioPosition> positions,

            Optional<ManualPortfolioReadSnapshot> snapshotOpt,

            String currentFingerprint

    ) {

        if (snapshotOpt.isPresent()

                && currentFingerprint.equals(snapshotOpt.get().getPositionsFingerprint())) {

            try {

                ManualPortfolioMaterializedStore.ReadSnapshotPayload stale =

                        materializedStore.readPayload(snapshotOpt.get());

                if (stale.views() != null && !stale.views().isEmpty() && stale.summary() != null) {

                    return new FastPendingData(stale.views(), stale.summary(), stale.insights());

                }

            } catch (IllegalStateException ex) {

                log.debug("[MANUAL_READ] stale snapshot unreadable user={}: {}", userId, ex.getMessage());

            }

        }

        if (positions.isEmpty()) {

            return new FastPendingData(

                    List.of(),

                    manualPortfolioService.emptySummary(),

                    null

            );

        }

        return new FastPendingData(

                viewAssembler.toPlaceholderViews(positions),

                manualPortfolioService.computeSummaryFor(positions, null),

                null

        );

    }



    private record FastPendingData(

            List<ManualPortfolioView> views,

            ManualPortfolioSummaryView summary,

            ManualPortfolioInsightsResponse insights

    ) {}



    private Optional<String> presetKey(LocalDate from, LocalDate to, List<ManualPortfolioPosition> positions) {

        LocalDate today = LocalDate.now(TZ);

        if (!to.equals(today)) {

            return Optional.empty();

        }

        LocalDate sixMonthStart = today.minusMonths(6);

        LocalDate oneYearStartRaw = today.minusYears(1);

        LocalDate earliestBuy = positions.stream()

                .map(ManualPortfolioPosition::getBuyDate)

                .filter(d -> d != null)

                .min(LocalDate::compareTo)

                .orElse(today);

        LocalDate oneYearStart = oneYearStartRaw.isBefore(earliestBuy) ? earliestBuy : oneYearStartRaw;

        if (from.equals(oneYearStartRaw) || (from.isBefore(oneYearStartRaw) && oneYearStart.equals(earliestBuy))) {

            return Optional.of(ManualPortfolioMaterializedStore.SERIES_VALUE_1Y);

        }

        if (from.equals(sixMonthStart) || (from.isBefore(sixMonthStart) && sixMonthStart.equals(earliestBuy))) {

            return Optional.of(ManualPortfolioMaterializedStore.SERIES_VALUE_6M);

        }

        if (from.equals(earliestBuy)) {

            return Optional.of(ManualPortfolioMaterializedStore.SERIES_VALUE_ALL);

        }

        return Optional.empty();

    }



    private Optional<List<ManualPortfolioTimeseriesPointDto>> sliceTimeseriesFromCached(

            Long userId,

            String fingerprint,

            LocalDate from,

            LocalDate to

    ) {

        LocalDate today = LocalDate.now(TZ);

        if (!to.equals(today)) {

            return Optional.empty();

        }

        for (String seriesKey : List.of(

                ManualPortfolioMaterializedStore.SERIES_VALUE_1Y,

                ManualPortfolioMaterializedStore.SERIES_VALUE_6M,

                ManualPortfolioMaterializedStore.SERIES_VALUE_ALL

        )) {

            Optional<List<ManualPortfolioTimeseriesPointDto>> cached =

                    materializedStore.findTimeseries(userId, seriesKey, fingerprint);

            if (cached.isEmpty() || cached.get().isEmpty()) {

                continue;

            }

            List<ManualPortfolioTimeseriesPointDto> sliced = cached.get().stream()

                    .filter(p -> p.date() != null && !p.date().isBefore(from) && !p.date().isAfter(to))

                    .toList();

            if (!sliced.isEmpty()) {

                return Optional.of(sliced);

            }

        }

        return Optional.empty();

    }

}


