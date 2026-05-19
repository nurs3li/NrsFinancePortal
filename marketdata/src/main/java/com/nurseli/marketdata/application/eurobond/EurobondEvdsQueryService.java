package com.nurseli.marketdata.application.eurobond;

import com.nurseli.marketdata.api.dto.eurobond.*;
import com.nurseli.marketdata.config.EurobondEvdsProperties;
import com.nurseli.marketdata.domain.eurobond.EurobondWeeklyObservation;
import com.nurseli.marketdata.repository.EurobondWeeklyObservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EurobondEvdsQueryService {

    private final EurobondEvdsProperties properties;
    private final EurobondSeriesRegistry registry;
    private final EurobondWeeklyObservationRepository repository;

    public EurobondOverviewResponse overview() {
        if (!properties.isEnabled()) {
            return disabledOverview();
        }
        Map<String, BigDecimal> latestByKey = loadLatestByKey();
        boolean any = latestByKey.values().stream().anyMatch(Objects::nonNull);
        LocalDate asOf = resolveAsOfDate(latestByKey);
        EurobondLatestMetricsDto metrics = new EurobondLatestMetricsDto(
                latestByKey.get("market-value"),
                latestByKey.get("book-value"),
                latestByKey.get("total-distribution"),
                latestByKey.get("remaining-maturity-short"),
                latestByKey.get("remaining-maturity-long"),
                latestByKey.get("original-maturity-short"),
                latestByKey.get("original-maturity-long"),
                latestByKey.get("currency-usd"),
                latestByKey.get("currency-eur"),
                latestByKey.get("currency-jpy"));
        EurobondShareMetricsDto shares = buildShares(metrics);
        return new EurobondOverviewResponse(
                any,
                EurobondSeriesRegistry.FREQUENCY_LABEL,
                EurobondSeriesRegistry.UNIT_LABEL,
                EurobondSeriesRegistry.SOURCE_LABEL,
                asOf,
                metrics,
                shares);
    }

    public EurobondHistoryResponse history(String seriesCode, LocalDate from, LocalDate to) {
        if (!properties.isEnabled()) {
            return emptyHistory(seriesCode);
        }
        Optional<EurobondSeriesRegistry.ResolvedSeries> spec = registry.findByCode(seriesCode);
        if (spec.isEmpty()) {
            return emptyHistory(seriesCode);
        }
        if (from == null || to == null || to.isBefore(from)) {
            return emptyHistory(seriesCode);
        }
        List<EurobondHistoryPointDto> points = repository
                .findBySeriesCodeAndObservationDateBetweenOrderByObservationDateAsc(spec.get().seriesCode(), from, to)
                .stream()
                .map(this::toPoint)
                .toList();
        return new EurobondHistoryResponse(
                !points.isEmpty(),
                spec.get().seriesCode(),
                spec.get().label(),
                EurobondSeriesRegistry.FREQUENCY_LABEL,
                EurobondSeriesRegistry.UNIT_LABEL,
                EurobondSeriesRegistry.SOURCE_LABEL,
                points);
    }

    public EurobondBatchHistoryResponse batchHistory(List<String> seriesCodes, LocalDate from, LocalDate to) {
        if (!properties.isEnabled()) {
            return new EurobondBatchHistoryResponse(
                    false,
                    EurobondSeriesRegistry.FREQUENCY_LABEL,
                    EurobondSeriesRegistry.UNIT_LABEL,
                    EurobondSeriesRegistry.SOURCE_LABEL,
                    Map.of());
        }
        if (from == null || to == null || to.isBefore(from) || seriesCodes == null || seriesCodes.isEmpty()) {
            return new EurobondBatchHistoryResponse(
                    false,
                    EurobondSeriesRegistry.FREQUENCY_LABEL,
                    EurobondSeriesRegistry.UNIT_LABEL,
                    EurobondSeriesRegistry.SOURCE_LABEL,
                    Map.of());
        }
        List<EurobondSeriesRegistry.ResolvedSeries> specs = registry.resolveCodes(seriesCodes);
        if (specs.isEmpty()) {
            return new EurobondBatchHistoryResponse(
                    false,
                    EurobondSeriesRegistry.FREQUENCY_LABEL,
                    EurobondSeriesRegistry.UNIT_LABEL,
                    EurobondSeriesRegistry.SOURCE_LABEL,
                    Map.of());
        }
        List<String> codes = specs.stream().map(EurobondSeriesRegistry.ResolvedSeries::seriesCode).toList();
        List<EurobondWeeklyObservation> rows =
                repository.findBySeriesCodeInAndObservationDateBetweenOrderBySeriesCodeAscObservationDateAsc(
                        codes, from, to);
        Map<String, List<EurobondWeeklyObservation>> grouped = rows.stream()
                .collect(Collectors.groupingBy(EurobondWeeklyObservation::getSeriesCode, LinkedHashMap::new, Collectors.toList()));
        Map<String, EurobondHistorySeriesDto> out = new LinkedHashMap<>();
        boolean any = false;
        for (EurobondSeriesRegistry.ResolvedSeries spec : specs) {
            List<EurobondHistoryPointDto> points = grouped.getOrDefault(spec.seriesCode(), List.of()).stream()
                    .map(this::toPoint)
                    .toList();
            if (!points.isEmpty()) {
                any = true;
            }
            out.put(
                    spec.seriesCode(),
                    new EurobondHistorySeriesDto(spec.seriesCode(), spec.label(), spec.seriesKey(), points));
        }
        return new EurobondBatchHistoryResponse(
                any,
                EurobondSeriesRegistry.FREQUENCY_LABEL,
                EurobondSeriesRegistry.UNIT_LABEL,
                EurobondSeriesRegistry.SOURCE_LABEL,
                out);
    }

    private EurobondOverviewResponse disabledOverview() {
        return new EurobondOverviewResponse(
                false,
                EurobondSeriesRegistry.FREQUENCY_LABEL,
                EurobondSeriesRegistry.UNIT_LABEL,
                EurobondSeriesRegistry.SOURCE_LABEL,
                null,
                EurobondLatestMetricsDto.empty(),
                EurobondShareMetricsDto.empty());
    }

    private EurobondHistoryResponse emptyHistory(String seriesCode) {
        return new EurobondHistoryResponse(
                false,
                seriesCode,
                null,
                EurobondSeriesRegistry.FREQUENCY_LABEL,
                EurobondSeriesRegistry.UNIT_LABEL,
                EurobondSeriesRegistry.SOURCE_LABEL,
                List.of());
    }

    private Map<String, BigDecimal> loadLatestByKey() {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (EurobondSeriesRegistry.ResolvedSeries spec : registry.all()) {
            BigDecimal v = repository
                    .findTopBySeriesCodeOrderByObservationDateDesc(spec.seriesCode())
                    .map(EurobondWeeklyObservation::getValue)
                    .orElse(null);
            out.put(spec.seriesKey(), v);
        }
        return out;
    }

    private LocalDate resolveAsOfDate(Map<String, BigDecimal> latestByKey) {
        LocalDate max = null;
        for (EurobondSeriesRegistry.ResolvedSeries spec : registry.all()) {
            Optional<EurobondWeeklyObservation> row =
                    repository.findTopBySeriesCodeOrderByObservationDateDesc(spec.seriesCode());
            if (row.isPresent()) {
                LocalDate d = row.get().getObservationDate();
                if (max == null || d.isAfter(max)) {
                    max = d;
                }
            }
        }
        return max;
    }

    static EurobondShareMetricsDto buildShares(EurobondLatestMetricsDto m) {
        BigDecimal total = m.totalDistribution();
        return new EurobondShareMetricsDto(
                sharePct(m.usdIssues(), total),
                sharePct(m.eurIssues(), total),
                sharePct(m.jpyIssues(), total),
                sharePct(m.remainingLong(), total),
                sharePct(m.remainingShort(), total));
    }

    static BigDecimal sharePct(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.signum() <= 0) {
            return null;
        }
        return part
                .multiply(BigDecimal.valueOf(100))
                .divide(total, 4, RoundingMode.HALF_UP);
    }

    private EurobondHistoryPointDto toPoint(EurobondWeeklyObservation o) {
        return new EurobondHistoryPointDto(o.getObservationDate(), o.getValue());
    }
}
