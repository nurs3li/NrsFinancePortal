package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.CpiTrMacroResponse;
import com.nurseli.marketdata.application.inflation.InflationIndexComputation;
import com.nurseli.marketdata.application.inflation.InflationIndexQueryService;
import com.nurseli.marketdata.application.inflation.InflationMonthMetrics;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EvdsCpiTrService {

    private final EvdsProperties evdsProperties;
    private final EvdsDebtClient evdsDebtClient;
    private final InflationIndexQueryService inflationIndexQueryService;

    /**
     * Önce DB (persist açıksa), yoksa EVDS'ten son ayın seviyesi ile MoM/YoY yüzdeleri.
     */
    public Optional<CpiTrMacroResponse> latestCpiTr() {
        Optional<InflationMonthMetrics> db = inflationIndexQueryService.latestMetrics(InflationIndicatorType.CPI);
        Optional<CpiTrMacroResponse> evds = fetchLatestFromEvds();
        if (db.isPresent() && evds.isPresent()) {
            YearMonth dbYm = db.get().yearMonth();
            YearMonth evdsYm = YearMonth.from(evds.get().indexMonth());
            if (evdsYm.isAfter(dbYm)) {
                return evds;
            }
        }
        if (db.isPresent()) {
            String series = inflationIndexQueryService.resolveSeriesCode(InflationIndicatorType.CPI);
            InflationMonthMetrics m = db.get();
            String note = m.annualChangePercent() == null
                    ? "Yıllık kıyas için en az 13 ay endeks verisi gerekir."
                    : null;
            return Optional.of(new CpiTrMacroResponse(
                    series,
                    m.monthStart(),
                    m.indexValue(),
                    m.monthlyChangePercent(),
                    m.annualChangePercent(),
                    note
            ));
        }
        return fetchLatestFromEvds();
    }

    private Optional<CpiTrMacroResponse> fetchLatestFromEvds() {
        if (!evdsProperties.isEnabled()) {
            return Optional.empty();
        }
        String series = evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.CPI_TR_INDEX);
        if (series == null || series.isBlank()) {
            return Optional.empty();
        }
        String trimmed = series.trim();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(24).minusDays(15);
        List<EvdsSeriesPoint> points = evdsDebtClient.fetchSeriesAscending(trimmed, start, end);
        return CpiTrComputation.fromAscendingPoints(points, trimmed);
    }
}
