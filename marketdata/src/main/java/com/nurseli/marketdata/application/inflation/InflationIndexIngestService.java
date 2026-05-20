package com.nurseli.marketdata.application.inflation;

import com.nurseli.marketdata.api.dto.inflation.InflationBackfillResponse;
import com.nurseli.marketdata.api.dto.inflation.InflationSeriesSyncSummaryDto;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.config.InflationCpiProperties;
import com.nurseli.marketdata.config.InflationPpiProperties;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InflationIndexIngestService {

    private final EvdsProperties evdsProperties;
    private final InflationCpiProperties inflationCpiProperties;
    private final InflationPpiProperties inflationPpiProperties;
    private final EvdsDebtClient evdsDebtClient;
    private final InflationIndexPersistenceService persistenceService;
    private final InflationIndexQueryService queryService;
    private final InflationMacroCacheService inflationMacroCacheService;

    @Transactional
    public InflationBackfillResponse backfill(LocalDate fromInclusive, LocalDate toInclusive) {
        return backfill(fromInclusive, toInclusive, true, true);
    }

    @Transactional
    public InflationBackfillResponse backfill(
            LocalDate fromInclusive,
            LocalDate toInclusive,
            boolean includeCpi,
            boolean includePpi
    ) {
        if (!evdsProperties.isEnabled()) {
            return emptyResult(fromInclusive, toInclusive, "evds disabled");
        }
        if (toInclusive.isBefore(fromInclusive)) {
            return emptyResult(fromInclusive, toInclusive, "invalid range");
        }
        List<InflationSeriesSyncSummaryDto> series = new ArrayList<>();
        if (includeCpi && inflationCpiProperties.isPersistEnabled()) {
            series.add(syncIndicator(InflationIndicatorType.CPI, inflationCpiProperties.getBaseYear(), fromInclusive, toInclusive));
        }
        if (includePpi && inflationPpiProperties.isPersistEnabled()) {
            series.add(syncIndicator(InflationIndicatorType.PPI, inflationPpiProperties.getBaseYear(), fromInclusive, toInclusive));
        }
        inflationMacroCacheService.evictInflationCaches();
        String status = series.stream().anyMatch(s -> "ok".equals(s.status())) ? "ok" : "partial";
        if (series.isEmpty()) {
            status = "no-op (persist disabled)";
        }
        log.info("[INFLATION] backfill {} → {} status={} series={}", fromInclusive, toInclusive, status, series);
        return new InflationBackfillResponse(status, fromInclusive, toInclusive, series);
    }

    public InflationPpiIngestService.InflationPpiSyncResult syncPpiRange(LocalDate fromInclusive, LocalDate toInclusive) {
        InflationSeriesSyncSummaryDto s = syncIndicator(
                InflationIndicatorType.PPI,
                inflationPpiProperties.getBaseYear(),
                fromInclusive,
                toInclusive
        );
        inflationMacroCacheService.evictInflationCaches();
        return new InflationPpiIngestService.InflationPpiSyncResult(
                s.observationCount(),
                s.rowsUpserted(),
                fromInclusive,
                toInclusive,
                s.status()
        );
    }

    private InflationSeriesSyncSummaryDto syncIndicator(
            InflationIndicatorType type,
            int baseYear,
            LocalDate fromInclusive,
            LocalDate toInclusive
    ) {
        String logicalKey = type == InflationIndicatorType.CPI
                ? EvdsSeriesLogicalNames.CPI_TR_INDEX
                : EvdsSeriesLogicalNames.PPI_TR_INDEX;
        String series = evdsProperties.getSeriesCode(logicalKey);
        if (series == null || series.isBlank()) {
            return new InflationSeriesSyncSummaryDto(
                    type.name(),
                    null,
                    null,
                    null,
                    0,
                    0,
                    logicalKey + " not configured"
            );
        }
        String trimmed = series.trim();
        List<EvdsSeriesPoint> points = evdsDebtClient.fetchSeriesAscending(trimmed, fromInclusive, toInclusive);
        List<InflationMonthMetrics> metrics = InflationIndexComputation.buildSortedMonthlySeries(points);
        int upserts = 0;
        for (InflationMonthMetrics m : metrics) {
            persistenceService.upsertMonth(type, trimmed, baseYear, m);
            upserts++;
        }
        InflationIndexQueryService.SeriesSummary summary = queryService.summarize(type);
        log.info(
                "[INFLATION] sync {} code={} latestDate={} latestValue={} observationCount={} upserted={}",
                type,
                summary.code(),
                summary.latestDate(),
                summary.latestValue(),
                summary.observationCount(),
                upserts
        );
        return new InflationSeriesSyncSummaryDto(
                type.name(),
                summary.code(),
                summary.latestDate(),
                summary.latestValue(),
                summary.observationCount(),
                upserts,
                metrics.isEmpty() ? "empty" : "ok"
        );
    }

    private static InflationBackfillResponse emptyResult(LocalDate from, LocalDate to, String status) {
        return new InflationBackfillResponse(status, from, to, List.of());
    }
}
