package com.nurseli.marketdata.application.inflation;

import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.config.InflationCpiProperties;
import com.nurseli.marketdata.config.InflationPpiProperties;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.domain.inflation.InflationIndexMonthlyEntity;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import com.nurseli.marketdata.infrastructure.persistence.InflationIndexMonthlyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InflationIndexQueryService {

    private final InflationIndexMonthlyRepository repository;
    private final EvdsProperties evdsProperties;
    private final InflationCpiProperties inflationCpiProperties;
    private final InflationPpiProperties inflationPpiProperties;

    public boolean isPersistReadEnabled(InflationIndicatorType type) {
        return switch (type) {
            case CPI -> inflationCpiProperties.isPersistEnabled();
            case PPI -> inflationPpiProperties.isPersistEnabled();
        };
    }

    public String resolveSeriesCode(InflationIndicatorType type) {
        String key = type == InflationIndicatorType.CPI
                ? EvdsSeriesLogicalNames.CPI_TR_INDEX
                : EvdsSeriesLogicalNames.PPI_TR_INDEX;
        return evdsProperties.getSeriesCode(key);
    }

    public List<InflationMonthMetrics> metricsBetween(
            InflationIndicatorType type,
            YearMonth from,
            YearMonth to
    ) {
        String seriesCode = resolveSeriesCode(type);
        if (!isPersistReadEnabled(type) || seriesCode == null || seriesCode.isBlank()) {
            return List.of();
        }
        LocalDate start = from.atDay(1);
        LocalDate end = to.atEndOfMonth();
        List<InflationIndexMonthlyEntity> rows = repository
                .findByIndicatorTypeAndSeriesCodeAndObservationMonthBetweenOrderByObservationMonthAsc(
                        type,
                        seriesCode.trim(),
                        start,
                        end
                );
        if (rows.isEmpty()) {
            return List.of();
        }
        List<InflationMonthMetrics> out = new ArrayList<>(rows.size());
        for (InflationIndexMonthlyEntity row : rows) {
            YearMonth ym = YearMonth.from(row.getObservationMonth());
            out.add(new InflationMonthMetrics(
                    ym,
                    row.getIndexValue(),
                    row.getMonthlyChangePct(),
                    row.getAnnualChangePct()
            ));
        }
        return out;
    }

    public List<EvdsSeriesPoint> indexPointsBetween(InflationIndicatorType type, LocalDate from, LocalDate to) {
        String seriesCode = resolveSeriesCode(type);
        if (!isPersistReadEnabled(type) || seriesCode == null || seriesCode.isBlank()) {
            return List.of();
        }
        List<InflationIndexMonthlyEntity> rows = repository
                .findByIndicatorTypeAndSeriesCodeAndObservationMonthBetweenOrderByObservationMonthAsc(
                        type,
                        seriesCode.trim(),
                        from.withDayOfMonth(1),
                        to
                );
        List<EvdsSeriesPoint> out = new ArrayList<>(rows.size());
        for (InflationIndexMonthlyEntity row : rows) {
            LocalDate d = row.getObservationMonth();
            if (d == null || row.getIndexValue() == null) {
                continue;
            }
            out.add(new EvdsSeriesPoint(d.atStartOfDay(), row.getIndexValue()));
        }
        return out;
    }

    public Optional<InflationMonthMetrics> latestMetrics(InflationIndicatorType type) {
        String seriesCode = resolveSeriesCode(type);
        if (!isPersistReadEnabled(type) || seriesCode == null || seriesCode.isBlank()) {
            return Optional.empty();
        }
        return repository.findFirstByIndicatorTypeAndSeriesCodeOrderByObservationMonthDesc(type, seriesCode.trim())
                .map(row -> new InflationMonthMetrics(
                        YearMonth.from(row.getObservationMonth()),
                        row.getIndexValue(),
                        row.getMonthlyChangePct(),
                        row.getAnnualChangePct()
                ));
    }

    public SeriesSummary summarize(InflationIndicatorType type) {
        String seriesCode = resolveSeriesCode(type);
        if (seriesCode == null || seriesCode.isBlank()) {
            return new SeriesSummary(type.name(), null, null, null, 0);
        }
        String trimmed = seriesCode.trim();
        long count = repository.countByIndicatorTypeAndSeriesCode(type, trimmed);
        return repository.findFirstByIndicatorTypeAndSeriesCodeOrderByObservationMonthDesc(type, trimmed)
                .map(row -> new SeriesSummary(
                        type.name(),
                        trimmed,
                        row.getObservationMonth(),
                        row.getIndexValue(),
                        (int) count
                ))
                .orElseGet(() -> new SeriesSummary(type.name(), trimmed, null, null, 0));
    }

    public record SeriesSummary(
            String indicator,
            String code,
            LocalDate latestDate,
            BigDecimal latestValue,
            int observationCount
    ) {}
}
