package com.nurseli.marketdata.application.inflation;

import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Aylık endeks seviyelerinden MoM ve YoY yüzde değişimleri.
 * Eksik önceki ay veya 12 ay önceki ay için ilgili yüzde alanı null kalır.
 */
public final class InflationIndexComputation {

    private InflationIndexComputation() {}

    /**
     * Tüm aylar için sıralı metrik listesi (en az bir gözlem ayı yeterli).
     */
    public static List<InflationMonthMetrics> buildSortedMonthlySeries(List<EvdsSeriesPoint> points) {
        TreeMap<YearMonth, BigDecimal> byMonth = aggregatePositiveIndexByMonth(points);
        List<InflationMonthMetrics> out = new ArrayList<>();
        for (YearMonth ym : byMonth.keySet()) {
            BigDecimal iCurr = byMonth.get(ym);
            if (iCurr == null || iCurr.signum() <= 0) {
                continue;
            }
            YearMonth prevYm = ym.minusMonths(1);
            BigDecimal iPrev = byMonth.get(prevYm);
            BigDecimal monthlyPct = null;
            if (iPrev != null && iPrev.signum() > 0) {
                monthlyPct = pctChange(iCurr, iPrev);
            }
            YearMonth yoyYm = ym.minusMonths(12);
            BigDecimal iYoy = byMonth.get(yoyYm);
            BigDecimal annualPct = null;
            if (iYoy != null && iYoy.signum() > 0) {
                annualPct = pctChange(iCurr, iYoy);
            }
            out.add(new InflationMonthMetrics(ym, iCurr, monthlyPct, annualPct));
        }
        return out;
    }

    /**
     * Son ay anlık görüntüsü; en az iki ayrı gözlem ayı gerekir ({@link com.nurseli.marketdata.application.CpiTrComputation} ile uyumlu).
     */
    public static Optional<InflationMonthMetrics> latestMetricsOrEmpty(List<EvdsSeriesPoint> points) {
        List<InflationMonthMetrics> series = buildSortedMonthlySeries(points);
        if (series.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(series.get(series.size() - 1));
    }

    public static BigDecimal pctChange(BigDecimal current, BigDecimal base) {
        return current.divide(base, 10, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private static TreeMap<YearMonth, BigDecimal> aggregatePositiveIndexByMonth(List<EvdsSeriesPoint> points) {
        TreeMap<YearMonth, BigDecimal> byMonth = new TreeMap<>();
        if (points == null) {
            return byMonth;
        }
        for (EvdsSeriesPoint p : points) {
            if (p == null || p.asOf() == null || p.value() == null || p.value().signum() <= 0) {
                continue;
            }
            LocalDate d = p.asOf().toLocalDate();
            YearMonth ym = YearMonth.from(d);
            byMonth.put(ym, p.value());
        }
        return byMonth;
    }
}
