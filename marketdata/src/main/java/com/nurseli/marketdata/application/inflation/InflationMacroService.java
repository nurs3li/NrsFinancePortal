package com.nurseli.marketdata.application.inflation;

import com.nurseli.marketdata.api.dto.CpiTrMacroResponse;
import com.nurseli.marketdata.api.dto.inflation.InflationCompareResponse;
import com.nurseli.marketdata.api.dto.inflation.InflationCompareRowDto;
import com.nurseli.marketdata.api.dto.inflation.InflationHistoryResponse;
import com.nurseli.marketdata.api.dto.inflation.InflationHistoryRowDto;
import com.nurseli.marketdata.api.dto.inflation.InflationIndicatorSnapshotDto;
import com.nurseli.marketdata.api.dto.inflation.InflationLatestResponse;
import com.nurseli.marketdata.application.CpiTrComputation;
import com.nurseli.marketdata.application.EvdsCpiTrService;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.InflationCpiProperties;
import com.nurseli.marketdata.config.InflationPpiProperties;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InflationMacroService {

    private static final String METHODOLOGY_NOTE =
            "ÜFE (Yİ-ÜFE) üretici maliyet enflasyonunu; TÜFE tüketici fiyat enflasyonunu gösterir. "
                    + "Grafiklerde yıllık değişim yüzdeleri (YoY) önceliklidir; endeks seviyesi enflasyon oranı değildir.";

    private final EvdsProperties evdsProperties;
    private final InflationCpiProperties inflationCpiProperties;
    private final InflationPpiProperties inflationPpiProperties;
    private final EvdsDebtClient evdsDebtClient;
    private final EvdsCpiTrService evdsCpiTrService;
    private final InflationIndexQueryService inflationIndexQueryService;

    @Cacheable(cacheNames = "market:macro:inflation", key = "'latest'")
    public InflationLatestResponse latest() {
        InflationIndicatorSnapshotDto cpi = mapCpiSnapshot(evdsCpiTrService.latestCpiTr());
        InflationIndicatorSnapshotDto ppi = loadPpiSnapshot();
        return new InflationLatestResponse(
                cpi,
                ppi,
                METHODOLOGY_NOTE,
                "Kaynak: EVDS (TÜFE genel endeks, Yİ-ÜFE üretici fiyat endeksi)."
        );
    }

    @Cacheable(cacheNames = "market:macro:inflation", key = "'history-' + #type + '-' + #from + '-' + #to")
    public InflationHistoryResponse history(InflationIndicatorType type, YearMonth from, YearMonth to) {
        List<InflationHistoryRowDto> rows = buildHistoryRows(type, from, to);
        return new InflationHistoryResponse(type.name(), rows);
    }

    @Cacheable(cacheNames = "market:macro:inflation", key = "'compare-' + #from + '-' + #to")
    public InflationCompareResponse compare(YearMonth from, YearMonth to) {
        Map<YearMonth, InflationMonthMetrics> cpi = indexByMonth(
                metricsInWindow(InflationIndicatorType.CPI, from, to)
        );
        Map<YearMonth, InflationMonthMetrics> ppi = indexByMonth(
                metricsInWindow(InflationIndicatorType.PPI, from, to)
        );
        List<InflationCompareRowDto> rows = new ArrayList<>();
        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
            InflationMonthMetrics c = cpi.get(ym);
            InflationMonthMetrics p = ppi.get(ym);
            rows.add(new InflationCompareRowDto(
                    ym.atDay(1),
                    c != null ? c.monthlyChangePercent() : null,
                    c != null ? c.annualChangePercent() : null,
                    p != null ? p.monthlyChangePercent() : null,
                    p != null ? p.annualChangePercent() : null
            ));
        }
        return new InflationCompareResponse(rows);
    }

    private InflationIndicatorSnapshotDto mapCpiSnapshot(Optional<CpiTrMacroResponse> opt) {
        return opt.map(cpi -> new InflationIndicatorSnapshotDto(
                "INFLATION",
                "CPI",
                "EVDS",
                "MONTHLY",
                "INDEX",
                inflationCpiProperties.getBaseYear(),
                "TR",
                cpi.seriesCode(),
                cpi.indexMonth(),
                cpi.cpiTrIndex(),
                cpi.cpiTrMonthly(),
                cpi.cpiTrAnnual(),
                cpi.annualNote()
        )).orElse(null);
    }

    private InflationIndicatorSnapshotDto loadPpiSnapshot() {
        Optional<InflationMonthMetrics> db = inflationIndexQueryService.latestMetrics(InflationIndicatorType.PPI);
        Optional<InflationMonthMetrics> evds = loadPpiMetricsFromEvds();
        if (db.isPresent() && evds.isPresent() && evds.get().yearMonth().isAfter(db.get().yearMonth())) {
            return mapMetricsSnapshot(InflationIndicatorType.PPI, evds.get());
        }
        if (db.isPresent()) {
            return mapMetricsSnapshot(InflationIndicatorType.PPI, db.get());
        }
        return evds.map(m -> mapMetricsSnapshot(InflationIndicatorType.PPI, m)).orElse(null);
    }

    private InflationIndicatorSnapshotDto mapMetricsSnapshot(InflationIndicatorType type, InflationMonthMetrics m) {
        String seriesCode = inflationIndexQueryService.resolveSeriesCode(type);
        Integer baseYear = type == InflationIndicatorType.PPI
                ? inflationPpiProperties.getBaseYear()
                : inflationCpiProperties.getBaseYear();
        String note = m.annualChangePercent() == null
                ? "Yıllık kıyas için en az 13 ay endeks verisi gerekir."
                : null;
        return new InflationIndicatorSnapshotDto(
                "INFLATION",
                type.name(),
                "EVDS",
                "MONTHLY",
                "INDEX",
                baseYear,
                "TR",
                seriesCode,
                m.monthStart(),
                m.indexValue(),
                m.monthlyChangePercent(),
                m.annualChangePercent(),
                note
        );
    }

    private Optional<InflationMonthMetrics> loadPpiMetricsFromEvds() {
        if (!evdsProperties.isEnabled()) {
            return Optional.empty();
        }
        String series = inflationIndexQueryService.resolveSeriesCode(InflationIndicatorType.PPI);
        if (series == null || series.isBlank()) {
            return Optional.empty();
        }
        String trimmed = series.trim();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(36);
        List<EvdsSeriesPoint> points = evdsDebtClient.fetchSeriesAscending(trimmed, start, end);
        return InflationIndexComputation.latestMetricsOrEmpty(CpiTrComputation.sortAsc(points));
    }

    private List<InflationHistoryRowDto> buildHistoryRows(InflationIndicatorType type, YearMonth from, YearMonth to) {
        List<InflationMonthMetrics> metrics = metricsInWindow(type, from, to);
        List<InflationHistoryRowDto> out = new ArrayList<>();
        Integer baseYear = type == InflationIndicatorType.PPI
                ? inflationPpiProperties.getBaseYear()
                : inflationCpiProperties.getBaseYear();
        String seriesCode = inflationIndexQueryService.resolveSeriesCode(type);
        if (seriesCode == null) {
            return out;
        }
        for (InflationMonthMetrics m : metrics) {
            if (m.yearMonth().isBefore(from) || m.yearMonth().isAfter(to)) {
                continue;
            }
            out.add(new InflationHistoryRowDto(
                    "INFLATION",
                    type.name(),
                    "EVDS",
                    "MONTHLY",
                    "INDEX",
                    baseYear,
                    "TR",
                    seriesCode,
                    m.monthStart(),
                    m.indexValue(),
                    m.monthlyChangePercent(),
                    m.annualChangePercent()
            ));
        }
        return out;
    }

    private List<InflationMonthMetrics> metricsInWindow(InflationIndicatorType type, YearMonth from, YearMonth to) {
        List<InflationMonthMetrics> db = inflationIndexQueryService.metricsBetween(type, from, to);
        List<InflationMonthMetrics> evds = fetchMetricsFromEvds(type, from, to);
        if (db.isEmpty()) {
            return evds;
        }
        if (evds.isEmpty()) {
            return db;
        }
        YearMonth dbLast = db.getLast().yearMonth();
        YearMonth evdsLast = evds.getLast().yearMonth();
        return evdsLast.isAfter(dbLast) ? evds : db;
    }

    private List<InflationMonthMetrics> fetchMetricsFromEvds(InflationIndicatorType type, YearMonth from, YearMonth to) {
        if (!evdsProperties.isEnabled()) {
            return List.of();
        }
        String series = inflationIndexQueryService.resolveSeriesCode(type);
        if (series == null || series.isBlank()) {
            return List.of();
        }
        LocalDate fetchStart = from.minusMonths(14).atDay(1);
        LocalDate fetchEnd = to.atEndOfMonth();
        List<EvdsSeriesPoint> points = evdsDebtClient.fetchSeriesAscending(series.trim(), fetchStart, fetchEnd);
        return InflationIndexComputation.buildSortedMonthlySeries(CpiTrComputation.sortAsc(points));
    }

    private static Map<YearMonth, InflationMonthMetrics> indexByMonth(List<InflationMonthMetrics> metrics) {
        Map<YearMonth, InflationMonthMetrics> map = new LinkedHashMap<>();
        for (InflationMonthMetrics m : metrics) {
            map.put(m.yearMonth(), m);
        }
        return map;
    }
}
