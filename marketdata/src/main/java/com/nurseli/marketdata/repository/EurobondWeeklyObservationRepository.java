package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.eurobond.EurobondWeeklyObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EurobondWeeklyObservationRepository extends JpaRepository<EurobondWeeklyObservation, Long> {

    Optional<EurobondWeeklyObservation> findBySeriesCodeAndObservationDate(String seriesCode, LocalDate observationDate);

    Optional<EurobondWeeklyObservation> findTopBySeriesCodeOrderByObservationDateDesc(String seriesCode);

    List<EurobondWeeklyObservation> findBySeriesCodeAndObservationDateBetweenOrderByObservationDateAsc(
            String seriesCode, LocalDate fromInclusive, LocalDate toInclusive);

    List<EurobondWeeklyObservation> findBySeriesCodeInAndObservationDateBetweenOrderBySeriesCodeAscObservationDateAsc(
            List<String> seriesCodes, LocalDate fromInclusive, LocalDate toInclusive);
}
