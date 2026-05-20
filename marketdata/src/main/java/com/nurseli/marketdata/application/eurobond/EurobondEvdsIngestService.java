package com.nurseli.marketdata.application.eurobond;

import com.nurseli.marketdata.config.EurobondEvdsProperties;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.domain.eurobond.EurobondWeeklyObservation;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import com.nurseli.marketdata.repository.EurobondWeeklyObservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EurobondEvdsIngestService {

    private static final String FREQUENCY = "WEEKLY";
    private static final String UNIT = "MILLION_USD";
    private static final String SOURCE = "EVDS";

    private final EurobondEvdsProperties eurobondEvdsProperties;
    private final EvdsProperties evdsProperties;
    private final EurobondSeriesRegistry registry;
    private final EvdsDebtClient evdsDebtClient;
    private final EurobondWeeklyObservationRepository repository;

    @Transactional
    public EurobondIngestResult ingestRange(LocalDate fromInclusive, LocalDate toInclusive) {
        if (!eurobondEvdsProperties.isEnabled()) {
            return new EurobondIngestResult(0, 0, 0, "eurobond-evds disabled");
        }
        if (!evdsProperties.isEnabled()) {
            return new EurobondIngestResult(0, 0, 0, "evds disabled");
        }
        if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            return new EurobondIngestResult(0, 0, 0, "invalid range");
        }
        int seriesTouched = 0;
        int pointsUpserted = 0;
        int pointsSkipped = 0;
        for (EurobondSeriesRegistry.ResolvedSeries spec : registry.all()) {
            if (spec.seriesCode() == null || spec.seriesCode().isBlank()) {
                continue;
            }
            seriesTouched++;
            List<EvdsSeriesPoint> pts = evdsDebtClient.fetchSeriesAscending(spec.seriesCode(), fromInclusive, toInclusive);
            for (EvdsSeriesPoint p : pts) {
                if (p == null || p.asOf() == null) {
                    pointsSkipped++;
                    continue;
                }
                BigDecimal v = p.value();
                if (v == null) {
                    pointsSkipped++;
                    continue;
                }
                upsert(spec, p.asOf().toLocalDate(), v);
                pointsUpserted++;
            }
        }
        log.info("[EUROBOND_EVDS] ingest from={} to={} seriesTouched={} upserted={} skipped={}",
                fromInclusive, toInclusive, seriesTouched, pointsUpserted, pointsSkipped);
        return new EurobondIngestResult(seriesTouched, pointsUpserted, pointsSkipped, "ok");
    }

    @Transactional
    public EurobondIngestResult ingestRecentWeeks(int weeks) {
        LocalDate to = LocalDate.now();
        int safeWeeks = Math.max(1, Math.min(weeks, 104));
        LocalDate from = to.minusWeeks(safeWeeks);
        return ingestRange(from, to);
    }

    private void upsert(EurobondSeriesRegistry.ResolvedSeries spec, LocalDate date, BigDecimal value) {
        String code = spec.seriesCode().trim();
        LocalDateTime now = LocalDateTime.now();
        repository.findBySeriesCodeAndObservationDate(code, date).ifPresentOrElse(existing -> {
            existing.setValue(value);
            existing.setLabel(spec.label());
            existing.setSeriesKey(spec.seriesKey());
            existing.setUpdatedAt(now);
            repository.save(existing);
        }, () -> {
            EurobondWeeklyObservation n = new EurobondWeeklyObservation();
            n.setSeriesCode(code);
            n.setSeriesKey(spec.seriesKey());
            n.setLabel(spec.label());
            n.setObservationDate(date);
            n.setValue(value);
            n.setFrequency(FREQUENCY);
            n.setUnit(UNIT);
            n.setSource(SOURCE);
            n.setCreatedAt(now);
            n.setUpdatedAt(now);
            repository.save(n);
        });
    }

    public record EurobondIngestResult(int seriesTouched, int pointsUpserted, int pointsSkipped, String status) {}
}
