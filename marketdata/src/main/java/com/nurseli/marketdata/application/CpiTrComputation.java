package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.CpiTrMacroResponse;
import com.nurseli.marketdata.application.inflation.InflationIndexComputation;
import com.nurseli.marketdata.application.inflation.InflationMonthMetrics;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * EVDS TÜFE genel endeks (örn. TP_GENENDEKS_T1) aylık endeks seviyelerinden
 * aylık ve yıllık enflasyon oranını hesaplar.
 *
 * <p>Örnek (endeks seviyesi, yüzde 4 ondalıkta yuvarlanır):</p>
 * <pre>
 *   2026-04: 4028.47, 2026-03: 3866.74 → MoM ≈ 4.18 (hesap: ≈4.1826)
 *   2025-04: 3043.23 → YoY ≈ 32.37 (hesap: ≈32.3748)
 * </pre>
 *
 * <p>Eski EVDS seri kodu örneği (yalnızca referans; Java sabiti olarak kullanılmaz):
 * {@code TP.FE.OKTG01}.</p>
 */
public final class CpiTrComputation {

    private CpiTrComputation() {}

    public static Optional<CpiTrMacroResponse> fromAscendingPoints(List<EvdsSeriesPoint> points, String seriesCode) {
        if (points == null || points.isEmpty() || seriesCode == null || seriesCode.isBlank()) {
            return Optional.empty();
        }
        Optional<InflationMonthMetrics> last = InflationIndexComputation.latestMetricsOrEmpty(sortAsc(points));
        if (last.isEmpty()) {
            return Optional.empty();
        }
        InflationMonthMetrics m = last.get();
        String annualNote = m.annualChangePercent() == null
                ? "Yıllık kıyas için en az 13 ay endeks verisi gerekir."
                : null;
        return Optional.of(new CpiTrMacroResponse(
                seriesCode.trim(),
                m.monthStart(),
                m.indexValue(),
                m.monthlyChangePercent(),
                m.annualChangePercent(),
                annualNote
        ));
    }

    /** Test ve doğrulama için: noktaları tarihe göre sıralar (artan). */
    public static List<EvdsSeriesPoint> sortAsc(List<EvdsSeriesPoint> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(p -> p != null && p.asOf() != null && p.value() != null && p.value().signum() > 0)
                .sorted(Comparator.comparing(EvdsSeriesPoint::asOf))
                .toList();
    }
}
