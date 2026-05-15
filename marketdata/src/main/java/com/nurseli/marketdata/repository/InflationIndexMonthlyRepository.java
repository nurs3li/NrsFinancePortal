package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.domain.inflation.InflationIndexMonthlyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InflationIndexMonthlyRepository extends JpaRepository<InflationIndexMonthlyEntity, Long> {

    Optional<InflationIndexMonthlyEntity> findByIndicatorTypeAndSeriesCodeAndObservationMonth(
            InflationIndicatorType indicatorType,
            String seriesCode,
            LocalDate observationMonth
    );

    List<InflationIndexMonthlyEntity> findByIndicatorTypeAndSeriesCodeAndObservationMonthBetweenOrderByObservationMonthAsc(
            InflationIndicatorType indicatorType,
            String seriesCode,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );

    Optional<InflationIndexMonthlyEntity> findFirstByIndicatorTypeAndSeriesCodeOrderByObservationMonthDesc(
            InflationIndicatorType indicatorType,
            String seriesCode
    );
}
