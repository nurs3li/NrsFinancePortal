package com.nurseli.marketdata.application.deposit;

import com.nurseli.marketdata.config.DepositRatesProperties;
import com.nurseli.marketdata.domain.deposit.DepositRateObservation;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import com.nurseli.marketdata.repository.DepositRateObservationRepository;
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
public class DepositRatesIngestService {

    private static final String CATEGORY = "DEPOSIT_RATE";
    private static final String SOURCE = "EVDS";
    private static final String UNIT = "PERCENT";
    private static final String FLOW = "AKIM";

    private final DepositRatesProperties depositRatesProperties;
    private final DepositRatesSeriesResolver depositRatesSeriesResolver;
    private final EvdsDebtClient evdsDebtClient;
    private final DepositRateObservationRepository repository;

    @Transactional
    public DepositRatesIngestResult ingestRange(LocalDate fromInclusive, LocalDate toInclusive) {
        if (!depositRatesProperties.isEnabled()) {
            return new DepositRatesIngestResult(0, 0, 0, "deposit-rates disabled");
        }
        if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            return new DepositRatesIngestResult(0, 0, 0, "invalid range");
        }
        int seriesTouched = 0;
        int pointsUpserted = 0;
        int pointsSkipped = 0;
        List<DepositRatesSeriesResolver.ResolvedDepositSeries> specs = depositRatesSeriesResolver.resolved();
        if (specs.isEmpty()) {
            return new DepositRatesIngestResult(0, 0, 0, "no series configured");
        }
        for (DepositRatesSeriesResolver.ResolvedDepositSeries spec : specs) {
            if (spec == null || spec.seriesCode() == null || spec.seriesCode().isBlank()) {
                continue;
            }
            String code = spec.seriesCode().trim();
            seriesTouched++;
            List<EvdsSeriesPoint> pts = evdsDebtClient.fetchSeriesAscending(code, fromInclusive, toInclusive);
            for (EvdsSeriesPoint p : pts) {
                if (p == null || p.asOf() == null) {
                    pointsSkipped++;
                    continue;
                }
                BigDecimal v = p.value();
                if (v == null || v.signum() < 0) {
                    pointsSkipped++;
                    continue;
                }
                LocalDate d = p.asOf().toLocalDate();
                upsertObservation(spec, d, v);
                pointsUpserted++;
            }
        }
        log.info("[DEPOSIT_RATES] ingest from={} to={} seriesTouched={} upserted={} skipped={}",
                fromInclusive, toInclusive, seriesTouched, pointsUpserted, pointsSkipped);
        return new DepositRatesIngestResult(seriesTouched, pointsUpserted, pointsSkipped, "ok");
    }

    private void upsertObservation(DepositRatesSeriesResolver.ResolvedDepositSeries spec, LocalDate date, BigDecimal value) {
        String seriesCode = spec.seriesCode().trim();
        LocalDateTime now = LocalDateTime.now();
        repository.findBySeriesCodeAndObservationDate(seriesCode, date).ifPresentOrElse(existing -> {
            existing.setRateValue(value);
            existing.setUpdatedAt(now);
            repository.save(existing);
        }, () -> {
            DepositRateObservation n = new DepositRateObservation();
            n.setSeriesCode(seriesCode);
            n.setCategory(CATEGORY);
            n.setSource(SOURCE);
            n.setFrequency(depositRatesProperties.getFrequency() != null ? depositRatesProperties.getFrequency() : "WEEKLY");
            n.setUnit(UNIT);
            n.setFlowType(FLOW);
            n.setCurrency(spec.currency() != null ? spec.currency().trim().toUpperCase() : "");
            n.setTerm(spec.term() != null ? spec.term().trim() : "");
            n.setObservationDate(date);
            n.setRateValue(value);
            n.setCreatedAt(now);
            n.setUpdatedAt(now);
            repository.save(n);
        });
    }

    public record DepositRatesIngestResult(int seriesTouched, int pointsUpserted, int pointsSkipped, String status) {}
}
