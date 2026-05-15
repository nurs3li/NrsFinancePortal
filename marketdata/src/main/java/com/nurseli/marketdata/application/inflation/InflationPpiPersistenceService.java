package com.nurseli.marketdata.application.inflation;

import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.domain.inflation.InflationIndexMonthlyEntity;
import com.nurseli.marketdata.repository.InflationIndexMonthlyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InflationPpiPersistenceService {

    private final InflationIndexMonthlyRepository repository;

    @Transactional
    public void upsertPpiMonth(
            String seriesCode,
            int baseYear,
            InflationMonthMetrics metrics
    ) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime created = now;
        var monthStart = metrics.monthStart();
        var existing = repository.findByIndicatorTypeAndSeriesCodeAndObservationMonth(
                InflationIndicatorType.PPI,
                seriesCode,
                monthStart
        );
        InflationIndexMonthlyEntity e = existing.orElseGet(InflationIndexMonthlyEntity::new);
        if (existing.isEmpty()) {
            e.setCreatedAt(now);
        } else {
            created = e.getCreatedAt() != null ? e.getCreatedAt() : now;
        }
        e.setIndicatorType(InflationIndicatorType.PPI);
        e.setSeriesCode(seriesCode);
        e.setCategory("INFLATION");
        e.setSource("EVDS");
        e.setFrequency("MONTHLY");
        e.setUnit("INDEX");
        e.setBaseYear(baseYear);
        e.setCountry("TR");
        e.setObservationMonth(monthStart);
        e.setIndexValue(metrics.indexValue());
        e.setMonthlyChangePct(metrics.monthlyChangePercent());
        e.setAnnualChangePct(metrics.annualChangePercent());
        e.setCreatedAt(created);
        e.setUpdatedAt(now);
        repository.save(e);
    }
}
