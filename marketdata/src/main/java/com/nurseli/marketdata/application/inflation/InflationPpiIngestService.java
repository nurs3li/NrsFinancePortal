package com.nurseli.marketdata.application.inflation;

import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.config.InflationPpiProperties;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InflationPpiIngestService {

    private final EvdsProperties evdsProperties;
    private final InflationPpiProperties inflationPpiProperties;
    private final EvdsDebtClient evdsDebtClient;
    private final InflationPpiPersistenceService inflationPpiPersistenceService;

    public InflationPpiSyncResult syncRange(LocalDate fromInclusive, LocalDate toInclusive) {
        if (!evdsProperties.isEnabled()) {
            return new InflationPpiSyncResult(0, 0, fromInclusive, toInclusive, "evds disabled");
        }
        if (!inflationPpiProperties.isPersistEnabled()) {
            return new InflationPpiSyncResult(0, 0, fromInclusive, toInclusive, "ppi persist disabled");
        }
        String series = evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.PPI_TR_INDEX);
        if (series == null || series.isBlank()) {
            return new InflationPpiSyncResult(0, 0, fromInclusive, toInclusive, "ppi series not configured");
        }
        if (toInclusive.isBefore(fromInclusive)) {
            return new InflationPpiSyncResult(0, 0, fromInclusive, toInclusive, "invalid range");
        }
        String trimmed = series.trim();
        List<EvdsSeriesPoint> points = evdsDebtClient.fetchSeriesAscending(trimmed, fromInclusive, toInclusive);
        List<InflationMonthMetrics> metrics = InflationIndexComputation.buildSortedMonthlySeries(points);
        int upserts = 0;
        for (InflationMonthMetrics m : metrics) {
            inflationPpiPersistenceService.upsertPpiMonth(trimmed, inflationPpiProperties.getBaseYear(), m);
            upserts++;
        }
        return new InflationPpiSyncResult(metrics.size(), upserts, fromInclusive, toInclusive, "ok");
    }

    public record InflationPpiSyncResult(int monthsProcessed, int rowsUpserted, LocalDate from, LocalDate to, String status) {}
}
