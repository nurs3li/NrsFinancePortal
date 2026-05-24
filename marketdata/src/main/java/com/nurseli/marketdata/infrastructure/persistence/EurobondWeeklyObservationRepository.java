package com.nurseli.marketdata.infrastructure.persistence;

import com.nurseli.marketdata.domain.eurobond.EurobondWeeklyObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    @Query("SELECT MAX(o.observationDate) FROM EurobondWeeklyObservation o")
    Optional<LocalDate> findMaxObservationDate();
}
