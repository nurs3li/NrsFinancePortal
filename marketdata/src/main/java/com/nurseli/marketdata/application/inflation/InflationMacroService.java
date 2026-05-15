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
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.config.InflationPpiProperties;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import lombok.RequiredArgsConstructor;
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
    private final InflationPpiProperties inflationPpiProperties;
    private final EvdsDebtClient evdsDebtClient;
    private final EvdsCpiTrService evdsCpiTrService;

    public InflationLatestResponse latest() {
        InflationIndicatorSnapshotDto cpi = mapCpiSnapshot(evdsCpiTrService.latestCpiTr());
        InflationIndicatorSnapshotDto ppi = loadPpiSnapshotFromEvds();
        return new InflationLatestResponse(
                cpi,
                ppi,
                METHODOLOGY_NOTE,
                "Kaynak: EVDS (TÜFE genel endeks, Yİ-ÜFE üretici fiyat endeksi)."
        );
    }

    public InflationHistoryResponse history(InflationIndicatorType type, YearMonth from, YearMonth to) {
        List<InflationHistoryRowDto> rows = buildHistoryRows(type, from, to);
        return new InflationHistoryResponse(type.name(), rows);
    }

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
                null,
                "TR",
                cpi.seriesCode(),
                cpi.indexMonth(),
                cpi.cpiTrIndex(),
                cpi.cpiTrMonthly(),
                cpi.cpiTrAnnual(),
                cpi.annualNote()
        )).orElse(null);
    }

    private InflationIndicatorSnapshotDto loadPpiSnapshotFromEvds() {
        if (!evdsProperties.isEnabled()) {
            return null;
        }
        String series = evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.PPI_TR_INDEX);
        if (series == null || series.isBlank()) {
            return null;
        }
        String trimmed = series.trim();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(36);
        List<EvdsSeriesPoint> points = evdsDebtClient.fetchSeriesAscending(trimmed, start, end);
        Optional<InflationMonthMetrics> last = InflationIndexComputation.latestMetricsOrEmpty(CpiTrComputation.sortAsc(points));
        if (last.isEmpty()) {
            return null;
        }
        InflationMonthMetrics m = last.get();
        String note = m.annualChangePercent() == null
                ? "Yıllık kıyas için en az 13 ay endeks verisi gerekir."
                : null;
        return new InflationIndicatorSnapshotDto(
                "INFLATION",
                "PPI",
                "EVDS",
                "MONTHLY",
                "INDEX",
                inflationPpiProperties.getBaseYear(),
                "TR",
                trimmed,
                m.monthStart(),
                m.indexValue(),
                m.monthlyChangePercent(),
                m.annualChangePercent(),
                note
        );
    }

    private List<InflationHistoryRowDto> buildHistoryRows(InflationIndicatorType type, YearMonth from, YearMonth to) {
        List<InflationMonthMetrics> metrics = metricsInWindow(type, from, to);
        List<InflationHistoryRowDto> out = new ArrayList<>();
        Integer baseYear = type == InflationIndicatorType.PPI ? inflationPpiProperties.getBaseYear() : null;
        String seriesCode = resolveSeriesCode(type);
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
        if (!evdsProperties.isEnabled()) {
            return List.of();
        }
        String series = resolveSeriesCode(type);
        if (series == null || series.isBlank()) {
            return List.of();
        }
        LocalDate fetchStart = from.minusMonths(14).atDay(1);
        LocalDate fetchEnd = to.atEndOfMonth();
        List<EvdsSeriesPoint> points = evdsDebtClient.fetchSeriesAscending(series.trim(), fetchStart, fetchEnd);
        return InflationIndexComputation.buildSortedMonthlySeries(CpiTrComputation.sortAsc(points));
    }

    private String resolveSeriesCode(InflationIndicatorType type) {
        String key = type == InflationIndicatorType.CPI
                ? EvdsSeriesLogicalNames.CPI_TR_INDEX
                : EvdsSeriesLogicalNames.PPI_TR_INDEX;
        return evdsProperties.getSeriesCode(key);
    }

    private static Map<YearMonth, InflationMonthMetrics> indexByMonth(List<InflationMonthMetrics> metrics) {
        Map<YearMonth, InflationMonthMetrics> map = new LinkedHashMap<>();
        for (InflationMonthMetrics m : metrics) {
            map.put(m.yearMonth(), m);
        }
        return map;
    }
}
